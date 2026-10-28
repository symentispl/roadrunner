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
package io.roadrunner.reports.json.tests;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.output.csv.CsvOutputEventReader;
import io.roadrunner.reports.LatencyOverTime;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.RunManifest;
import io.roadrunner.reports.json.JsonReportGenerator;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonReportGeneratorTest {

    @Test
    void writesASingleReportJsonCreatingTheOutputDirectory(@TempDir Path tempDir) throws Exception {
        var reportDir = tempDir.resolve("report");
        var out = new StringWriter();

        new JsonReportGenerator(Map.of("outputDir", reportDir.toString()))
                .generate(modelFromFixture(), new PrintWriter(out));

        assertThat(out.toString()).contains("Roadrunner JSON report at " + reportDir.resolve("report.json"));
        try (var files = Files.list(reportDir)) {
            assertThat(files).containsExactly(reportDir.resolve("report.json"));
        }
    }

    @Test
    void hasEverySectionAndSeriesMatchTheModel(@TempDir Path tempDir) throws Exception {
        var model = modelFromFixture();
        manifest("http", Map.of("--timeout", "5s"), "closed").writeTo(tempDir);

        parse(generate(tempDir, model), r -> {
            var slices = model.throughputSeries().length;
            assertThat(r.getMember("schemaVersion").asInt()).isEqualTo(1);
            assertThat(r.getMember("sampler").asString()).isEqualTo("http");
            assertThat(r.getMember("startedAt").asString()).isEqualTo("2026-10-28T10:00:00Z");
            assertThat(r.getMember("roadrunnerVersion").asString()).isEqualTo("1.2.3");
            var summary = r.getMember("summary");
            assertThat(summary.getMember("requests").asLong()).isEqualTo(model.total());
            assertThat(summary.getMember("p99Ms").asLong()).isEqualTo(model.p99());
            assertThat(summary.getMember("throughputPerSecond").asDouble()).isEqualTo(model.throughput());
            assertThat(summary.getMember("durationSeconds").asDouble()).isEqualTo(model.durationSeconds());
            assertThat(r.getMember("latencyPercentiles").getArraySize()).isEqualTo(4);
            var over = r.getMember("latencyOverTime");
            for (var key : List.of("seconds", "p50Ms", "p90Ms", "p99Ms")) {
                assertThat(over.getMember(key).getArraySize()).as(key).isEqualTo(slices);
            }
            assertThat(over.getMember("p99Ms").getArrayElement(slices - 1).asLong())
                    .isEqualTo(model.latencyOverTime().p99()[slices - 1]);
            var tp = r.getMember("throughputOverTime");
            assertThat(tp.getMember("seconds").getArraySize()).isEqualTo(slices);
            assertThat(tp.getMember("requestsPerSecond").getArraySize()).isEqualTo(slices);
            var sliceSeconds = tp.getMember("sliceSeconds").asDouble();
            assertThat(tp.getMember("requestsPerSecond").getArrayElement(0).asDouble() * sliceSeconds)
                    .isCloseTo(model.throughputSeries()[0], org.assertj.core.data.Offset.offset(0.01));
            assertThat(r.getMember("status").getMemberKeys()).containsExactlyInAnyOrderElementsOf(
                    model.statusBreakdown().keySet());
            var config = r.getMember("configuration");
            assertThat(config.getMember("sampler").getMember("options").getMember("--timeout").asString())
                    .isEqualTo("5s");
            assertThat(config.getMember("loadModel").getMember("concurrency").asInt()).isEqualTo(5);
            assertThat(config.getMember("loadModel").getMember("rate").isNull()).isTrue();
            assertThat(config.getMember("loadModel").getMember("pauseDetectors").getArraySize())
                    .isEqualTo(2);
            assertThat(config.getMember("environment").getMember("cpus").asInt()).isEqualTo(8);
            assertThat(config.getMember("environment").getMember("os").getMember("name").asString())
                    .isEqualTo("Linux");
        });
    }

    @Test
    void trickyRunDataIsEscaped(@TempDir Path tempDir) throws Exception {
        var tricky = "q\"uote \\ back ${x} \n tab\t \u0001   é中 😀 </script>";
        manifest(tricky, Map.of(tricky, tricky), "open").writeTo(tempDir);

        parse(generate(tempDir, synthetic(10, Map.of(tricky, 10L))), r -> {
            assertThat(r.getMember("sampler").asString()).isEqualTo(tricky);
            var options = r.getMember("configuration").getMember("sampler").getMember("options");
            assertThat(options.getMember(tricky).asString()).isEqualTo(tricky);
            assertThat(r.getMember("status").getMember(tricky).asLong()).isEqualTo(10);
        });
    }

    @Test
    void openWorldManifest(@TempDir Path tempDir) throws Exception {
        manifest("http", Map.of(), "open").writeTo(tempDir);

        parse(generate(tempDir, modelFromFixture()), r -> {
            var load = r.getMember("configuration").getMember("loadModel");
            assertThat(load.getMember("model").asString()).isEqualTo("open");
            assertThat(load.getMember("rate").asInt()).isEqualTo(50);
            assertThat(load.getMember("duration").asString()).isEqualTo("PT1M");
            assertThat(load.getMember("concurrency").isNull()).isTrue();
            assertThat(load.getMember("requests").isNull()).isTrue();
            assertThat(load.getMember("pauseDetectors").getArraySize()).isZero();
        });
    }

    @Test
    void withoutManifestHeaderAndConfigurationAreNull(@TempDir Path tempDir) throws Exception {
        parse(generate(tempDir, synthetic(10, Map.of())), r -> {
            assertThat(r.getMember("sampler").isNull()).isTrue();
            assertThat(r.getMember("startedAt").isNull()).isTrue();
            assertThat(r.getMember("roadrunnerVersion").isNull()).isTrue();
            assertThat(r.getMember("configuration").isNull()).isTrue();
            assertThat(r.getMember("summary").getMember("requests").asLong()).isEqualTo(10);
        });
    }

    @Test
    void emptyStatusIsAnEmptyObject(@TempDir Path tempDir) throws Exception {
        parse(generate(tempDir, synthetic(10, Map.of())), r -> assertThat(
                        r.getMember("status").getMemberKeys())
                .isEmpty());
    }

    @Test
    void emptyRunDoesNotProduceNonFiniteNumbers(@TempDir Path tempDir) throws Exception {
        var model = new ReportModel(
                0, 0, 0, 0, Double.NaN, Double.POSITIVE_INFINITY, 0, 0, 0, 0, 0, 0, 0,
                new long[20], new long[0], new long[0], Map.of(), new LatencyOverTime(new long[0], new long[0], new long[0]));
        var json = generate(tempDir, model);

        assertThat(json).doesNotContain("NaN", "Infinity");
        parse(json, r -> assertThat(r.getMember("summary").getMember("throughputPerSecond").isNull())
                .isTrue());
    }

    @Test
    void numbersAreLocaleIndependent(@TempDir Path tempDir) throws Exception {
        var previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ar-EG"));
        try {
            var json = generate(tempDir, synthetic(1_234, Map.of("200", 1_234L)));

            assertThat(json).contains("\"requests\":1234", "\"percentile\":99.9");
            assertThat(json.chars().filter(c -> Character.isDigit(c) && c > '9').count())
                    .isZero();
            parse(json, r -> assertThat(r.getMember("summary").getMember("requests").asLong())
                    .isEqualTo(1_234));
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static void parse(String json, java.util.function.Consumer<Value> check) {
        try (var context = Context.create()) {
            check.accept(context.eval("js", "JSON.parse(" + quote(json) + ")"));
        }
    }

    /** A JS string literal for arbitrary text, built without trusting the JSON under test. */
    private static String quote(String s) {
        var sb = new StringBuilder("'");
        for (var c : s.toCharArray()) {
            sb.append(String.format("\\u%04x", (int) c));
        }
        return sb.append("'").toString();
    }

    private static String generate(Path dir, ReportModel model) throws Exception {
        new JsonReportGenerator(Map.of("outputDir", dir.toString())).generate(model, new PrintWriter(new StringWriter()));
        return Files.readString(dir.resolve("report.json"));
    }

    private static RunManifest manifest(String sampler, Map<String, String> options, String loadModel) {
        var open = "open".equals(loadModel);
        return new RunManifest(
                "1.2.3", "2026-10-28T10:00:00Z", sampler, options, loadModel, open ? "" : "5", open ? "" : "100",
                open ? "50" : "", open ? "PT1M" : "", open ? "" : "vt,jvm", "", "27", "", "Linux", "6.17", "amd64", "8",
                "1024");
    }

    private static ReportModel synthetic(long total, Map<String, Long> status) {
        var slices = 30;
        var flat = new long[slices];
        java.util.Arrays.fill(flat, total / slices);
        return new ReportModel(
                total, total, 0, 60, total / 60.0, 0, 1, 5, 900, 5, 20, 80, 400,
                new long[20], flat, flat, status, new LatencyOverTime(flat, flat, flat));
    }

    private static ReportModel modelFromFixture() {
        return ReportModel.from(new CsvOutputEventReader(Paths.get("src/test/resources/output.csv")));
    }
}
