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

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ReportModel(
        long total,
        long success,
        long errors,
        double durationSeconds,
        double throughput,
        double errorPercentage,
        long minMs,
        long meanMs,
        long maxMs,
        long p50,
        long p90,
        long p99,
        long p999,
        long[] latencyBuckets,
        long[] throughputSeries,
        Map<String, Long> statusBreakdown) {

    private static final int LATENCY_BUCKETS = 20;
    private static final int TIME_SLICES = 30;

    public static ReportModel from(
            EventReader reader, Histogram histogram, long firstStart, long lastStop, long total, long errors) {
        // Guard the empty/degenerate run: with no responses, firstStart stays Long.MAX_VALUE and
        // lastStop stays 0, which would otherwise yield a huge negative duration.
        var window = lastStop - firstStart;
        var durationSeconds = window > 0 ? window / 1_000_000_000.0 : 0.0;
        var throughput = durationSeconds > 0 ? total / durationSeconds : 0.0;
        var errorPercentage = total == 0 ? 0.0 : (double) errors / total * 100;

        var series = new long[TIME_SLICES];
        var windowNanos = Math.max(1L, lastStop - firstStart);
        var status = new LinkedHashMap<String, Long>();
        var statusKey = reader.attachmentKeys().stream()
                .filter(k -> k.name().toLowerCase(Locale.ROOT).contains("status"))
                .findFirst()
                .orElse(null);
        for (var event : reader) {
            if (event instanceof SamplerResponse<?> response) {
                var offset = response.scheduledStartTime() - firstStart;
                var slice = (int) Math.min(TIME_SLICES - 1, offset * TIME_SLICES / windowNanos);
                if (slice >= 0) {
                    series[slice]++;
                }
                if (statusKey != null) {
                    var v = response.attachmentValueAt(statusKey);
                    if (v != null) {
                        status.merge(v, 1L, Long::sum);
                    }
                }
            }
        }

        var buckets = new long[LATENCY_BUCKETS];
        var minV = histogram.getMinValue();
        var maxV = histogram.getMaxValue();
        var range = Math.max(1L, maxV - minV);
        for (var v : histogram.recordedValues()) {
            var idx = (int) Math.min(
                    LATENCY_BUCKETS - 1L, (v.getValueIteratedTo() - minV) * LATENCY_BUCKETS / range);
            if (idx >= 0) {
                buckets[idx] += v.getCountAtValueIteratedTo();
            }
        }

        return new ReportModel(
                total,
                total - errors,
                errors,
                durationSeconds,
                throughput,
                errorPercentage,
                toMillis(histogram.getMinValue()),
                toMillis((long) histogram.getMean()),
                toMillis(histogram.getMaxValue()),
                toMillis(histogram.getValueAtPercentile(50)),
                toMillis(histogram.getValueAtPercentile(90)),
                toMillis(histogram.getValueAtPercentile(99)),
                toMillis(histogram.getValueAtPercentile(99.9)),
                buckets,
                series,
                status);
    }

    private static long toMillis(long nanos) {
        return Duration.ofNanos(nanos).toMillis();
    }
}
