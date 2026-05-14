# Open-World Pause-Corrected Latency Recording — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire `LatencyStats` and pause detectors into `OpenWorldStrategy` so stalls (carrier saturation, optional JVM pauses) fill in synthetic latency samples that the existing per-request manual correction cannot observe, and have the reports use the pause-corrected histogram by default.

**Architecture:** A new `roadrunner-latency-recording` module wraps `LatencyStats` behind a small `LatencyRecorder` interface declared in `roadrunner-api`. `roadrunner-core` only sees that interface. The CLI builds a `LatencyRecorder` from `--pause-detectors=` and threads it through `Bootstrap` → `DefaultRoadrunner` → the strategy. After the run, the recorder writes `latency.hgrm` into the output directory; reports prefer that file over the CSV-derived histogram unless `--raw-latency` is set.

**Tech Stack:** Java 25, virtual threads, Maven multi-module, JUnit Jupiter + AssertJ, picocli, shaded HdrHistogram (`io.roadrunner.shaded.hdrhistogram`), the existing `roadrunner-latency-utils` module.

**Spec:** `docs/superpowers/specs/2026-05-14-open-world-pause-corrected-latency-design.md`.

---

## File Structure

**New:**
- `roadrunner-api/src/main/java/io/roadrunner/api/latency/LatencyRecorder.java` — interface + nested noop impl.
- `roadrunner-latency-recording/pom.xml`
- `roadrunner-latency-recording/src/main/java/module-info.java`
- `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/PauseDetectorKind.java`
- `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/CompositePauseDetector.java` (package-private)
- `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/LatencyStatsRecorder.java` (package-private)
- `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/LatencyRecorders.java` — public factory.
- `roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/LatencyStatsRecorderTest.java`
- `roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/LatencyRecordersTest.java`
- `roadrunner-core-tests/src/test/java/io/roadrunner/core/tests/OpenWorldRecorderIT.java`

**Modified:**
- `pom.xml` (root) — register the new module.
- `roadrunner-api/src/main/java/module-info.java` — export the new `latency` package.
- `roadrunner-core/src/main/java/io/roadrunner/core/internal/ExecutionStrategy.java` — add `LatencyRecorder` to `execute`.
- `roadrunner-core/src/main/java/io/roadrunner/core/internal/OpenWorldStrategy.java` — call `recorder.record(...)`.
- `roadrunner-core/src/main/java/io/roadrunner/core/internal/ClosedWorldStrategy.java` — accept the param, ignore it.
- `roadrunner-core/src/main/java/io/roadrunner/core/internal/DefaultRoadrunner.java` — own the recorder, call `writeSnapshot`/`close` in `finally`.
- `roadrunner-core/src/main/java/io/roadrunner/core/Bootstrap.java` — `withLatencyRecorder(...)`.
- `roadrunner-core/src/test/java/io/roadrunner/core/internal/OpenWorldStrategyTest.java` — pass `LatencyRecorder.noop()`.
- `roadrunner-cli/pom.xml` — depend on `roadrunner-latency-recording`.
- `roadrunner-cli/src/main/java/io/roadrunner/cli/RunCommand.java` — new options, build recorder, propagate to report config.
- `roadrunner-reports-console/src/main/java/io/roadrunner/reports/console/ConsoleReportGenerator.java` — prefer `latency.hgrm`.
- `roadrunner-reports-html/src/main/java/io/roadrunner/reports/html/HtmlReportGenerator.java` — same.
- `roadrunner-app/pom.xml` — depend on `roadrunner-latency-recording` so the bundled image ships it.

**Cross-cutting decisions made here (resolving the spec's open questions):**
- `outputDir` does **not** become a parameter on `ExecutionStrategy.execute`. Instead, `DefaultRoadrunner` owns the `writeSnapshot(outputDir)`/`close()` calls in its own `finally` block, since it already holds `outputDir`. The strategy only receives the `LatencyRecorder` and calls `record(...)` during the run.
- Snapshot file is `latency.hgrm` written with HdrHistogram's `HistogramLogWriter` (text-line format with base64-encoded histograms — interoperable with `hdr-plot`).
- `outputDir` and `rawLatency` are passed into report generators through the existing `Map<String,String>` configuration argument under keys `"outputDir"` and `"rawLatency"`.

---

## Task 1: Add `LatencyRecorder` API interface

**Files:**
- Create: `roadrunner-api/src/main/java/io/roadrunner/api/latency/LatencyRecorder.java`
- Modify: `roadrunner-api/src/main/java/module-info.java`

- [ ] **Step 1: Create the interface**

Write `roadrunner-api/src/main/java/io/roadrunner/api/latency/LatencyRecorder.java`:

```java
/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.api.latency;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Records per-request latency samples and, optionally, pause-corrected samples.
 * Implementations may attach pause detectors that fill in synthetic samples when
 * the load generator itself stalls.
 *
 * <p>{@link #record} is hot-path; implementations must be wait-free and thread-safe.
 * {@link #writeSnapshot} is called once at the end of a run.
 */
public interface LatencyRecorder extends AutoCloseable {

    /**
     * Record a successful request's corrected latency, in nanoseconds.
     */
    void record(long correctedLatencyNanos);

    /**
     * Persist the recorder's histogram into {@code outputDir/latency.hgrm}.
     * Called once at end of run. Implementations should tolerate being called before
     * any {@link #record} calls (empty histogram is fine).
     */
    void writeSnapshot(Path outputDir) throws IOException;

    /**
     * Release detector threads and other resources. Idempotent.
     */
    @Override
    void close();

    /**
     * A recorder whose {@link #record} is a no-op and that writes nothing.
     * Used when no pause detectors are configured.
     */
    static LatencyRecorder noop() {
        return NoopLatencyRecorder.INSTANCE;
    }

    final class NoopLatencyRecorder implements LatencyRecorder {
        static final NoopLatencyRecorder INSTANCE = new NoopLatencyRecorder();

        private NoopLatencyRecorder() {}

        @Override
        public void record(long correctedLatencyNanos) {}

        @Override
        public void writeSnapshot(Path outputDir) {}

        @Override
        public void close() {}
    }
}
```

- [ ] **Step 2: Export the package from the API module**

Edit `roadrunner-api/src/main/java/module-info.java` — add one line inside the `module io.roadrunner.api { ... }` block:

```java
    exports io.roadrunner.api.latency;
```

Final body of the module declaration:

```java
module io.roadrunner.api {
    exports io.roadrunner.api;
    exports io.roadrunner.api.protocol;
    exports io.roadrunner.api.metrics;
    exports io.roadrunner.api.measurments;
    exports io.roadrunner.api.reports;
    exports io.roadrunner.api.events;
    exports io.roadrunner.api.latency;
}
```

- [ ] **Step 3: Compile**

Run: `./mvnw -pl roadrunner-api compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add roadrunner-api/src/main/java/io/roadrunner/api/latency/LatencyRecorder.java \
        roadrunner-api/src/main/java/module-info.java
git commit -m "api: add LatencyRecorder interface with noop default"
```

---

## Task 2: Create the `roadrunner-latency-recording` module

**Files:**
- Create: `roadrunner-latency-recording/pom.xml`
- Create: `roadrunner-latency-recording/src/main/java/module-info.java`
- Modify: `pom.xml` (root)

- [ ] **Step 1: Write the module pom**

Create `roadrunner-latency-recording/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.roadrunner</groupId>
        <artifactId>roadrunner</artifactId>
        <version>0.0.4-SNAPSHOT</version>
    </parent>

    <artifactId>roadrunner-latency-recording</artifactId>

    <dependencies>
        <dependency>
            <groupId>io.roadrunner</groupId>
            <artifactId>roadrunner-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.roadrunner</groupId>
            <artifactId>roadrunner-latency-utils</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.roadrunner</groupId>
            <artifactId>roadrunner-hdrhistogram</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>${assertj-core.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Write `module-info.java`**

Create `roadrunner-latency-recording/src/main/java/module-info.java`:

```java
/*
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
module io.roadrunner.latency.recording {
    requires io.roadrunner.api;
    requires io.roadrunner.latency;
    requires io.roadrunner.hdrhistogram;

    exports io.roadrunner.latency.recording;
}
```

- [ ] **Step 3: Register the module in the root pom**

Edit `pom.xml` (root): inside the `<modules>` block, add `<module>roadrunner-latency-recording</module>` right after the line that says `<module>roadrunner-latency-utils</module>` (currently at the bottom near `roadrunner-app`). Use this snippet — locate the existing block and add the new line:

Search for:
```xml
        <module>roadrunner-latency-utils</module>
```

Replace with:
```xml
        <module>roadrunner-latency-utils</module>
        <module>roadrunner-latency-recording</module>
```

- [ ] **Step 4: Verify the multi-module build picks it up**

Run: `./mvnw -pl roadrunner-latency-recording validate`
Expected: `BUILD SUCCESS` (no compile step yet because no Java files in `src/main/java` outside `module-info.java`).

Then: `./mvnw -pl roadrunner-latency-recording compile`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add pom.xml \
        roadrunner-latency-recording/pom.xml \
        roadrunner-latency-recording/src/main/java/module-info.java
git commit -m "build: add roadrunner-latency-recording module skeleton"
```

---

## Task 3: `PauseDetectorKind` enum and `CompositePauseDetector`

**Files:**
- Create: `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/PauseDetectorKind.java`
- Create: `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/CompositePauseDetector.java`
- Test: `roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/CompositePauseDetectorTest.java`

- [ ] **Step 1: Write the enum**

Create `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/PauseDetectorKind.java`:

```java
/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.latency.recording;

/**
 * Kinds of pause detectors that can be attached to a {@link LatencyRecorders}-built recorder.
 */
public enum PauseDetectorKind {
    /** Detects virtual-thread carrier saturation (probe-based). */
    VT_SCHEDULING,
    /** Detects JVM-wide pauses (GC, safepoints) via consensus across detector threads. */
    JVM_PAUSE
}
```

- [ ] **Step 2: Write the failing test**

Create `roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/CompositePauseDetectorTest.java`:

```java
/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.latency.recording;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.latency.PauseDetector;
import io.roadrunner.latency.PauseDetectorListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class CompositePauseDetectorTest {

    @Test
    void forwardsPauseEventsFromAllUnderlyingDetectors() throws Exception {
        var detectorA = new TestDetector();
        var detectorB = new TestDetector();

        var composite = new CompositePauseDetector(List.of(detectorA, detectorB));

        var received = new CopyOnWriteArrayList<long[]>();
        composite.addListener((pauseLength, pauseEndTime) -> received.add(new long[] {pauseLength, pauseEndTime}));

        detectorA.emit(100L, 1_000L);
        detectorB.emit(200L, 2_000L);

        // PauseDetector dispatches asynchronously; wait briefly for both events to flow.
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (received.size() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertThat(received).hasSize(2);
        assertThat(received).anyMatch(p -> p[0] == 100L && p[1] == 1_000L);
        assertThat(received).anyMatch(p -> p[0] == 200L && p[1] == 2_000L);

        composite.shutdown();
        detectorA.shutdown();
        detectorB.shutdown();
    }

    @Test
    void shutdownTearsDownEveryUnderlyingDetector() {
        var detectorA = new TestDetector();
        var detectorB = new TestDetector();
        var composite = new CompositePauseDetector(List.of(detectorA, detectorB));

        composite.shutdown();

        assertThat(detectorA.isShutdown()).isTrue();
        assertThat(detectorB.isShutdown()).isTrue();
    }

    /** A bare-bones PauseDetector that exposes notifyListeners for the test. */
    private static final class TestDetector extends PauseDetector {
        private volatile boolean shutdown;

        void emit(long pauseLength, long pauseEndTime) {
            notifyListeners(pauseLength, pauseEndTime);
        }

        boolean isShutdown() {
            return shutdown;
        }

        @Override
        public void shutdown() {
            shutdown = true;
            super.shutdown();
        }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./mvnw -pl roadrunner-latency-recording test -Dtest=CompositePauseDetectorTest`
Expected: FAIL with compile error (`CompositePauseDetector` does not exist).

- [ ] **Step 4: Implement `CompositePauseDetector`**

Create `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/CompositePauseDetector.java`:

```java
/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.latency.recording;

import io.roadrunner.latency.PauseDetector;
import io.roadrunner.latency.PauseDetectorListener;
import java.util.List;

/**
 * Aggregates several {@link PauseDetector}s into one. {@link io.roadrunner.latency.LatencyStats}
 * takes a single {@code PauseDetector} in its constructor, so we use this composite to
 * fan in events from multiple detectors.
 */
final class CompositePauseDetector extends PauseDetector {

    private final List<PauseDetector> underlying;
    private final PauseDetectorListener forwarding = this::notifyListeners;

    CompositePauseDetector(List<PauseDetector> underlying) {
        this.underlying = List.copyOf(underlying);
        for (PauseDetector detector : this.underlying) {
            detector.addListener(forwarding, true /* high priority */);
        }
    }

    @Override
    public void shutdown() {
        for (PauseDetector detector : underlying) {
            detector.removeListener(forwarding);
            detector.shutdown();
        }
        super.shutdown();
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -pl roadrunner-latency-recording test -Dtest=CompositePauseDetectorTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit**

```bash
git add roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/PauseDetectorKind.java \
        roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/CompositePauseDetector.java \
        roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/CompositePauseDetectorTest.java
git commit -m "latency-recording: composite pause detector + kind enum"
```

---

## Task 4: `LatencyStatsRecorder`

**Files:**
- Create: `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/LatencyStatsRecorder.java`
- Test: `roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/LatencyStatsRecorderTest.java`

- [ ] **Step 1: Write the failing test**

Create `roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/LatencyStatsRecorderTest.java`:

```java
/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.latency.recording;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.latency.SimplePauseDetector;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import io.roadrunner.shaded.hdrhistogram.HistogramLogReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LatencyStatsRecorderTest {

    @Test
    void writeSnapshotProducesLatencyHgrmThatRoundTripsRecordedValues(@TempDir Path tmp) throws IOException {
        var detector = new SimplePauseDetector();
        try (var recorder = new LatencyStatsRecorder(detector)) {
            recorder.record(1_000_000L); // 1 ms
            recorder.record(2_000_000L); // 2 ms
            recorder.record(5_000_000L); // 5 ms

            recorder.writeSnapshot(tmp);
        }

        var hgrm = tmp.resolve("latency.hgrm");
        assertThat(hgrm).exists();

        var combined = new Histogram(1L, 3_600_000_000_000L, 3);
        try (var reader = new HistogramLogReader(hgrm.toFile())) {
            Histogram h;
            while ((h = (Histogram) reader.nextIntervalHistogram()) != null) {
                combined.add(h);
            }
        }

        assertThat(combined.getTotalCount()).isEqualTo(3L);
        assertThat(combined.getMinValue()).isLessThanOrEqualTo(1_000_000L);
        assertThat(combined.getMaxValue()).isGreaterThanOrEqualTo(5_000_000L);
    }

    @Test
    void writeSnapshotIsOkWithNoRecordedSamples(@TempDir Path tmp) throws IOException {
        var detector = new SimplePauseDetector();
        try (var recorder = new LatencyStatsRecorder(detector)) {
            recorder.writeSnapshot(tmp);
        }

        assertThat(tmp.resolve("latency.hgrm")).exists();
    }

    @Test
    void closeIsIdempotent() {
        var detector = new SimplePauseDetector();
        var recorder = new LatencyStatsRecorder(detector);
        recorder.close();
        recorder.close();
        // No exception.
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl roadrunner-latency-recording test -Dtest=LatencyStatsRecorderTest`
Expected: FAIL with compile error (`LatencyStatsRecorder` does not exist).

- [ ] **Step 3: Implement `LatencyStatsRecorder`**

Create `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/LatencyStatsRecorder.java`:

```java
/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.latency.recording;

import io.roadrunner.api.latency.LatencyRecorder;
import io.roadrunner.latency.LatencyStats;
import io.roadrunner.latency.PauseDetector;
import io.roadrunner.shaded.hdrhistogram.HistogramLogWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link LatencyRecorder} backed by {@link LatencyStats} and a pause detector.
 * Writes a single-interval HdrHistogram log to {@code latency.hgrm}.
 */
final class LatencyStatsRecorder implements LatencyRecorder {

    /** Bounds match the existing console report's significant-digits choice. */
    private static final long LOWEST_TRACKABLE_NS = 1_000L;
    private static final long HIGHEST_TRACKABLE_NS = 3_600_000_000_000L; // 1 hour
    private static final int SIGNIFICANT_DIGITS = 3;

    private final PauseDetector pauseDetector;
    private final LatencyStats latencyStats;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    LatencyStatsRecorder(PauseDetector pauseDetector) {
        this.pauseDetector = pauseDetector;
        this.latencyStats = new LatencyStats.Builder()
                .lowestTrackableLatency(LOWEST_TRACKABLE_NS)
                .highestTrackableLatency(HIGHEST_TRACKABLE_NS)
                .numberOfSignificantValueDigits(SIGNIFICANT_DIGITS)
                .pauseDetector(pauseDetector)
                .build();
    }

    @Override
    public void record(long correctedLatencyNanos) {
        if (correctedLatencyNanos <= 0) {
            return; // HdrHistogram rejects non-positive values; skip noisy samples.
        }
        if (correctedLatencyNanos < LOWEST_TRACKABLE_NS) {
            correctedLatencyNanos = LOWEST_TRACKABLE_NS;
        } else if (correctedLatencyNanos > HIGHEST_TRACKABLE_NS) {
            correctedLatencyNanos = HIGHEST_TRACKABLE_NS;
        }
        latencyStats.recordLatency(correctedLatencyNanos);
    }

    @Override
    public void writeSnapshot(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        var hgrm = outputDir.resolve("latency.hgrm");
        try (var out = new PrintStream(Files.newOutputStream(hgrm))) {
            var writer = new HistogramLogWriter(out);
            writer.outputLogFormatVersion();
            writer.outputStartTime(System.currentTimeMillis());
            writer.outputLegend();
            writer.outputIntervalHistogram(latencyStats.getIntervalHistogram());
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            latencyStats.stop();
            pauseDetector.shutdown();
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -pl roadrunner-latency-recording test -Dtest=LatencyStatsRecorderTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`.

If `HistogramLogWriter`/`HistogramLogReader` are not in the shaded HdrHistogram artifact, the failure mode is a clear `ClassNotFoundException`. In that case, locate the unshaded class name in `roadrunner-hdrhistogram` and adjust both the test and the recorder imports — they are both standard HdrHistogram classes and should be present.

- [ ] **Step 5: Commit**

```bash
git add roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/LatencyStatsRecorder.java \
        roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/LatencyStatsRecorderTest.java
git commit -m "latency-recording: LatencyStatsRecorder implements LatencyRecorder"
```

---

## Task 5: `LatencyRecorders` factory

**Files:**
- Create: `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/LatencyRecorders.java`
- Test: `roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/LatencyRecordersTest.java`

- [ ] **Step 1: Write the failing test**

Create `roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/LatencyRecordersTest.java`:

```java
/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.latency.recording;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.api.latency.LatencyRecorder;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class LatencyRecordersTest {

    @Test
    void emptyKindsReturnsNoopRecorder() {
        var recorder = LatencyRecorders.create(EnumSet.noneOf(PauseDetectorKind.class));
        assertThat(recorder).isSameAs(LatencyRecorder.noop());
        recorder.close();
    }

    @Test
    void singleKindReturnsRealRecorder() {
        var recorder = LatencyRecorders.create(EnumSet.of(PauseDetectorKind.VT_SCHEDULING));
        try {
            assertThat(recorder).isNotSameAs(LatencyRecorder.noop());
            // Record one sample so we know the recorder is wired.
            recorder.record(1_500_000L);
        } finally {
            recorder.close();
        }
    }

    @Test
    void bothKindsReturnsRealRecorderUsingCompositeDetector() {
        var recorder = LatencyRecorders.create(EnumSet.allOf(PauseDetectorKind.class));
        try {
            assertThat(recorder).isNotSameAs(LatencyRecorder.noop());
            recorder.record(2_500_000L);
        } finally {
            recorder.close();
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl roadrunner-latency-recording test -Dtest=LatencyRecordersTest`
Expected: FAIL — `LatencyRecorders` does not exist.

- [ ] **Step 3: Implement the factory**

Create `roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/LatencyRecorders.java`:

```java
/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.latency.recording;

import io.roadrunner.api.latency.LatencyRecorder;
import io.roadrunner.latency.PauseDetector;
import io.roadrunner.latency.SimplePauseDetector;
import io.roadrunner.latency.VirtualThreadSchedulingDetector;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Builds {@link LatencyRecorder} instances configured with the requested pause detectors.
 */
public final class LatencyRecorders {

    private LatencyRecorders() {}

    /**
     * @param kinds which pause detectors to wire in. An empty set returns {@link LatencyRecorder#noop()}.
     */
    public static LatencyRecorder create(EnumSet<PauseDetectorKind> kinds) {
        if (kinds.isEmpty()) {
            return LatencyRecorder.noop();
        }

        var detectors = new ArrayList<PauseDetector>();
        for (PauseDetectorKind kind : kinds) {
            detectors.add(switch (kind) {
                case VT_SCHEDULING -> new VirtualThreadSchedulingDetector();
                case JVM_PAUSE -> new SimplePauseDetector();
            });
        }

        PauseDetector pauseDetector = detectors.size() == 1
                ? detectors.get(0)
                : new CompositePauseDetector(List.copyOf(detectors));
        return new LatencyStatsRecorder(pauseDetector);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -pl roadrunner-latency-recording test -Dtest=LatencyRecordersTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 5: Run the full module test suite**

Run: `./mvnw -pl roadrunner-latency-recording test`
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add roadrunner-latency-recording/src/main/java/io/roadrunner/latency/recording/LatencyRecorders.java \
        roadrunner-latency-recording/src/test/java/io/roadrunner/latency/recording/LatencyRecordersTest.java
git commit -m "latency-recording: LatencyRecorders factory with kind selection"
```

---

## Task 6: Thread `LatencyRecorder` through core (no behavior change yet)

This task wires the recorder through `Bootstrap` → `DefaultRoadrunner` → `ExecutionStrategy` → both strategies, defaulting to `noop`. No `record(...)` calls are added yet; that's Task 7. The behavior of existing tests must not change.

**Files:**
- Modify: `roadrunner-core/src/main/java/io/roadrunner/core/internal/ExecutionStrategy.java`
- Modify: `roadrunner-core/src/main/java/io/roadrunner/core/internal/OpenWorldStrategy.java`
- Modify: `roadrunner-core/src/main/java/io/roadrunner/core/internal/ClosedWorldStrategy.java`
- Modify: `roadrunner-core/src/main/java/io/roadrunner/core/internal/DefaultRoadrunner.java`
- Modify: `roadrunner-core/src/main/java/io/roadrunner/core/Bootstrap.java`
- Modify: `roadrunner-core/src/test/java/io/roadrunner/core/internal/OpenWorldStrategyTest.java`

- [ ] **Step 1: Update `ExecutionStrategy.execute` signature**

Edit `roadrunner-core/src/main/java/io/roadrunner/core/internal/ExecutionStrategy.java`. Replace the method declaration:

```java
    void execute(Supplier<Protocol> protocolFactory, QueueingProtocolResponsesJournal journal)
            throws InterruptedException;
```

with:

```java
    void execute(
            Supplier<Protocol> protocolFactory,
            QueueingProtocolResponsesJournal journal,
            LatencyRecorder recorder)
            throws InterruptedException;
```

Add the import:

```java
import io.roadrunner.api.latency.LatencyRecorder;
```

- [ ] **Step 2: Update `OpenWorldStrategy.execute` signature**

Edit `roadrunner-core/src/main/java/io/roadrunner/core/internal/OpenWorldStrategy.java`. Add the import:

```java
import io.roadrunner.api.latency.LatencyRecorder;
```

Replace the `execute` method's signature:

```java
    public void execute(Supplier<Protocol> protocolFactory, QueueingProtocolResponsesJournal journal)
            throws InterruptedException {
```

with:

```java
    public void execute(
            Supplier<Protocol> protocolFactory,
            QueueingProtocolResponsesJournal journal,
            LatencyRecorder recorder)
            throws InterruptedException {
```

(The `recorder` reference is unused in this task; the next task hooks it up. Java does not warn on unused parameters, so leave it.)

- [ ] **Step 3: Update `ClosedWorldStrategy.execute` signature**

Edit `roadrunner-core/src/main/java/io/roadrunner/core/internal/ClosedWorldStrategy.java`. Add the import:

```java
import io.roadrunner.api.latency.LatencyRecorder;
```

Replace the `execute` method's signature:

```java
    public void execute(Supplier<Protocol> protocolFactory, QueueingProtocolResponsesJournal journal)
            throws InterruptedException {
```

with:

```java
    public void execute(
            Supplier<Protocol> protocolFactory,
            QueueingProtocolResponsesJournal journal,
            LatencyRecorder recorder)
            throws InterruptedException {
```

The `recorder` parameter is intentionally ignored — closed-world is out of scope for this change.

- [ ] **Step 4: Update `Bootstrap` to accept a recorder**

Edit `roadrunner-core/src/main/java/io/roadrunner/core/Bootstrap.java`. Add the import:

```java
import io.roadrunner.api.latency.LatencyRecorder;
```

Inside the class, add a field with a default and a setter, alongside the existing `measurementProgress` field:

```java
    private LatencyRecorder latencyRecorder = LatencyRecorder.noop();
```

```java
    public Bootstrap withLatencyRecorder(LatencyRecorder latencyRecorder) {
        this.latencyRecorder = latencyRecorder;
        return this;
    }
```

Update the `build()` method to pass the recorder:

Replace:

```java
        return new DefaultRoadrunner(strategy, measurementProgress, outputDir);
```

with:

```java
        return new DefaultRoadrunner(strategy, measurementProgress, outputDir, latencyRecorder);
```

- [ ] **Step 5: Update `DefaultRoadrunner` constructor and call site**

Edit `roadrunner-core/src/main/java/io/roadrunner/core/internal/DefaultRoadrunner.java`. Add the import:

```java
import io.roadrunner.api.latency.LatencyRecorder;
```

Add a field:

```java
    private final LatencyRecorder latencyRecorder;
```

Replace the constructor:

```java
    public DefaultRoadrunner(ExecutionStrategy strategy, MeasurementProgress measurementProgress, Path outputDir) {
        this.strategy = strategy;
        this.measurementProgress = measurementProgress;
        this.outputDir = outputDir;
    }
```

with:

```java
    public DefaultRoadrunner(
            ExecutionStrategy strategy,
            MeasurementProgress measurementProgress,
            Path outputDir,
            LatencyRecorder latencyRecorder) {
        this.strategy = strategy;
        this.measurementProgress = measurementProgress;
        this.outputDir = outputDir;
        this.latencyRecorder = latencyRecorder;
    }
```

Update the strategy invocation in `execute(...)`. Replace:

```java
                strategy.execute(requestsFactory, responsesJournal);
```

with:

```java
                strategy.execute(requestsFactory, responsesJournal, latencyRecorder);
```

(Snapshot / close are added in Task 7.)

- [ ] **Step 6: Update `OpenWorldStrategyTest`**

Edit `roadrunner-core/src/test/java/io/roadrunner/core/internal/OpenWorldStrategyTest.java`. Add the import:

```java
import io.roadrunner.api.latency.LatencyRecorder;
```

Find the call to `strategy.execute(...)`. Currently:

```java
            strategy.execute(
                    () -> () -> {
                        var start = System.nanoTime();
                        var stop = System.nanoTime();
                        return ProtocolResponse.empty(start, stop);
                    },
                    journal);
```

Replace the closing line `                    journal);` with:

```java
                    journal,
                    LatencyRecorder.noop());
```

If there are additional `strategy.execute(...)` call sites in this file, apply the same change to each.

- [ ] **Step 7: Add the `requires` to `roadrunner-core`'s module-info**

`roadrunner-core` already `requires io.roadrunner.api` transitively, so no change needed. Confirm by reading `roadrunner-core/src/main/java/module-info.java` — if it lists `requires io.roadrunner.api;`, you're done. Otherwise add that line.

- [ ] **Step 8: Compile and run core tests**

Run: `./mvnw -pl roadrunner-core,roadrunner-core-tests test`
Expected: all existing tests still pass.

- [ ] **Step 9: Commit**

```bash
git add roadrunner-core/src/main/java/io/roadrunner/core/internal/ExecutionStrategy.java \
        roadrunner-core/src/main/java/io/roadrunner/core/internal/OpenWorldStrategy.java \
        roadrunner-core/src/main/java/io/roadrunner/core/internal/ClosedWorldStrategy.java \
        roadrunner-core/src/main/java/io/roadrunner/core/internal/DefaultRoadrunner.java \
        roadrunner-core/src/main/java/io/roadrunner/core/Bootstrap.java \
        roadrunner-core/src/test/java/io/roadrunner/core/internal/OpenWorldStrategyTest.java
git commit -m "core: thread LatencyRecorder through Bootstrap and strategies (noop default)"
```

---

## Task 7: Record samples in OpenWorld; snapshot + close in DefaultRoadrunner

**Files:**
- Modify: `roadrunner-core/src/main/java/io/roadrunner/core/internal/OpenWorldStrategy.java`
- Modify: `roadrunner-core/src/main/java/io/roadrunner/core/internal/DefaultRoadrunner.java`

- [ ] **Step 1: Pass the recorder into `RoadrunnerUser`**

Edit `roadrunner-core/src/main/java/io/roadrunner/core/internal/OpenWorldStrategy.java`.

In the inner class `RoadrunnerUser`, add a field and update the constructor.

Replace the existing class header through constructor:

```java
    private static class RoadrunnerUser implements Runnable {
        private final QueueingProtocolResponsesJournal journal;
        private final Protocol protocol;
        private final long scheduledStartTime;
        private final Phaser phaser;

        public RoadrunnerUser(
                QueueingProtocolResponsesJournal journal, Protocol protocol, long scheduledStartTime, Phaser phaser) {
            this.journal = journal;
            this.protocol = protocol;
            this.scheduledStartTime = scheduledStartTime;
            this.phaser = phaser;
        }
```

with:

```java
    private static class RoadrunnerUser implements Runnable {
        private final QueueingProtocolResponsesJournal journal;
        private final Protocol protocol;
        private final long scheduledStartTime;
        private final Phaser phaser;
        private final LatencyRecorder recorder;

        public RoadrunnerUser(
                QueueingProtocolResponsesJournal journal,
                Protocol protocol,
                long scheduledStartTime,
                Phaser phaser,
                LatencyRecorder recorder) {
            this.journal = journal;
            this.protocol = protocol;
            this.scheduledStartTime = scheduledStartTime;
            this.phaser = phaser;
            this.recorder = recorder;
        }
```

In `RoadrunnerUser.run()`, after the `journal.response(...)` call and before the `catch (Exception e)` block, insert one line so the success path also records into the recorder. Replace:

```java
                journal.response(
                        response.withScheduledStartTime(scheduledStartTime).withLatency(correctedLatency));
            } catch (Exception e) {
```

with:

```java
                journal.response(
                        response.withScheduledStartTime(scheduledStartTime).withLatency(correctedLatency));
                recorder.record(correctedLatency);
            } catch (Exception e) {
```

In `OpenWorldStrategy.execute(...)`, update the submission line. Replace:

```java
                requestsExecutor.submit(new RoadrunnerUser(journal, protocolFactory.get(), scheduledStartTime, phaser));
```

with:

```java
                requestsExecutor.submit(
                        new RoadrunnerUser(journal, protocolFactory.get(), scheduledStartTime, phaser, recorder));
```

- [ ] **Step 2: Add snapshot + close to `DefaultRoadrunner.execute`**

Edit `roadrunner-core/src/main/java/io/roadrunner/core/internal/DefaultRoadrunner.java`. Add the import:

```java
import java.io.IOException;
```

Replace the existing strategy-execution try block:

```java
            try {
                strategy.execute(requestsFactory, responsesJournal, latencyRecorder);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return DefaultMeasurements.from(responsesJournal.measurementsReader());
```

with:

```java
            try {
                strategy.execute(requestsFactory, responsesJournal, latencyRecorder);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    latencyRecorder.writeSnapshot(outputDir);
                } catch (IOException e) {
                    LOG.warn("failed to write latency snapshot to {}", outputDir, e);
                }
                latencyRecorder.close();
            }
            return DefaultMeasurements.from(responsesJournal.measurementsReader());
```

- [ ] **Step 3: Build and run tests**

Run: `./mvnw -pl roadrunner-core,roadrunner-core-tests test`
Expected: all tests pass. (`OpenWorldStrategyTest` uses `LatencyRecorder.noop()`, so the new `record` and `writeSnapshot` calls are inert.)

- [ ] **Step 4: Commit**

```bash
git add roadrunner-core/src/main/java/io/roadrunner/core/internal/OpenWorldStrategy.java \
        roadrunner-core/src/main/java/io/roadrunner/core/internal/DefaultRoadrunner.java
git commit -m "core: record corrected latency into LatencyRecorder and snapshot at run end"
```

---

## Task 8: CLI options and report-config wiring

**Files:**
- Modify: `roadrunner-cli/pom.xml`
- Modify: `roadrunner-cli/src/main/java/io/roadrunner/cli/RunCommand.java`
- Modify: `roadrunner-app/pom.xml`

- [ ] **Step 1: Add `roadrunner-latency-recording` dependency to the CLI**

Edit `roadrunner-cli/pom.xml`. Inside `<dependencies>`, add (near the existing `roadrunner-core` dependency):

```xml
        <dependency>
            <groupId>io.roadrunner</groupId>
            <artifactId>roadrunner-latency-recording</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 2: Add the same dependency to the app (so the jlink image bundles it)**

Edit `roadrunner-app/pom.xml`. Inside `<dependencies>`, add the same block:

```xml
        <dependency>
            <groupId>io.roadrunner</groupId>
            <artifactId>roadrunner-latency-recording</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 3: Add CLI options to `RunCommand`**

Edit `roadrunner-cli/src/main/java/io/roadrunner/cli/RunCommand.java`. Add imports:

```java
import io.roadrunner.api.latency.LatencyRecorder;
import io.roadrunner.latency.recording.LatencyRecorders;
import io.roadrunner.latency.recording.PauseDetectorKind;
import java.util.Arrays;
import java.util.EnumSet;
```

Add two new `@Option` fields next to the existing `outputDir` and `report` fields:

```java
    @Option(
            names = "--pause-detectors",
            description =
                    "Comma-separated list of pause detectors to record into the corrected-latency histogram: vt, jvm, or vt,jvm. Empty / unset disables pause-corrected recording.",
            defaultValue = "")
    String pauseDetectors;

    @Option(
            names = "--raw-latency",
            description =
                    "Reports use the per-event CSV histogram even when a pause-corrected histogram.hgrm is present.")
    boolean rawLatency;
```

- [ ] **Step 4: Build the recorder and propagate config to the report**

Edit `RunCommand.run(...)`. The full updated method body:

```java
    public void run(ProtocolProvider protocolProvider) throws Exception {
        var detectorKinds = parsePauseDetectors(pauseDetectors);
        var recorder = LatencyRecorders.create(detectorKinds);

        var bootstrap = new Bootstrap().withOutputDir(outputDir).withLatencyRecorder(recorder);

        if (loadModel.closedWorld != null) {
            bootstrap
                    .withClosedWorldModel(loadModel.closedWorld.concurrency, loadModel.closedWorld.numberOfRequests)
                    .withMeasurementProgress(new ProgressBar(100, 0, loadModel.closedWorld.numberOfRequests));
        } else {
            bootstrap
                    .withOpenWorldModel(loadModel.openWorld.rate, loadModel.openWorld.duration)
                    .withMeasurementProgress(new TimeBasedProgressBar(loadModel.openWorld.duration));
        }

        try (var roadrunner = bootstrap.build()) {
            LOG.debug("loading report generators");
            var reportOpts = report;
            if (reportOpts == null) {
                reportOpts = "console";
            }
            var reportConfiguration = ReportConfiguration.parse(reportOpts);
            var chartGeneratorProviders = ChartGeneratorProviders.load();
            var reportGeneratorProvider = chartGeneratorProviders.get(reportConfiguration.reportFormat());
            if (reportGeneratorProvider == null) {
                throw new IllegalArgumentException("report generator %s unknown, supported report formats %s"
                        .formatted(
                                reportConfiguration.reportFormat(), chartGeneratorProviders.supportedReportFormats()));
            }

            var reportConfig = new java.util.HashMap<>(reportConfiguration.configuration());
            reportConfig.put("outputDir", bootstrap.outputDir().toString());
            reportConfig.put("rawLatency", Boolean.toString(rawLatency));

            var chartGenerator = reportGeneratorProvider.create(reportConfig);
            var measurements = roadrunner.execute(() -> protocolProvider.newProtocol());
            chartGenerator.generateChart(measurements.samplesReader());
        }
    }

    private static EnumSet<PauseDetectorKind> parsePauseDetectors(String spec) {
        if (spec == null || spec.isBlank() || "none".equalsIgnoreCase(spec.trim())) {
            return EnumSet.noneOf(PauseDetectorKind.class);
        }
        var kinds = EnumSet.noneOf(PauseDetectorKind.class);
        for (String token : Arrays.stream(spec.split(",")).map(String::trim).toList()) {
            switch (token.toLowerCase()) {
                case "vt" -> kinds.add(PauseDetectorKind.VT_SCHEDULING);
                case "jvm" -> kinds.add(PauseDetectorKind.JVM_PAUSE);
                default -> throw new IllegalArgumentException(
                        "unknown pause detector '" + token + "', expected one of: vt, jvm, none");
            }
        }
        return kinds;
    }
```

The `LatencyRecorder recorder` local is built once and handed to `Bootstrap`. `DefaultRoadrunner` owns the snapshot/close lifecycle (Task 7), so the CLI does not manage it explicitly.

- [ ] **Step 5: Compile + run CLI tests**

Run: `./mvnw -pl roadrunner-cli,roadrunner-cli-tests test`
Expected: existing tests pass.

- [ ] **Step 6: Commit**

```bash
git add roadrunner-cli/pom.xml \
        roadrunner-cli/src/main/java/io/roadrunner/cli/RunCommand.java \
        roadrunner-app/pom.xml
git commit -m "cli: add --pause-detectors and --raw-latency flags"
```

---

## Task 9: Console report prefers `latency.hgrm`

**Files:**
- Modify: `roadrunner-reports-console/src/main/java/io/roadrunner/reports/console/ConsoleReportGenerator.java`

- [ ] **Step 1: Add a helper that loads the histogram from the output dir**

Edit `roadrunner-reports-console/src/main/java/io/roadrunner/reports/console/ConsoleReportGenerator.java`. Add imports:

```java
import io.roadrunner.shaded.hdrhistogram.HistogramLogReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
```

(Some of these may already be present; do not double-import.)

- [ ] **Step 2: Replace `generateChart` to prefer the snapshot**

Replace the entire `generateChart(EventReader eventReader)` method body with:

```java
    @Override
    public void generateChart(EventReader eventReader) throws IOException {
        // Ensure the progress bar line is terminated before the report starts
        System.out.println();

        var rawLatency = Boolean.parseBoolean(properties.getOrDefault("rawLatency", "false"));
        var outputDirProp = properties.get("outputDir");
        var snapshotPath = outputDirProp == null ? null : Paths.get(outputDirProp).resolve("latency.hgrm");
        var useSnapshot = !rawLatency && snapshotPath != null && Files.isRegularFile(snapshotPath);

        Histogram histogram = useSnapshot ? readSnapshotHistogram(snapshotPath) : new Histogram(3);

        // Track the first and last measurement timestamps to calculate total duration
        long firstStartTime = Long.MAX_VALUE;
        var lastStopTime = 0L;

        // Track error counts
        var totalRequests = 0L;
        var errorRequests = 0L;

        for (var event : eventReader) {
            if (event instanceof ProtocolResponse<?> response) {
                totalRequests++;
                if (!useSnapshot) {
                    histogram.recordValue(response.latency());
                }
                firstStartTime = Math.min(firstStartTime, response.scheduledStartTime());
                lastStopTime = Math.max(lastStopTime, response.stopTime());
                if (response instanceof ProtocolResponse.Error) {
                    errorRequests++;
                }
            }
        }

        // Calculate total duration in seconds
        double totalDurationSeconds = (lastStopTime - firstStartTime) / 1_000_000_000.0;

        // Calculate requests per second
        double requestsPerSecond = totalRequests / totalDurationSeconds;

        // Calculate error percentage
        double errorPercentage = totalRequests == 0 ? 0.0 : (double) errorRequests / totalRequests * 100;

        // Calculate error rate (errors per second)
        double errorRate = errorRequests / totalDurationSeconds;

        var lookups = new HashMap<String, String>();
        lookups.put("totalCount", Long.toString(totalRequests));
        lookups.put("successCount", Long.toString(totalRequests - errorRequests));
        lookups.put("errorCount", Long.toString(errorRequests));
        lookups.put("errorPercentage", String.format("%.2f", errorPercentage));
        lookups.put("errorRate", String.format("%.2f", errorRate));
        lookups.put("maxValue", Long.toString(toMillis(histogram.getMaxValue())));
        lookups.put("minValue", Long.toString(toMillis(histogram.getMinValue())));
        lookups.put(
                "meanValue",
                Long.toString(toMillis(Double.valueOf(histogram.getMean()).longValue())));
        lookups.put("p50", Long.toString(toMillis(percentileOf(histogram, 50))));
        lookups.put("p90", Long.toString(toMillis(percentileOf(histogram, 90))));
        lookups.put("p99", Long.toString(toMillis(percentileOf(histogram, 99))));
        lookups.put("p999", Long.toString(toMillis(percentileOf(histogram, 99.9))));
        lookups.put("requestsPerSecond", String.format("%.2f", requestsPerSecond));
        lookups.put("totalDurationSeconds", String.format("%.2f", totalDurationSeconds));

        var stringSubstitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.interpolatorStringLookup(lookups));

        try (var reader = new BufferedReader(new StringSubstitutorReader(
                new InputStreamReader(
                        ConsoleReportGenerator.class.getResourceAsStream("/reports/console/console.tmpl")),
                stringSubstitutor))) {
            reader.lines().forEach(System.out::println);
        }
    }

    private static Histogram readSnapshotHistogram(Path snapshotPath) throws IOException {
        var combined = new Histogram(1L, 3_600_000_000_000L, 3);
        try (var reader = new HistogramLogReader(snapshotPath.toFile())) {
            Histogram h;
            while ((h = (Histogram) reader.nextIntervalHistogram()) != null) {
                combined.add(h);
            }
        }
        return combined;
    }
```

- [ ] **Step 3: Compile and run report tests**

Run: `./mvnw -pl roadrunner-reports-console test`
Expected: `BUILD SUCCESS` (no behavior change for any test that doesn't set `outputDir` in its config; the snapshot path is only used when both `outputDir` is provided and the file exists).

- [ ] **Step 4: Commit**

```bash
git add roadrunner-reports-console/src/main/java/io/roadrunner/reports/console/ConsoleReportGenerator.java
git commit -m "reports-console: prefer latency.hgrm when present unless --raw-latency"
```

---

## Task 10: HTML report prefers `latency.hgrm`

**Files:**
- Modify: `roadrunner-reports-html/src/main/java/io/roadrunner/reports/html/HtmlReportGenerator.java`

- [ ] **Step 1: Wire snapshot loading into HTML**

Edit `roadrunner-reports-html/src/main/java/io/roadrunner/reports/html/HtmlReportGenerator.java`. Add imports:

```java
import io.roadrunner.shaded.hdrhistogram.HistogramLogReader;
import java.nio.file.Files;
import java.util.HashMap;
```

Add two fields next to the existing `outputPath`:

```java
    private final Path snapshotPath;
    private final boolean rawLatency;
```

Update the constructor:

```java
    public HtmlReportGenerator(Map<String, String> configuration) {
        outputPath = Paths.get(
                requireNonNull(configuration.get("outputPath"), "missing required outputPath configuration property"));
        var outputDirCfg = configuration.get("outputDir");
        snapshotPath = outputDirCfg == null ? null : Paths.get(outputDirCfg).resolve("latency.hgrm");
        rawLatency = Boolean.parseBoolean(configuration.getOrDefault("rawLatency", "false"));
    }
```

- [ ] **Step 2: Use the snapshot for percentiles in the HTML template**

Inside `generateChart(EventReader eventReader)`, change the histogram construction. Find:

```java
        var histogram = new Histogram(3);
```

Replace with:

```java
        var useSnapshot = !rawLatency && snapshotPath != null && Files.isRegularFile(snapshotPath);
        Histogram histogram = useSnapshot ? loadSnapshot(snapshotPath) : new Histogram(3);
```

In the same method, inside the `case ProtocolResponse<?> r: { ... }` block, gate the `histogram.recordValue(r.latency());` line so it only runs when not using the snapshot. Replace:

```java
                    case ProtocolResponse<?> r: {
                        histogram.recordValue(r.latency());
                        datapoints.printf("\t{x : %d,y : %d},%n", r.timestamp(), r.latency());
                        break;
                    }
```

with:

```java
                    case ProtocolResponse<?> r: {
                        if (!useSnapshot) {
                            histogram.recordValue(r.latency());
                        }
                        datapoints.printf("\t{x : %d,y : %d},%n", r.timestamp(), r.latency());
                        break;
                    }
```

Add the helper method at the end of the class (just before the final `}`):

```java
    private static Histogram loadSnapshot(Path path) throws IOException {
        var combined = new Histogram(1L, 3_600_000_000_000L, 3);
        try (var reader = new HistogramLogReader(path.toFile())) {
            Histogram h;
            while ((h = (Histogram) reader.nextIntervalHistogram()) != null) {
                combined.add(h);
            }
        }
        return combined;
    }
```

(The existing template only renders `max`/`min`/`mean` from the histogram, which apply to both paths.)

- [ ] **Step 3: Compile and run HTML report tests**

Run: `./mvnw -pl roadrunner-reports-html test`
Expected: `BUILD SUCCESS`. The existing `HtmlReportGeneratorTest` does not set `outputDir`, so it continues to use the CSV-built histogram.

- [ ] **Step 4: Commit**

```bash
git add roadrunner-reports-html/src/main/java/io/roadrunner/reports/html/HtmlReportGenerator.java
git commit -m "reports-html: prefer latency.hgrm when present unless --raw-latency"
```

---

## Task 11: Integration test — open-world + recorder + report roundtrip

**Files:**
- Create: `roadrunner-core-tests/src/it/java/io/roadrunner/core/tests/OpenWorldRecorderIT.java`
- Modify: `roadrunner-core-tests/pom.xml` — add `roadrunner-latency-recording` as a test-scope dependency.

> Integration tests in this repo live under `src/it/java` (the root pom uses `build-helper-maven-plugin` to register that directory as a test source). Surefire skips `*IT.java` by default, so the test is picked up by failsafe during `verify`.

- [ ] **Step 1: Add the test-scope dependency**

Edit `roadrunner-core-tests/pom.xml`. Inside `<dependencies>`, add (test scope):

```xml
        <dependency>
            <groupId>io.roadrunner</groupId>
            <artifactId>roadrunner-latency-recording</artifactId>
            <version>${project.version}</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Write the failing integration test**

Create `roadrunner-core-tests/src/it/java/io/roadrunner/core/tests/OpenWorldRecorderIT.java`:

```java
/**
 * Copyright 2024 Symentis.pl
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.roadrunner.core.tests;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.protocol.Protocol;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.latency.recording.LatencyRecorders;
import io.roadrunner.latency.recording.PauseDetectorKind;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import io.roadrunner.shaded.hdrhistogram.HistogramLogReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenWorldRecorderIT {

    @Test
    void openWorldWritesLatencyHgrmWhenVtDetectorEnabled(@TempDir Path tmp) throws Exception {
        var recorder = LatencyRecorders.create(EnumSet.of(PauseDetectorKind.VT_SCHEDULING));

        var bootstrap = new Bootstrap()
                .withOutputDir(tmp)
                .withLatencyRecorder(recorder)
                .withOpenWorldModel(10, Duration.ofMillis(500));

        try (var roadrunner = bootstrap.build()) {
            Protocol fastProtocol = () -> {
                long start = System.nanoTime();
                long stop = System.nanoTime();
                return ProtocolResponse.empty(start, stop);
            };
            roadrunner.execute(() -> fastProtocol);
        }

        var hgrm = tmp.resolve("latency.hgrm");
        assertThat(hgrm).exists();

        var combined = new Histogram(1L, 3_600_000_000_000L, 3);
        try (var reader = new HistogramLogReader(hgrm.toFile())) {
            Histogram h;
            while ((h = (Histogram) reader.nextIntervalHistogram()) != null) {
                combined.add(h);
            }
        }

        // Open-world at 10 rps for 500ms = ~5 requests. Allow 1-20 range to absorb scheduler
        // jitter on a CI host.
        assertThat(combined.getTotalCount()).isBetween(1L, 20L);
    }

    @Test
    void openWorldWritesEmptyHgrmWhenRecorderIsNoop(@TempDir Path tmp) throws Exception {
        // No withLatencyRecorder() call — bootstrap defaults to LatencyRecorder.noop()
        var bootstrap = new Bootstrap()
                .withOutputDir(tmp)
                .withOpenWorldModel(10, Duration.ofMillis(200));

        try (var roadrunner = bootstrap.build()) {
            Protocol fastProtocol = () -> {
                long start = System.nanoTime();
                long stop = System.nanoTime();
                return ProtocolResponse.empty(start, stop);
            };
            roadrunner.execute(() -> fastProtocol);
        }

        // Noop recorder writes nothing.
        assertThat(Files.exists(tmp.resolve("latency.hgrm"))).isFalse();
    }
}
```

- [ ] **Step 3: Run the integration test**

Run: `./mvnw -pl roadrunner-core-tests verify`
Expected: failsafe runs `OpenWorldRecorderIT` and reports `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 4: Run the full project verify**

Run: `./mvnw clean verify`
Expected: `BUILD SUCCESS` across all modules.

- [ ] **Step 5: Commit**

```bash
git add roadrunner-core-tests/pom.xml \
        roadrunner-core-tests/src/it/java/io/roadrunner/core/tests/OpenWorldRecorderIT.java
git commit -m "core-tests: IT for open-world latency recorder snapshot"
```

---

## Final verification

- [ ] **Run the full build once more:**

  Run: `./mvnw clean verify`
  Expected: `BUILD SUCCESS`.

- [ ] **Smoke test the CLI:**

  Run a manual open-world run with VT detector and verify the snapshot file appears (this is optional — only do if a `vm` protocol invocation is convenient):

  ```bash
  ./mvnw -q package -DskipTests
  mkdir -p /tmp/rr-smoke
  ./roadrunner-app/target/maven-jlink/default/bin/roadrunner run \
      --rate=100 --duration=2s -s /tmp/rr-smoke \
      --pause-detectors=vt vm
  ls /tmp/rr-smoke   # expect: output.csv  latency.hgrm
  ```

  Expected: both `output.csv` and `latency.hgrm` present in `/tmp/rr-smoke`, and the console report renders percentiles.
