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
package io.roadrunner.output.csv;

import static java.lang.Long.parseLong;

import io.roadrunner.api.measurments.Sample;
import io.roadrunner.api.measurments.SamplesReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import org.apache.commons.csv.CSVFormat;

public class CsvOutputSamplesReader implements SamplesReader {
    private final Path csvOutputFile;

    public CsvOutputSamplesReader(Path csvOutputFile) {
        this.csvOutputFile = csvOutputFile;
    }

    @Override
    public Iterator<Sample> iterator() {
        try {
            var in = Files.newBufferedReader(csvOutputFile);
            return CSVFormat.DEFAULT.parse(in).stream()
                    .filter(row -> "REQ".equals(row.get(0)))
                    .map(row -> new Sample(
                            parseLong(row.get(1)),
                            parseLong(row.get(2)),
                            parseLong(row.get(3)),
                            parseLong(row.get(4)),
                            Sample.Status.valueOf(row.get(5))))
                    .iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
