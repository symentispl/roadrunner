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
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ReportModelTest {

    private static EventReader readerOf(List<Event> events) {
        return events::iterator;
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
        assertThat(model.durationSeconds()).isCloseTo(2.0, Assertions.within(0.001));
        assertThat(model.throughput()).isCloseTo(2.0, Assertions.within(0.001));
        assertThat(model.errorPercentage()).isCloseTo(25.0, Assertions.within(0.001));
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

    @Test
    void derivesCountsAndWindowFromTheEvents() {
        var model = ReportModel.from(readerOf(List.of(
                response(0L, 1_000_000_000L, 10_000_000L),
                error(1_000_000_000L, 2_000_000_000L, 20_000_000L),
                response(1_000_000_000L, 2_000_000_000L, 30_000_000L))));

        assertThat(model.total()).isEqualTo(3L);
        assertThat(model.errors()).isEqualTo(1L);
        assertThat(model.success()).isEqualTo(2L);
        assertThat(model.durationSeconds()).isCloseTo(2.0, Assertions.within(0.001));
        // Latencies pass through a 3-significant-digit histogram and are then floored to whole
        // milliseconds, so 10 ms can come back as 9. Assert the magnitude, not the exact value.
        assertThat(model.minMs()).isCloseTo(10L, Assertions.within(1L));
        assertThat(model.maxMs()).isCloseTo(30L, Assertions.within(1L));
    }

    @Test
    void percentilesPerTimeSliceFollowTheResponsesInThatSlice() {
        // A three second window, so slice n covers 100 ms: one response at the start (slice 0), one
        // halfway (slice 15) and one at the very end (slice 29).
        var model = ReportModel.from(readerOf(List.of(
                response(0L, 3_000_000_000L, 10_000_000L),
                response(1_500_000_000L, 3_000_000_000L, 20_000_000L),
                response(2_999_999_999L, 3_000_000_000L, 30_000_000L))));

        var latencyOverTime = model.latencyOverTime();
        assertThat(latencyOverTime.p50()).hasSize(30);
        assertThat(latencyOverTime.p90()).hasSize(30);
        assertThat(latencyOverTime.p99()).hasSize(30);

        // within(1) because latencies pass through a 3-significant-digit histogram and are floored
        // to whole milliseconds; the point is which slice each response landed in.
        assertThat(latencyOverTime.p50()[0]).isCloseTo(10L, Assertions.within(1L));
        assertThat(latencyOverTime.p50()[15]).isCloseTo(20L, Assertions.within(1L));
        assertThat(latencyOverTime.p99()[29]).isCloseTo(30L, Assertions.within(1L));
    }

    @Test
    void slicesWithoutResponsesHoldZero() {
        var model = ReportModel.from(readerOf(List.of(response(0L, 1_000_000_000L, 10_000_000L))));

        // Everything landed in slice 0, so every other slice is untouched rather than absent.
        assertThat(model.latencyOverTime().p50()[1]).isZero();
        assertThat(model.latencyOverTime().p99()[29]).isZero();
    }

    @Test
    void usersOverTimeTracksConcurrencyAndCarriesForwardBetweenEvents() {
        // A three second window: two users enter in slice 0, one leaves halfway (slice 15), and the
        // last response anchors the window so every slice up to the end carries the last known count.
        var model = ReportModel.from(readerOf(List.of(
                new UserEvent.Enter(0L),
                new UserEvent.Enter(0L),
                response(0L, 3_000_000_000L, 10_000_000L),
                new UserEvent.Exit(1_500_000_000L),
                response(2_999_999_999L, 3_000_000_000L, 20_000_000L))));

        assertThat(model.usersOverTime()).hasSize(30);
        assertThat(model.usersOverTime()[0]).isEqualTo(2L);
        assertThat(model.usersOverTime()[10]).isEqualTo(2L); // carried forward, no event in this slice
        assertThat(model.usersOverTime()[15]).isEqualTo(1L);
        assertThat(model.usersOverTime()[29]).isEqualTo(1L); // carried forward to the end of the run
    }

    private static Event response(long scheduledStart, long stopTime, long latency) {
        return new SamplerResponse.Response(scheduledStart, stopTime)
                .withScheduledStartTime(scheduledStart)
                .withLatency(latency);
    }

    private static Event error(long scheduledStart, long stopTime, long latency) {
        return new SamplerResponse.Error(scheduledStart, stopTime)
                .withScheduledStartTime(scheduledStart)
                .withLatency(latency);
    }
}
