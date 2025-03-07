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

import io.roadrunner.api.measurments.Measurement;
import io.roadrunner.api.measurments.MeasurementsReader;
import io.roadrunner.api.reports.ReportGenerator;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
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
    public void generateChart(MeasurementsReader measurementsReader) throws IOException {
        var histogram = new Histogram(3);

        for (Measurement measurement : measurementsReader) {
            try {
                var responseTime = measurement.stopTime() - measurement.startTime();
                histogram.recordValue(responseTime);
            } catch (NumberFormatException e) {
                System.out.println("invalid line");
            }
        }

        var map = Map.of(
                "totalCount", histogram.getTotalCount(),
                "maxValue", toMillis(histogram.getMaxValue()),
                "minValue", toMillis(histogram.getMinValue()),
                "meanValue", toMillis(Double.valueOf(histogram.getMean()).longValue()),
                "p50", toMillis(percentileOf(histogram, 50)),
                "p90", toMillis(percentileOf(histogram, 90)),
                "p99", toMillis(percentileOf(histogram, 99)),
                "p999", toMillis(percentileOf(histogram, 99.9)));

        var stringSubstitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.interpolatorStringLookup(map));

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
