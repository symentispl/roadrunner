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

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.io.StringSubstitutorReader;

public class ChartGenerator {

    public ChartGenerator() {}

    public void generateChart(Path outputPath, Path responsesCsv) throws IOException {
        if (Files.exists(outputPath) || Files.isRegularFile(outputPath)) {
            throw new FileAlreadyExistsException("");
        }

        var indexHtml = Files.createDirectory(outputPath).resolve("index.html");
        var datapointsJs = outputPath.resolve("data.js");

        try (Reader in = Files.newBufferedReader(responsesCsv);
                PrintStream out = new PrintStream(datapointsJs.toFile())) {
            CSVParser records = CSVFormat.DEFAULT.parse(in);
            out.println("const datapoints = [");
            for (CSVRecord record : records) {
                out.println("\t{x : %d,y : %d},"
                        .formatted(
                                Long.parseLong(record.get(0)),
                                (Long.parseLong(record.get(1)) - Long.parseLong(record.get(0)))));
            }
            out.println("];");
        }

        try (var reader = new StringSubstitutorReader(
                        new InputStreamReader(ChartGenerator.class.getResourceAsStream("/templates/index.html.tmpl")),
                        StringSubstitutor.createInterpolator());
                var writer = new FileWriter(indexHtml.toFile())) {
            IOUtils.copy(reader, writer);
        }
    }
}
