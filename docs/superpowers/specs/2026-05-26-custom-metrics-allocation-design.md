# Custom Metrics — Allocation-Free Design

**Date:** 2026-05-26
**Branch:** add-support-for-custom-metrics
**Status:** approved

## Problem

The current `SamplerResponse.Response` holds custom metrics in a `Metrics` wrapper backed by a `HashMap<String, Metric>`. Every request allocates a new `HashMap` and a `Metric` record per metric entry. At high request rates this generates GC pressure that distorts the latency measurements roadrunner is trying to capture accurately.

## Goals

- Near-zero heap allocation on the per-request hot path for metric recording and response construction.
- Clean, type-safe API for sampler implementors.
- Metric names declared once per sampler implementation (not per request).
- Serialization path (`CsvOutputEventListener`) unchanged in structure, updated to read from new storage.
- Seam for future backing implementations (off-heap, memory-mapped) without changing the `Sampler` API.

## Key Design Decisions

### Metric names are pre-registered integers

Metric names are static per sampler implementation — a given sampler always emits the same set of metrics. Names are registered once at startup via `MetricRegistry`, which assigns sequential `int` IDs and stores `String` / `MetricUnit` lookup arrays indexed by ID. Per-request code stores only `int` IDs and `double` values — no `String` allocation, no hashing.

`MetricKey` is a plain `record` wrapping an `int`. It is created once at registration time and stored as a field on the sampler instance. It is never allocated on the hot path.

### Consumer&lt;MetricSink&gt; with inline lambda

Metrics are written inline at response construction time via a `Consumer<MetricSink>` passed to the builder. An inline lambda that captures only local variables (not `this`) is translated to a static synthetic method by javac. Because `SamplerResponseBuilder` will have exactly one live implementation at any call site (always `HeapSamplerResponseBuilder`), the JIT devirtualizes the call, inlines the chain, proves the lambda does not escape, and eliminates the lambda object via scalar replacement. Verified empirically: 5M iterations post-warmup on an 8 MB heap produced zero GC events.

### `SamplerResponseBuilder` as construction seam

The builder is injected into `Sampler.execute()`. It owns the knowledge of how responses are backed (pre-sized primitive arrays today, off-heap tomorrow). Swapping the backing requires only a new builder implementation — sampler code is untouched.

### Metric arrays on the base class

Both `SamplerResponse.Response` and `SamplerResponse.Error` can record metrics. The `int[] metricIds`, `double[] metricValues`, and `int metricCount` fields move to the `SamplerResponse` base class to avoid duplicating serialization logic.

## API

### `roadrunner-api`

```java
public record MetricKey(int id, String name, MetricUnit unit) {}

public interface MetricSink {
    void add(MetricKey key, double value);
}

public final class MetricRegistry {
    public MetricKey register(String name, MetricUnit unit);
    public Collection<MetricKey> registeredKeys();
    public int size();
}

public interface SamplerResponseBuilder {
    <T> SamplerResponse.Response<T> response(long start, long stop, T body, Consumer<MetricSink> metrics);
    <T> SamplerResponse.Response<T> response(long start, long stop, T body);
    SamplerResponse.Error error(long start, long stop, String message, Consumer<MetricSink> metrics);
    SamplerResponse.Error error(long start, long stop, String message);
}
```

`Sampler.execute()` updated signature:

```java
SamplerResponse<?> execute(SamplerParameters parameters, SamplerResponseBuilder builder);
```

`SamplerPlugin` SPI gets one new default method (existing plugins require no changes):

```java
default void registerMetrics(MetricRegistry registry) {}
```

### `SamplerResponse` internal storage (base class)

Replace `Metrics metrics = Metrics.empty()` (on `Response`) with fields on `SamplerResponse`:

```java
private final int[] metricIds;
private final double[] metricValues;
private int metricCount;
```

Arrays are sized to `registry.size()` at construction via the builder. `addMetric(String, double)` is removed. `Metrics` and `Metric` classes are deleted.

Iteration for serialization (async path):

```java
for (var key : registry.registeredKeys()) {
    key.name();                      // carried on the key — no lookup needed
    response.metricValueAt(key);     // metricValues[key.id()] — direct array read
}
```

`response.metricValueAt(MetricKey key)` returns `metricValues[key.id()]`, using the ID as the array index directly.

### `roadrunner-core` — `HeapSamplerResponseBuilder`

```java
final class HeapSamplerResponseBuilder implements SamplerResponseBuilder {
    private final int metricCapacity;
    private final ReusableMetricSink sink;   // one allocation at builder construction

    HeapSamplerResponseBuilder(MetricRegistry registry) {
        this.metricCapacity = registry.size();
        this.sink = new ReusableMetricSink(metricCapacity);
    }

    public <T> SamplerResponse.Response<T> response(
            long start, long stop, T body, Consumer<MetricSink> writer) {
        var response = new SamplerResponse.Response<>(start, stop, body, metricCapacity);
        sink.attachTo(response);
        writer.accept(sink);
        sink.detach();
        return response;
    }
    // same pattern for error(); no-metrics overloads pass null writer
}
```

`ReusableMetricSink` is a pre-allocated wrapper that writes through to whatever `SamplerResponse` it is currently attached to. It is attached and detached synchronously within each builder call — no concurrency concern since each virtual thread user loop has its own builder instance.

### Wiring in `DefaultRoadrunner`

1. For each loaded plugin: `plugin.registerMetrics(registry)`
2. `SamplerResponseBuilder builder = new HeapSamplerResponseBuilder(registry)`
3. Per request: `sampler.execute(params, builder)`

One `HeapSamplerResponseBuilder` is created per virtual thread user loop (inside the spawn, not shared). `ReusableMetricSink` is not thread-safe and must not be shared.

### `CsvOutputEventListener`

Receives `MetricRegistry` at construction. `appendResponseRow` adds metric columns after existing fields for both `Response` and `Error` cases:

```java
for (var key : registry.registeredKeys()) {
    rowBuilder.append(',')
              .append(registry.nameFor(key))
              .append('=')
              .append(response.metricValueAt(key));
}
```

## Typical sampler usage

```java
class JdbcSampler implements Sampler {
    private final MetricKey bytesReadKey;
    private final MetricKey rowCountKey;

    JdbcSampler(MetricRegistry registry) {
        this.bytesReadKey = registry.register("bytes_read", MetricUnit.BYTES);
        this.rowCountKey  = registry.register("row_count",  MetricUnit.COUNT);  // COUNT added to enum
    }

    public SamplerResponse<?> execute(SamplerParameters params, SamplerResponseBuilder builder) {
        // ... execute query ...
        return builder.response(start, stop, result, sink -> {
            sink.add(bytesReadKey, bytes);   // captures locals only — zero alloc post-JIT
            sink.add(rowCountKey,  rows);
        });
    }
}
```

## Deleted

- `io.roadrunner.api.metrics.Metrics`
- `io.roadrunner.api.metrics.Metric`
- `SamplerResponse.Response.addMetric(String, double)`
- Static factory methods on `SamplerResponse` used for response construction (replaced by builder)

`MetricUnit` is retained — it moves from being a per-entry field on `Metric` to being registered once per metric name in `MetricRegistry`.

## Allocation budget per request (post-JIT)

| Object | Notes |
|---|---|
| `SamplerResponse.Response` | unavoidable — lives on the event queue |
| `int[]` metricIds (inside Response) | small, sized to registered count (1–5 elements) |
| `double[]` metricValues (inside Response) | same |
| Lambda object | eliminated by JIT scalar replacement (monomorphic call site, non-escaping) |
| `ReusableMetricSink` | one allocation at builder construction, reused across all requests |

## Documentation

Create `site/modules/ROOT/pages/internals/sampler-lifecycle.adoc` explaining:

- How `SamplerPlugin.registerMetrics()` is called once at startup and how `MetricKey` instances flow from registration to the sampler fields
- The per-request lifecycle: `Sampler.execute()` receives `SamplerResponseBuilder`, calls `builder.response()` / `builder.error()` with an inline `Consumer<MetricSink>`, returns the `SamplerResponse`
- How `DefaultRoadrunner` wires one `HeapSamplerResponseBuilder` per virtual thread user loop
- How `CsvOutputEventListener` reads metrics back via `registry.registeredKeys()` and `response.metricValueAt(key)`

Add a new **Roadrunner Internals** section to `site/modules/ROOT/nav.adoc` and link the new page under it:

```
* Roadrunner Internals
** xref:internals/sampler-lifecycle.adoc[Sampler Lifecycle]
```

## Out of scope

- Off-heap or memory-mapped response backing (the `SamplerResponseBuilder` seam makes this a future drop-in).
- Open-world / arrival-rate load models.
- Metric aggregation or reporting beyond CSV serialization.
