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
package io.roadrunner.reports.html.tests;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.output.csv.CsvOutputEventReader;
import io.roadrunner.reports.LatencyOverTime;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.html.HtmlReportGenerator;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportGeneratorTest {

    @Test
    void writesASingleIndexHtmlCreatingTheOutputDirectory(@TempDir Path tempDir) throws Exception {
        var reportDir = tempDir.resolve("report");
        var out = new StringWriter();

        new HtmlReportGenerator(Map.of("outputDir", reportDir.toString()))
                .generate(modelFromFixture(), new PrintWriter(out));

        assertThat(out.toString()).contains("Roadrunner HTML report at " + reportDir.resolve("index.html"));
        try (var files = Files.list(reportDir)) {
            assertThat(files).containsExactly(reportDir.resolve("index.html"));
        }
    }

    @Test
    void referencesNothingOutsideTheFile(@TempDir Path tempDir) throws Exception {
        var html = generate(tempDir, modelFromFixture());

        // Licence banners (ours and Chart.js's) legitimately mention URLs; they are comments, not references.
        var code = html.replaceAll("(?s)<!--.*?-->", "").replaceAll("(?s)/\\*.*?\\*/", "");
        assertThat(code).doesNotContain("http://").doesNotContain("https://");
        assertThat(html).doesNotContainPattern("<script[^>]*\\ssrc=").doesNotContainPattern("<link[^>]*\\shref=");
        assertThat(html).contains("Chart.js");
    }

    @Test
    void hasEveryPanelAndStatTile(@TempDir Path tempDir) throws Exception {
        var html = generate(tempDir, modelFromFixture());

        assertThat(html)
                .contains("id=\"percentiles\"", "id=\"latency\"", "id=\"throughput\"", "<h2>Status</h2>")
                .contains(">requests<", ">error rate<", ">throughput<", ">p50<", ">p99<", ">duration<");
        assertThat(Pattern.compile("class=\"tile\"").matcher(html).results().count())
                .isEqualTo(6);
    }

    @Test
    void embeddedSeriesMatchTheModel(@TempDir Path tempDir) throws Exception {
        var model = modelFromFixture();
        var html = generate(tempDir, model);

        var json = html.substring(html.indexOf("const data = ") + 13, html.indexOf(";\nconst css"));
        try (var context = Context.create()) {
            var data = context.eval("js", "(" + json + ")");
            var slices = model.throughputSeries().length;
            for (var key : List.of("seconds", "p50", "p90", "p99", "rate")) {
                assertThat(data.getMember(key).getArraySize()).as(key).isEqualTo(slices);
            }
            assertThat(data.getMember("percentiles").getArraySize()).isEqualTo(4);
            assertThat(data.getMember("p99").getArrayElement(slices - 1).asLong())
                    .isEqualTo(model.latencyOverTime().p99()[slices - 1]);
        }
    }

    @Test
    void sizeDoesNotDependOnTheNumberOfRequests(@TempDir Path tempDir) throws Exception {
        var small = generate(tempDir.resolve("small"), synthetic(2_000));
        var huge = generate(tempDir.resolve("huge"), synthetic(1_000_000));

        assertThat(huge.length()).isLessThan(small.length() + 2_000);
        assertThat(huge.length()).isLessThan(400_000);
    }

    private static String generate(Path dir, ReportModel model) throws Exception {
        new HtmlReportGenerator(Map.of("outputDir", dir.toString())).generate(model, new PrintWriter(new StringWriter()));
        return Files.readString(dir.resolve("index.html"));
    }

    private static ReportModel synthetic(long total) {
        var slices = 30;
        var flat = new long[slices];
        java.util.Arrays.fill(flat, total / slices);
        return new ReportModel(
                total, total, 0, 60, total / 60.0, 0, 1, 5, 900, 5, 20, 80, 400,
                new long[20], flat, flat, Map.of("200", total), new LatencyOverTime(flat, flat, flat));
    }

    private static ReportModel modelFromFixture() {
        return ReportModel.from(new CsvOutputEventReader(Paths.get("src/test/resources/output.csv")));
    }
}
