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
import io.roadrunner.shaded.hdrhistogram.EncodableHistogram;
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

        var combined = new Histogram(1_000L, 3_600_000_000_000L, 3);
        try (var reader = new HistogramLogReader(hgrm.toFile())) {
            EncodableHistogram next;
            while ((next = reader.nextIntervalHistogram()) != null) {
                if (next instanceof Histogram h) {
                    combined.add(h);
                }
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
