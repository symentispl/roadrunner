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

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.output.csv.CsvOutputEventReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.tuple.Pair;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HtmlReportGeneratorTest {

    @Test
    void generatePerformanceChartHtml(@TempDir Path tempDir) throws Exception {
        // given
        var chartDir = Files.createDirectory(tempDir.resolve("report"));
        var properties = new HashMap<String, String>();
        properties.put("outputPath", chartDir.toString());
        var chartGenerator = new HtmlReportGenerator(properties);
        var eventReader = new CsvOutputEventReader(Paths.get("src/test/resources/output.csv"));
        // when
        chartGenerator.generateChart(eventReader);
        // then
        assertThat(chartDir.resolve("index.html")).isNotEmptyFile();

        var dataJs = chartDir.resolve("data.js");
        assertThat(dataJs).isNotEmptyFile();
        var jsDatapoints = evalJsToDatapoints(dataJs);
        var cvsDatapoints = getDatapoints(eventReader);
        assertThat(jsDatapoints).isEqualTo(cvsDatapoints);

        assertThat(chartDir.resolve("users.js")).isNotEmptyFile();

    }

    private static List<Pair<Long, Long>> getDatapoints(CsvOutputEventReader eventReader) {
        return StreamSupport.stream(eventReader.spliterator(), false)
                .filter(ProtocolResponse.Response.class::isInstance)
                .map(ProtocolResponse.Response.class::cast)
                .map(r -> Pair.of(r.timestamp(), r.latency())).toList();
    }

    private static List<Pair<Long, Long>> evalJsToDatapoints(Path dataJs) throws IOException {
        try (Context context = Context.create()) {
            var points = new ArrayList<Pair<Long, Long>>();
            var source = " () => {\n" + Files.readString(dataJs) + "\nreturn datapoints;\n}";
            var value = context.eval("js", source);
            if (value.canExecute()) {
                var execute = value.execute();
                var iterator = execute.getIterator();
                while (iterator.hasIteratorNextElement()) {
                    var nextElement = iterator.getIteratorNextElement();
                    points.add(Pair.of(nextElement.getMember("x").asLong(), nextElement.getMember("y").asLong()));
                }
            }
            return points;
        }
    }
}
