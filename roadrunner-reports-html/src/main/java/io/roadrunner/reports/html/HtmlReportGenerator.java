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
package io.roadrunner.reports.html;

import static java.util.Objects.requireNonNull;

import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.api.reports.ReportGenerator;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.io.StringSubstitutorReader;
import org.apache.commons.text.lookup.StringLookupFactory;

public class HtmlReportGenerator implements ReportGenerator {

    private final Path outputPath;

    public HtmlReportGenerator(Map<String, String> configuration) {
        outputPath = Paths.get(
                requireNonNull(configuration.get("outputPath"), "missing required outputPath configuration property"));
    }

    @Override
    public void generateChart(EventReader eventReader) throws IOException {
        var indexHtml = outputPath.resolve("index.html");
        var datapointsJs = outputPath.resolve("data.js");
        var usersJs = outputPath.resolve("users.js");

        var histogram = new Histogram(3);

        int u = 0;
        try (PrintStream datapoints = new PrintStream(datapointsJs.toFile());
                PrintStream users = new PrintStream(usersJs.toFile())) {
            datapoints.println("const datapoints = [");
            users.println(("const users = ["));
            for (Event event : eventReader) {
                switch (event) {
                    case ProtocolResponse<?> r: {
                        histogram.recordValue(r.latency());
                        datapoints.printf("\t{x : %d,y : %d},%n", r.timestamp(), r.latency());
                        break;
                    }
                    case UserEvent.Enter e: {
                        u++;
                        users.printf("\t{x : %d,y : %d},%n", e.timestamp(), u);
                        break;
                    }
                    case UserEvent.Exit e: {
                        u--;
                        users.printf("\t{x : %d,y : %d},%n", e.timestamp(), u);
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unexpected value: " + event);
                }
            }
            datapoints.println("];");
            users.println("];");
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
                                HtmlReportGenerator.class.getResourceAsStream("/reports/html/index.html.tmpl")),
                        stringSubstitutor);
                var writer = new FileWriter(indexHtml.toFile())) {
            IOUtils.copy(reader, writer);
        }
    }
}
