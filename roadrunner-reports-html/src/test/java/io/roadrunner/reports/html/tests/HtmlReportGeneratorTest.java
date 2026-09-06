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
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.html.HtmlReportGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportGeneratorTest {

    @Test
    void generateCreatesOutputDirectoryIfMissing(@TempDir Path tempDir) throws Exception {
        // given — outputDir does not exist yet; generate must create it
        var reportDir = tempDir.resolve("report");
        var properties = new HashMap<String, String>();
        properties.put("outputDir", reportDir.toString());
        var reportGenerator = new HtmlReportGenerator(properties);
        var model = modelFromFixture();
        // when
        reportGenerator.generate(model);
        // then
        assertThat(reportDir.resolve("index.html")).isNotEmptyFile();
        assertThat(reportDir.resolve("data.js")).isNotEmptyFile();
        assertThat(reportDir.resolve("users.js")).isNotEmptyFile();
    }

    @Test
    void generateWritesLatencyAndUserSeriesFromTheModel(@TempDir Path tempDir) throws Exception {
        // given
        var reportDir = Files.createDirectory(tempDir.resolve("report"));
        var properties = new HashMap<String, String>();
        properties.put("outputDir", reportDir.toString());
        var reportGenerator = new HtmlReportGenerator(properties);
        var model = modelFromFixture();
        // when
        reportGenerator.generate(model);
        // then
        assertThat(reportDir.resolve("index.html")).isNotEmptyFile();

        var dataJs = reportDir.resolve("data.js");
        assertThat(dataJs).isNotEmptyFile();
        var datapoints = evalDatapoints(dataJs);
        assertThat(datapoints.get("p50"))
                .isEqualTo(toList(model.latencyOverTime().p50()));
        assertThat(datapoints.get("p90"))
                .isEqualTo(toList(model.latencyOverTime().p90()));
        assertThat(datapoints.get("p99"))
                .isEqualTo(toList(model.latencyOverTime().p99()));

        var usersJs = reportDir.resolve("users.js");
        assertThat(usersJs).isNotEmptyFile();
        assertThat(evalSeries(usersJs, "users")).isEqualTo(toList(model.usersOverTime()));
    }

    private static ReportModel modelFromFixture() {
        return ReportModel.from(new CsvOutputEventReader(Paths.get("src/test/resources/output.csv")));
    }

    private static List<Long> toList(long[] values) {
        var list = new ArrayList<Long>(values.length);
        for (var value : values) {
            list.add(value);
        }
        return list;
    }

    private static Map<String, List<Long>> evalDatapoints(Path dataJs) throws IOException {
        try (var context = Context.create()) {
            var source = " () => {\n" + Files.readString(dataJs) + "\nreturn datapoints;\n}";
            var value = context.eval("js", source).execute();
            var result = new HashMap<String, List<Long>>();
            for (var key : List.of("p50", "p90", "p99")) {
                result.put(key, seriesYValues(value.getMember(key)));
            }
            return result;
        }
    }

    private static List<Long> evalSeries(Path js, String variableName) throws IOException {
        try (var context = Context.create()) {
            var source = " () => {\n" + Files.readString(js) + "\nreturn " + variableName + ";\n}";
            var value = context.eval("js", source).execute();
            return seriesYValues(value);
        }
    }

    private static List<Long> seriesYValues(org.graalvm.polyglot.Value series) {
        var values = new ArrayList<Long>();
        var iterator = series.getIterator();
        while (iterator.hasIteratorNextElement()) {
            values.add(iterator.getIteratorNextElement().getMember("y").asLong());
        }
        return values;
    }
}
