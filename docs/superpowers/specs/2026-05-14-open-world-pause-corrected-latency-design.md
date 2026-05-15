# Open-World Pause-Corrected Latency Recording

Date: 2026-05-14
Status: Approved (design); implementation plan to follow

## Context

`OpenWorldStrategy` already performs per-request coordinated-omission
correction: each `RoadrunnerUser` is tagged with the intended arrival time
(`scheduledStartTime`) drawn from the configured arrival rate, and the user
records `correctedLatency = serviceTime + inQueueTime` on the response. That
correction handles request-queue delay even when the scheduler thread misses
several arrival slots and has to catch up.

It does not catch a different class of stall: when the load generator itself
is paused (carrier-thread saturation, JVM GC, safepoints) and never submits
some scheduled requests at all. Those samples never enter the journal, so the
percentiles in the report silently omit them.

LatencyUtils' technique fills that gap: a pause detector observes stalls
independently of the request loop, and `LatencyStats` produces a histogram
that includes synthetic samples projected over the detected pause interval.
The `roadrunner-latency-utils` module already has the upstream LatencyUtils
code plus `VirtualThreadSchedulingDetector` for carrier-saturation detection.

## Goals

1. Surface latency samples that would have been recorded if the load generator
   had not stalled — first for `OpenWorldStrategy` only.
2. Keep the existing per-request coordinated-omission correction in place; the
   new mechanism is complementary, not a replacement.
3. Pause-corrected percentiles become the report default; the raw CSV-derived
   histogram remains available behind a flag.
4. Clean module layering: `roadrunner-core` and `roadrunner-api` stay unaware
   of `LatencyStats` and the pause detectors.
5. Opt-in by default: with no `--pause-detectors` flag, behavior matches today
   (no LatencyStats wired in, raw histogram used by reports).

## Non-Goals

- `ClosedWorldStrategy` is out of scope for this change. The same abstractions
  will apply later — its `scheduledStartTime` is `now`, so pause-fill matters
  less but mechanically still works. Adding it later should be a small change
  to that strategy plus its CLI plumbing.
- The CSV event journal is not replaced. Events still flow through
  `QueueingProtocolResponsesJournal` unchanged.
- The CSV histogram pathway is not removed. It remains the fallback when no
  recorder snapshot exists and the source of truth under `--raw-latency`.
- We do not introduce new event types on the journal (no `PauseEvent`). Pause
  effects show up only through the histogram snapshot.

## Architecture

```
  roadrunner-api ────── declares LatencyRecorder
        ▲
        │
        │  implements
        │
  roadrunner-latency-recording  ──→  roadrunner-latency-utils
        ▲                                  (LatencyStats, detectors)
        │
        │  constructed at CLI level from flags;
        │  injected as LatencyRecorder
        │
  roadrunner-cli ──→ Bootstrap ──→ DefaultRoadrunner ──→ OpenWorldStrategy
                                                         (uses LatencyRecorder
                                                          via the API only)
```

`roadrunner-core` gains no dependency on `roadrunner-latency-utils` or the
recording module. It depends only on the API interface.

## API

New package `io.roadrunner.api.latency` in `roadrunner-api`:

```java
public interface LatencyRecorder extends AutoCloseable {
    void record(long correctedLatencyNanos);
    void writeSnapshot(Path outputDir) throws IOException;
    @Override void close();

    static LatencyRecorder noop() { ... }
}
```

- `record` is called from the request-recording path (one call per successful
  response).
- `writeSnapshot` flushes the interval histogram and persists it to
  `outputDir/latency.hgrm` using HdrHistogram's standard encoded format
  (compatible with the `hdr-plot` ecosystem).
- `close` releases pause-detector resources and is idempotent.
- `noop()` returns a `LatencyRecorder` whose `record` is a no-op and whose
  `writeSnapshot` writes nothing. Used when no detectors are configured and in
  tests. Lets strategy code call `recorder.record(...)` unconditionally.

The filename `latency.hgrm` is fixed by the API contract so reports can find
it without further configuration. The `.hgrm` extension is the convention
used by HdrHistogram tooling.

## New module: `roadrunner-latency-recording`

- Maven coordinates: `io.roadrunner:roadrunner-latency-recording`.
- Depends on `roadrunner-api` (interface) and `roadrunner-latency-utils`
  (`LatencyStats`, `PauseDetector`, `SimplePauseDetector`,
  `VirtualThreadSchedulingDetector`).
- Module exports a single public package `io.roadrunner.latency.recording`
  containing the factory and the kind enum:

  ```java
  public enum PauseDetectorKind { VT_SCHEDULING, JVM_PAUSE }

  public final class LatencyRecorders {
      public static LatencyRecorder create(EnumSet<PauseDetectorKind> kinds);
  }
  ```

- The implementation builds:
  - one or more `PauseDetector` instances based on `kinds`
    (`VirtualThreadSchedulingDetector` for `VT_SCHEDULING`, `SimplePauseDetector`
    for `JVM_PAUSE`),
  - a single `LatencyStats` configured with histogram bounds matching the
    existing console report: lowest = 1 µs (1 000 ns), highest = 1 hour,
    significant digits = 3,
  - when more than one detector is requested, an internal
    `CompositePauseDetector extends PauseDetector` that registers as a
    listener on each underlying detector and re-emits their pause events
    upstream — `LatencyStats` takes a single `PauseDetector` in its
    constructor, so composition lives here, not in the upstream library.
- `writeSnapshot` calls `latencyStats.getIntervalHistogram()` and uses
  `HistogramLogWriter` (or `HistogramEncoder.encodeIntoByteBuffer`, whichever
  matches the existing shaded HdrHistogram surface) to serialize.
- `close` shuts down the pause detectors and the `LatencyStats` scheduled
  executor.

If `kinds` is empty, the factory returns `LatencyRecorder.noop()`.

## CLI changes

- New CLI option on the `run` subcommand:
  `--pause-detectors=<vt|jvm|vt,jvm|none>` (default: `none`).
- New flag on the report subcommand: `--raw-latency`. When set, reports
  ignore `latency.hgrm` even if present and rebuild the histogram from CSV.
- `Main` / `RunCommand` parses the option, calls `LatencyRecorders.create(...)`,
  and threads the resulting `LatencyRecorder` through `Bootstrap` into
  `DefaultRoadrunner`.

## OpenWorldStrategy changes

- `ExecutionStrategy.execute(...)` gains a `LatencyRecorder recorder`
  parameter. (No context object — strategies already take a small fixed set of
  args; adding one more keeps the call site obvious.)
- `OpenWorldStrategy.execute(...)`:
  - Same scheduler loop as today.
  - In the `finally` block (after `awaitAdvance`, before
    `executorService.shutdown`): call
    `recorder.writeSnapshot(outputDir)` then `recorder.close()`.
  - The strategy must have access to `outputDir`. The current strategy
    doesn't take it — that's the open question below. Whichever mechanism the
    plan picks (extra parameter on `execute`, or a strategy-context object),
    the resolution lands here.
- `RoadrunnerUser.run()`:
  - On the success path, after `journal.response(...)`, call
    `recorder.record(correctedLatency)`.
  - Errors are not recorded into the recorder; only successful samples
    contribute to the histogram (matches what the report's percentile section
    is meant to describe).

`ClosedWorldStrategy.execute(...)` gains the same parameter but uses
`LatencyRecorder.noop()` until a follow-up change extends pause recording to
it. The parameter is in the interface either way, so adding the wiring later
is a one-file change.

## Report changes

`ConsoleReportGenerator` and `HtmlReportGenerator`:

1. Look for `latency.hgrm` in the input directory (the same directory whose
   `output.csv` they already consume).
2. If present and `--raw-latency` is not set: decode it into a `Histogram` and
   use that as the source for `p50/p90/p99/p999/min/max/mean`.
3. Otherwise: keep building the histogram from `response.latency()` over CSV
   events (current behavior).

CSV-derived counts (`totalCount`, `errorCount`, `errorRate`, `requestsPerSecond`,
`totalDurationSeconds`) continue to come from the event stream — those are not
percentile data and pause-fill should not affect them.

## Lifecycle

```
CLI
  ├─ parse --pause-detectors → EnumSet<PauseDetectorKind>
  ├─ recorder = LatencyRecorders.create(kinds)
  └─ bootstrap.build(... recorder ...)

DefaultRoadrunner.execute(...)
  └─ strategy.execute(protocolFactory, journal, recorder, outputDir)
       │
       ▼
  OpenWorldStrategy.execute
    ├─ scheduler loop
    │   └─ user thread: protocol.execute()
    │       ├─ journal.response(response.withLatency(correctedLatency))
    │       └─ recorder.record(correctedLatency)   // success path only
    │
    └─ finally:
        recorder.writeSnapshot(outputDir);
        recorder.close();
```

The strategy owns the `writeSnapshot`/`close` calls because it knows when the
run ends. `recorder.close()` is idempotent so a noop or partial init won't
trip up the call site.

## Configuration defaults

- `LatencyStats` bounds: `lowestTrackableLatency = 1_000` ns, `highestTrackableLatency`
  = 1 hour (`3_600_000_000_000` ns), `numberOfSignificantValueDigits = 3`.
- `SimplePauseDetector` defaults: as upstream (1 ms sleep, 1 ms threshold, 3
  detector threads).
- `VirtualThreadSchedulingDetector` defaults: as in the current code (1 ms
  probe interval, 1 ms threshold).

The factory accepts no further tuning knobs in this change. If users want to
tune detector thresholds, a follow-up can add `--pause-vt-threshold` etc.

## Testing

- Unit tests for `LatencyStatsRecorder`:
  - `noop()` writes no file and ignores `record`.
  - `record` + `writeSnapshot` round-trips: decode the written `latency.hgrm`
    and verify totalCount, min, max, p99.
  - `close` is idempotent.
- Integration test in `roadrunner-core-tests`: an open-world run against the
  `vm` protocol with `VT_SCHEDULING` detector wired in. Assert that
  `output.csv` and `latency.hgrm` both appear in the output dir and that the
  report subcommand prefers the histogram when `--raw-latency` is absent.
- Existing `OpenWorldStrategyTest` continues to pass; the new `recorder`
  parameter uses `LatencyRecorder.noop()` in those tests.

## Risks

1. **Backward compat for existing users**: default `--pause-detectors=none`
   means `latency.hgrm` is not produced, and reports keep using the CSV-derived
   histogram. No surprise change to output for users who don't opt in.
2. **Histogram bounds mismatch**: if a future change moves the report away
   from `Histogram(3)`, the recorder bounds must move too. Documented in the
   recorder factory.
3. **HdrHistogram wire format**: we depend on the shaded HdrHistogram
   serializer/deserializer being symmetric. Verified by the round-trip unit
   test.
4. **Cross-strategy parameter creep**: every strategy now takes a
   `LatencyRecorder` even though only open-world uses it today. Cost is one
   constructor parameter; benefit is no interface change when closed-world
   gets the same treatment.

## Open questions (deferred to the implementation plan)

- Whether `outputDir` is threaded into `ExecutionStrategy.execute` alongside
  `recorder` or sourced from a strategy-side context. The current strategy
  doesn't have it.
- Exact CSV-encoded vs binary-encoded format for `latency.hgrm` (HdrHistogram
  has both; the binary log format is more compact but the CSV form is
  greppable). Lean toward binary log format for fidelity.
- Whether to also expose a JSON summary of the recorder snapshot (count,
  min/max/percentiles) for tooling that doesn't read `.hgrm`. Out of scope
  unless tooling demand emerges.
