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
package io.roadrunner.cli;

import io.roadrunner.reports.ComparisonModel;
import io.roadrunner.reports.RunDirectory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(description = "Compare two finished runs, optionally failing when a metric is outside its tolerance")
class DiffCommand {

    @Parameters(index = "0", paramLabel = "<baseline-dir>", description = "Output directory of the baseline run")
    Path baseline;

    @Parameters(index = "1", paramLabel = "<current-dir>", description = "Output directory of the run to check")
    Path current;

    @Option(
            names = "--tolerance",
            description =
                    "Bound the change of metrics, e.g. 'p99:+10%,throughput:-5%,errors:+0.5%'. Metrics not listed are reported but not checked. Exits with code 3 when a bound is exceeded.")
    String tolerance;

    @Option(
            names = "-r",
            description = "Report format to generate: console (default). Console options: -r console:nocolors=true for no colours, -r console:ascii=true for no box-drawing characters",
            converter = PrefixedMap.Converter.class)
    PrefixedMap report = new PrefixedMap("console", Map.of());

    @Option(
            names = "--raw-latency",
            description =
                    "Always build reports from raw per-request latencies, even when pause-corrected data is available.")
    boolean rawLatency;

    public void run(java.io.PrintWriter out) throws Exception {
        var tolerances = ComparisonReports.parseTolerances(tolerance);
        ComparisonReports.requireRunDirectory(baseline, "baseline");
        ComparisonReports.requireRunDirectory(current, "current");

        try (var reportPlugins = ReportPlugins.load(new Preferences(Paths.get(System.getProperty("user.home"))))) {
            var reportPlugin = reportPlugins.get(report.prefix());
            if (reportPlugin == null) {
                throw new IllegalArgumentException("report generator %s unknown, supported report formats %s"
                        .formatted(report.prefix(), reportPlugins.supportedReportFormats()));
            }
            var config = new HashMap<>(report.parameters());
            config.put("outputDir", current.toString());
            var generator = ComparisonReports.generator(reportPlugin, config);

            var comparison = ComparisonModel.of(
                    ComparisonReports.load(baseline, rawLatency),
                    ComparisonReports.load(current, rawLatency),
                    tolerances,
                    RunDirectory.of(baseline),
                    RunDirectory.of(current));
            ComparisonReports.report(comparison, generator, out);
        }
    }
}
