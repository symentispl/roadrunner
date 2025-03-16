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

import io.roadrunner.api.measurments.Measurement;
import io.roadrunner.api.measurments.MeasurementsReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import org.apache.commons.csv.CSVFormat;

public class CsvOutputMeasurementsReader implements MeasurementsReader {
    private final Path csvOutputFile;

    public CsvOutputMeasurementsReader(Path csvOutputFile) {
        this.csvOutputFile = csvOutputFile;
    }

    @Override
    public Iterator<Measurement> iterator() {
        try {
            var in = Files.newBufferedReader(csvOutputFile);
            return CSVFormat.DEFAULT.parse(in).stream()
                    .map(row -> new Measurement(
                            parseLong(row.get(0)),
                            parseLong(row.get(1)),
                            parseLong(row.get(2)),
                            parseLong(row.get(3)),
                            Measurement.Status.valueOf(row.get(4))))
                    .iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
