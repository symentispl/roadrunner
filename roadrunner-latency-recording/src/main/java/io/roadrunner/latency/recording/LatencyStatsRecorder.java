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
