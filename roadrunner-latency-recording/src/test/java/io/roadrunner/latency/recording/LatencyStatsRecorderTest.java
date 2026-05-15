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
import io.roadrunner.shaded.hdrhistogram.EncodableHistogram;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import io.roadrunner.shaded.hdrhistogram.HistogramLogReader;
import java.io.IOException;
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

        var combined = new Histogram(1_000L, 3_600_000_000_000L, 3);
        try (var reader = new HistogramLogReader(hgrm.toFile())) {
            EncodableHistogram next;
            while ((next = reader.nextIntervalHistogram()) != null) {
                if (next instanceof Histogram h) {
                    combined.add(h);
                }
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
