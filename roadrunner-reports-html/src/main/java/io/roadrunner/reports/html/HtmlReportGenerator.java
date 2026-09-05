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

import io.roadrunner.reports.ReportGenerator;
import io.roadrunner.reports.ReportModel;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.io.StringSubstitutorReader;
import org.apache.commons.text.lookup.StringLookupFactory;

public class HtmlReportGenerator implements ReportGenerator {

    private final Path outputDir;

    public HtmlReportGenerator(Map<String, String> configuration) {
        outputDir = Paths.get(
                requireNonNull(configuration.get("outputDir"), "missing required outputDir configuration property"));
    }

    @Override
    public void generate(ReportModel model) throws IOException {
        var indexHtml = outputDir.resolve("index.html");
        var datapointsJs = outputDir.resolve("data.js");
        var usersJs = outputDir.resolve("users.js");

        Files.createDirectories(outputDir);

        var latencyOverTime = model.latencyOverTime();
        try (var datapoints = new PrintStream(datapointsJs.toFile())) {
            datapoints.println("const datapoints = {");
            writeSeries(datapoints, "p50", latencyOverTime.p50(), model.durationSeconds());
            writeSeries(datapoints, "p90", latencyOverTime.p90(), model.durationSeconds());
            writeSeries(datapoints, "p99", latencyOverTime.p99(), model.durationSeconds());
            datapoints.println("};");
        }

        try (var users = new PrintStream(usersJs.toFile())) {
            users.println("const users = [");
            writePoints(users, model.usersOverTime(), model.durationSeconds());
            users.println("];");
        }

        var map = Map.of("max", model.maxMs(), "min", model.minMs(), "mean", model.meanMs());
        var stringSubstitutor = new StringSubstitutor(StringLookupFactory.INSTANCE.interpolatorStringLookup(map));

        try (var reader = new StringSubstitutorReader(
                        new InputStreamReader(
                                HtmlReportGenerator.class.getResourceAsStream("/reports/html/index.html.tmpl")),
                        stringSubstitutor);
                var writer = new FileWriter(indexHtml.toFile())) {
            IOUtils.copy(reader, writer);
        }
    }

    private static void writeSeries(PrintStream out, String name, long[] series, double durationSeconds) {
        out.printf(Locale.ROOT, "\t%s : [%n", name);
        writePoints(out, series, durationSeconds);
        out.println("\t],");
    }

    private static void writePoints(PrintStream out, long[] series, double durationSeconds) {
        for (var i = 0; i < series.length; i++) {
            var elapsedSeconds = durationSeconds * i / series.length;
            out.printf(Locale.ROOT, "\t{x : %.2f, y : %d},%n", elapsedSeconds, series[i]);
        }
    }
}
