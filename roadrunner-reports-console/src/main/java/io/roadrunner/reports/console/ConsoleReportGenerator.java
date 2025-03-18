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

import io.roadrunner.api.measurments.Sample;
import io.roadrunner.api.measurments.SamplesReader;
import io.roadrunner.api.reports.ReportGenerator;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.io.StringSubstitutorReader;
import org.apache.commons.text.lookup.StringLookupFactory;

final class ConsoleReportGenerator implements ReportGenerator {

    private final Map<String, String> properties;

    ConsoleReportGenerator(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public void generateChart(SamplesReader samplesReader) throws IOException {
        var histogram = new Histogram(3);

        // Track the first and last measurement timestamps to calculate total duration
        long firstStartTime = Long.MAX_VALUE;
        var lastStopTime = 0L;

        // Track error counts
        var totalRequests = 0L;
        var errorRequests = 0L;

        for (Sample sample : samplesReader) {
            try {
                totalRequests++;
                var responseTime = sample.stopTime() - sample.startTime();
                histogram.recordValue(responseTime);

                // Check if this is an error response
                if (sample.status() == Sample.Status.KO) {
                    errorRequests++;
                }

                // Update first start time and last stop time
                firstStartTime = Math.min(firstStartTime, sample.startTime());
                lastStopTime = Math.max(lastStopTime, sample.stopTime());
            } catch (NumberFormatException e) {
                System.out.println("invalid line");
            }
        }

        // Calculate total duration in seconds
        double totalDurationSeconds = (lastStopTime - firstStartTime) / 1_000_000_000.0;

        // Calculate requests per second
        double requestsPerSecond = totalRequests / totalDurationSeconds;

        // Calculate error percentage
        double errorPercentage = (double) errorRequests / totalRequests * 100;

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

    private static long percentileOf(Histogram histogram, double percentile) {
        return histogram.getValueAtPercentile(percentile);
    }

    private static long toMillis(long maxValue) {
        return Duration.ofNanos(maxValue).toMillis();
    }
}
