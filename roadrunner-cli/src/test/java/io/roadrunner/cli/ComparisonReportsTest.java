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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roadrunner.reports.ComparisonModel;
import io.roadrunner.reports.Tolerances;
import io.roadrunner.reports.spi.ReportComparisonGenerator;
import io.roadrunner.reports.spi.ReportComparisonProvider;
import io.roadrunner.reports.spi.ReportGeneratorProvider;
import io.roadrunner.reports.spi.ReportPlugin;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComparisonReportsTest {

    // one request every millisecond, each taking latencyMillis
    private static Path runDirectory(Path dir, long latencyMillis) throws IOException {
        var latency = latencyMillis * 1_000_000;
        var rows = IntStream.range(0, 100)
                .mapToObj(i -> "REQ,%d,%d,%d,%d,OK"
                        .formatted(i * 1_000_000L, i * 1_000_000L, i * 1_000_000L + latency, latency))
                .collect(Collectors.joining("\n"));
        Files.writeString(dir.resolve("output.csv"), "type,scheduled,start,stop,latency,status\n" + rows + "\n");
        return dir;
    }

    private static ReportComparisonGenerator recording() {
        return (model, out) -> out.println("verdict " + model.verdict());
    }

    @Test
    void loadsAModelFromARunDirectory(@TempDir Path dir) throws Exception {
        var model = ComparisonReports.load(runDirectory(dir, 10), true);

        assertThat(model.total()).isEqualTo(100);
        assertThat(model.errors()).isZero();
        assertThat(model.p99()).isEqualTo(10);
    }

    @Test
    void rejectsADirectoryWithoutOutputCsv(@TempDir Path dir) {
        assertThatThrownBy(() -> ComparisonReports.requireRunDirectory(dir, "baseline"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseline run directory")
                .hasMessageContaining("output.csv");
        assertThatThrownBy(() -> ComparisonReports.requireRunDirectory(dir.resolve("missing"), "current"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("current run directory");
    }

    @Test
    void rejectsToleranceMalformedInput() {
        assertThatThrownBy(() -> ComparisonReports.parseTolerances("p99:ten"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsWithoutErrorWhenEverythingIsWithinTolerance(@TempDir Path baseline, @TempDir Path current)
            throws Exception {
        var comparison = ComparisonModel.of(
                ComparisonReports.load(runDirectory(baseline, 10), true),
                ComparisonReports.load(runDirectory(current, 10), true),
                Tolerances.parse("p99:+10%"));
        var out = new StringWriter();

        assertThatCode(() -> ComparisonReports.report(comparison, recording(), new PrintWriter(out)))
                .doesNotThrowAnyException();
        assertThat(out.toString()).contains("verdict PASSED");
    }

    @Test
    void rendersTheComparisonAndThenFailsOnABreach(@TempDir Path baseline, @TempDir Path current) throws Exception {
        var comparison = ComparisonModel.of(
                ComparisonReports.load(runDirectory(baseline, 10), true),
                ComparisonReports.load(runDirectory(current, 20), true),
                Tolerances.parse("p99:+10%"));
        var out = new StringWriter();

        assertThatThrownBy(() -> ComparisonReports.report(comparison, recording(), new PrintWriter(out)))
                .isInstanceOf(ToleranceBreachException.class)
                .hasMessageContaining("p99");
        assertThat(out.toString()).contains("verdict FAILED");
    }

    @Test
    void refusesAReportFormatWithoutComparisonSupport() {
        var plugin = new ReportPlugin() {
            @Override
            public String name() {
                return "plain";
            }

            @Override
            public ReportGeneratorProvider reportGeneratorProvider() {
                return config -> (model, out) -> {};
            }
        };

        assertThatThrownBy(() -> ComparisonReports.generator(plugin, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("report format plain does not support comparison");
    }

    @Test
    void createsTheGeneratorOfAPluginThatSupportsComparison() {
        ReportComparisonProvider provider = config -> (model, out) -> {};
        var plugin = new ReportPlugin() {
            @Override
            public String name() {
                return "comparing";
            }

            @Override
            public ReportGeneratorProvider reportGeneratorProvider() {
                return config -> (model, out) -> {};
            }

            @Override
            public Optional<ReportComparisonProvider> reportComparisonProvider() {
                return Optional.of(provider);
            }
        };

        assertThat(ComparisonReports.generator(plugin, Map.of())).isNotNull();
    }
}
