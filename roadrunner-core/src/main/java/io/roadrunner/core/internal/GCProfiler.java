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
package io.roadrunner.core.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import jdk.jfr.consumer.RecordingStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Profiles GC activity during a benchmark run using JFR event streaming (JEP 349).
 *
 * <p>Events are delivered asynchronously to an {@code onEvent} handler by the JFR dispatch thread.
 * {@link #close()} blocks until all in-flight handlers have completed, so aggregation after close
 * is safe without additional synchronisation.
 *
 * <p>Usage:
 * <pre>{@code
 * try (var profiler = new GCProfiler()) {
 *     profiler.start();
 *     // ... benchmark ...
 * } // prints GC stats on close
 * }</pre>
 */
final class GCProfiler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GCProfiler.class);

    private record GcPause(String cause, Duration sumOfPauses) {}

    private final RecordingStream stream;
    // Written by JFR dispatch thread, read by main thread after stream.close().
    // CopyOnWriteArrayList avoids external synchronisation while keeping close() safe.
    private final List<GcPause> pauses = new CopyOnWriteArrayList<>();

    GCProfiler() {
        this.stream = new RecordingStream();
        stream.enable("jdk.GarbageCollection");
        stream.onEvent(
                "jdk.GarbageCollection",
                event -> pauses.add(new GcPause(event.getString("cause"), event.getDuration("sumOfPauses"))));
    }

    void start() {
        stream.startAsync();
    }

    @Override
    public void close() {
        // Blocks until the stream is closed and all onEvent handlers have returned.
        stream.close();
        printStats();
    }

    private void printStats() {
        if (pauses.isEmpty()) {
            LOG.info("GC Statistics: no GC events recorded");
            return;
        }

        var pausesByCause = new TreeMap<String, List<Duration>>();
        var allPauses = new ArrayList<Duration>(pauses.size());

        for (var pause : pauses) {
            pausesByCause.computeIfAbsent(pause.cause(), k -> new ArrayList<>()).add(pause.sumOfPauses());
            allPauses.add(pause.sumOfPauses());
        }

        var totalNs = allPauses.stream().mapToLong(Duration::toNanos).sum();

        LOG.info("GC Statistics:");
        LOG.info("  Total GC events: {}", allPauses.size());
        LOG.info("  Total pause time: {}", formatMs(totalNs));

        LOG.info("  Pauses by cause:");
        for (var entry : pausesByCause.entrySet()) {
            var durations = entry.getValue();
            var avgNs = (long)
                    durations.stream().mapToLong(Duration::toNanos).average().orElse(0);
            var maxNs = durations.stream().mapToLong(Duration::toNanos).max().orElse(0);
            LOG.info(
                    "    {}: {} events, avg {}, max {}",
                    entry.getKey(),
                    durations.size(),
                    formatMs(avgNs),
                    formatMs(maxNs));
        }

        Collections.sort(allPauses);
        LOG.info("  Pause duration distribution:");
        LOG.info("    min: {}", formatMs(allPauses.getFirst().toNanos()));
        LOG.info("    p50: {}", formatMs(percentile(allPauses, 50).toNanos()));
        LOG.info("    p90: {}", formatMs(percentile(allPauses, 90).toNanos()));
        LOG.info("    p99: {}", formatMs(percentile(allPauses, 99).toNanos()));
        LOG.info("    max: {}", formatMs(allPauses.getLast().toNanos()));
    }

    private static String formatMs(long nanos) {
        return "%.2f ms".formatted(nanos / 1_000_000.0);
    }

    /**
     * Nearest-rank percentile over a sorted list.
     */
    private static Duration percentile(List<Duration> sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.clamp(index, 0, sorted.size() - 1));
    }
}
