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

import io.roadrunner.output.csv.CsvOutputEventReader;
import io.roadrunner.reports.ComparisonModel;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.RunDirectory;
import io.roadrunner.reports.Tolerances;
import io.roadrunner.reports.spi.ReportComparisonGenerator;
import io.roadrunner.reports.spi.ReportPlugin;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** What {@code diff} and {@code run --baseline} share: read the runs, compare them, render, and gate on the verdict. */
final class ComparisonReports {

    private ComparisonReports() {}

    static Tolerances parseTolerances(String tolerance) {
        return tolerance == null ? Tolerances.empty() : Tolerances.parse(tolerance);
    }

    /** Fails early, before a load test runs for minutes, when the directory is not a finished run. */
    static void requireRunDirectory(Path dir, String role) {
        if (!Files.isDirectory(dir) || !Files.isRegularFile(RunDirectory.of(dir).outputCsv())) {
            throw new IllegalArgumentException(
                    "%s run directory %s is not readable: expected a directory containing output.csv"
                            .formatted(role, dir));
        }
    }

    /** The new run must not write into the baseline: that would overwrite what it is compared against. */
    static void requireDistinct(Path baseline, Path outputDir) throws IOException {
        if (outputDir == null || !Files.exists(outputDir)) {
            return; // a fresh directory cannot be the baseline
        }
        if (Files.isSameFile(baseline, outputDir)) {
            throw new IllegalArgumentException(
                    "output directory %s is the baseline directory: the run would overwrite the baseline"
                            .formatted(outputDir));
        }
    }

    static ReportModel load(Path dir, boolean rawLatency) throws IOException {
        var runDirectory = RunDirectory.of(dir);
        return ReportModel.from(new CsvOutputEventReader(runDirectory.outputCsv()), runDirectory, rawLatency);
    }

    static ReportComparisonGenerator generator(ReportPlugin plugin, Map<String, String> configuration) {
        return plugin.reportComparisonProvider()
                .orElseThrow(() -> new IllegalArgumentException(
                        "report format %s does not support comparison".formatted(plugin.name())))
                .create(configuration);
    }

    /** Renders the comparison, then throws {@link ToleranceBreachException} if any tolerance was breached. */
    static void report(ComparisonModel comparison, ReportComparisonGenerator generator, PrintWriter out)
            throws IOException {
        generator.generate(comparison, out);
        out.flush();
        if (comparison.isFailed()) {
            throw new ToleranceBreachException("tolerance breached: " + breaches(comparison));
        }
    }

    private static String breaches(ComparisonModel comparison) {
        return comparison.metrics().values().stream()
                .filter(c -> c.isFailed())
                .sorted(java.util.Comparator.comparing(c -> c.metric().ordinal()))
                .map(c -> "%s %s".formatted(c.metric().label(), String.format(Locale.ROOT, "%+.2f", c.delta())))
                .collect(Collectors.joining(", "));
    }
}
