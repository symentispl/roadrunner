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
package io.roadrunner.charts;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.output.csv.CsvOutputMeasurementsReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChartGeneratorTest {

    @Test
    void generatePerformanceChartHtml(@TempDir Path tempDir) throws Exception {
        var chartDir = Files.createDirectory(tempDir.resolve("report"));
        var chartGenerator = new ChartGenerator();
        chartGenerator.generateChart(
                chartDir, new CsvOutputMeasurementsReader(Paths.get("src/test/resources/output.csv")));
        assertThat(chartDir.resolve("index.html")).isNotEmptyFile();
        assertThat(chartDir.resolve("data.js")).isNotEmptyFile();
    }
}
