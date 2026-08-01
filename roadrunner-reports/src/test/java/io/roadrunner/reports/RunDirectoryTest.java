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
package io.roadrunner.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import io.roadrunner.shaded.hdrhistogram.HistogramLogWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunDirectoryTest {

    @Test
    void hasNoSnapshotWhenTheRunRecordedNone(@TempDir Path dir) throws IOException {
        assertThat(RunDirectory.of(dir).latencySnapshot()).isEmpty();
    }

    @Test
    void readsTheSnapshotAndMergesItsIntervals(@TempDir Path dir) throws IOException {
        writeSnapshot(dir, 40_000_000L, 60_000_000L);

        var snapshot = RunDirectory.of(dir).latencySnapshot().orElseThrow();

        assertThat(snapshot.getTotalCount()).isEqualTo(2L);
        assertThat(snapshot.getMaxValue()).isGreaterThanOrEqualTo(60_000_000L);
    }

    @Test
    void snapshotSupersedesMeasuredLatenciesUnlessRawIsAsked(@TempDir Path dir) throws IOException {
        // The snapshot says 100 ms; the events say 10 ms. Which one wins is the whole point of the
        // --raw-latency flag.
        writeSnapshot(dir, 100_000_000L);
        var reader = readerOf(List.of(response(0L, 1_000_000_000L, 10_000_000L)));

        var corrected = ReportModel.from(reader, RunDirectory.of(dir), false);
        var raw = ReportModel.from(reader, RunDirectory.of(dir), true);

        assertThat(corrected.p99()).isEqualTo(100L);
        assertThat(raw.p99()).isEqualTo(10L);
    }

    private static void writeSnapshot(Path dir, long... latenciesNanos) throws IOException {
        var histogram = new Histogram(1_000L, 3_600_000_000_000L, 3);
        for (var latency : latenciesNanos) {
            histogram.recordValue(latency);
        }
        histogram.setStartTimeStamp(1_000L);
        histogram.setEndTimeStamp(2_000L);
        try (var out = new PrintStream(dir.resolve("latency.hgrm").toFile(), "UTF-8")) {
            var writer = new HistogramLogWriter(out);
            writer.outputLogFormatVersion();
            writer.outputLegend();
            writer.outputIntervalHistogram(histogram);
        }
    }

    private static EventReader readerOf(List<Event> events) {
        return events::iterator;
    }

    private static Event response(long scheduledStart, long stopTime, long latency) {
        return new SamplerResponse.Response(scheduledStart, stopTime)
                .withScheduledStartTime(scheduledStart)
                .withLatency(latency);
    }
}
