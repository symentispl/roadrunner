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
package io.roadrunner.charts.html;

import io.roadrunner.api.charts.ChartGenerator;
import io.roadrunner.api.measurments.Measurement;
import io.roadrunner.api.measurments.MeasurementsReader;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.io.StringSubstitutorReader;
import org.apache.commons.text.lookup.StringLookupFactory;

public class HtmlChartGenerator implements ChartGenerator {

    private final Path outputPath;

    public HtmlChartGenerator(Properties properties) {
        outputPath = Paths.get(properties.get("outputPath").toString());
    }

    @Override
    public void generateChart(MeasurementsReader measurementsReader) throws IOException {
        var indexHtml = outputPath.resolve("index.html");
        var datapointsJs = outputPath.resolve("data.js");

        var histogram = new Histogram(3);

        try (PrintStream out = new PrintStream(datapointsJs.toFile())) {
            out.println("const datapoints = [");
            for (Measurement measurement : measurementsReader) {
                try {
                    var responseTime = measurement.stopTime() - measurement.startTime();
                    histogram.recordValue(responseTime);
                    out.println("\t{x : %d,y : %d},".formatted(measurement.startTime(), responseTime));
                } catch (NumberFormatException e) {
                    System.out.println("invalid line");
                }
            }
            out.println("];");
        }

        var map = Map.of(
                "max",
                Duration.ofNanos(histogram.getMaxValue()).toMillis(),
                "min",
                Duration.ofNanos(histogram.getMinValue()).toMillis(),
                "mean",
                Duration.ofNanos(Double.valueOf(histogram.getMean()).longValue())
                        .toMillis());

        var stringSubstitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.interpolatorStringLookup(map));

        try (var reader = new StringSubstitutorReader(
                        new InputStreamReader(
                                HtmlChartGenerator.class.getResourceAsStream("/templates/index.html.tmpl")),
                        stringSubstitutor);
                var writer = new FileWriter(indexHtml.toFile())) {
            IOUtils.copy(reader, writer);
        }
    }
}
