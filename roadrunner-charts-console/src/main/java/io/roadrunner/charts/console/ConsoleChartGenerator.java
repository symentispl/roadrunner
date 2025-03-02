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
package io.roadrunner.charts.console;

import static java.lang.Math.round;
import static java.time.Duration.ofNanos;

import io.roadrunner.api.charts.ChartGenerator;
import io.roadrunner.api.measurments.Measurement;
import io.roadrunner.api.measurments.MeasurementsReader;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import java.util.Properties;

public class ConsoleChartGenerator implements ChartGenerator {

    private final Properties properties;

    public ConsoleChartGenerator(Properties properties) {
        this.properties = properties;
    }

    @Override
    public void generateChart(MeasurementsReader measurementsReader) {
        var histogram = new Histogram(3);

        for (Measurement measurement : measurementsReader) {
            try {
                var responseTime = measurement.stopTime() - measurement.startTime();
                histogram.recordValue(responseTime);
            } catch (NumberFormatException e) {
                System.out.println("invalid line");
            }
        }

        System.out.println("HdrHistogram Summary:");
        System.out.println("=====================");
        System.out.printf("Total Count    : %d%n", histogram.getTotalCount());
        System.out.printf("Min Value (ms) : %d ms%n", getMillis(histogram.getMinValue()));
        System.out.printf("Max Value (ms) : %d ms%n", getMillis(histogram.getMaxValue()));
        System.out.printf("Mean Value (ms): %d ms%n", getMillis(histogram.getMean()));
        System.out.printf("50th Percentile: %d ms%n", getMillis(histogram.getValueAtPercentile(50)));
        System.out.printf("90th Percentile: %d ms%n", getMillis(histogram.getValueAtPercentile(90)));
        System.out.printf("99th Percentile: %d ms%n", getMillis(histogram.getValueAtPercentile(99)));
        System.out.printf("99.9th Percentile: %d ms%n", getMillis(histogram.getValueAtPercentile(99.9)));
        System.out.println("=====================");
    }

    private static long getMillis(double value) {
        return ofNanos(round(value)).toMillis();
    }
}
