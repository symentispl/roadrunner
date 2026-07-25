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
package io.roadrunner.reports.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportModelTest {

    private static EventReader readerOf(List<Event> events) {
        return new EventReader() {
            @Override
            public Iterator<Event> iterator() {
                return events.iterator();
            }
        };
    }

    @Test
    void computesThroughputAndErrorRate() {
        // 4 responses over a 2-second window: start=0, stop=2_000_000_000 ns
        var hist = new Histogram(3);
        hist.recordValue(1_000_000);
        var model = ReportModel.from(readerOf(List.of()), hist, 0L, 2_000_000_000L, 4L, 1L);

        assertThat(model.total()).isEqualTo(4L);
        assertThat(model.success()).isEqualTo(3L);
        assertThat(model.errors()).isEqualTo(1L);
        assertThat(model.durationSeconds()).isCloseTo(2.0, within(0.001));
        assertThat(model.throughput()).isCloseTo(2.0, within(0.001));
        assertThat(model.errorPercentage()).isCloseTo(25.0, within(0.001));
    }

    @Test
    void throughputSeriesHasFixedLength() {
        var hist = new Histogram(3);
        hist.recordValue(1_000_000);
        var model = ReportModel.from(readerOf(List.of()), hist, 0L, 1_000_000_000L, 1L, 0L);
        assertThat(model.throughputSeries()).hasSize(30);
        assertThat(model.latencyBuckets()).hasSize(20);
    }

    @Test
    void latencyBucketsDoNotDoubleCountConcentratedValues() {
        // 1000 identical recorded values must land in exactly one bucket each,
        // not be counted in every bucket (regression for percentile-boundary overlap).
        var hist = new Histogram(3);
        for (var i = 0; i < 1000; i++) {
            hist.recordValue(5_000_000);
        }
        var model = ReportModel.from(readerOf(List.of()), hist, 0L, 1_000_000_000L, 1000L, 0L);

        var buckets = model.latencyBuckets();
        assertThat(Arrays.stream(buckets).sum()).isEqualTo(1000L);
        var nonZeroBuckets = Arrays.stream(buckets).filter(c -> c > 0).count();
        assertThat(nonZeroBuckets).isLessThanOrEqualTo(2L);
    }
}
