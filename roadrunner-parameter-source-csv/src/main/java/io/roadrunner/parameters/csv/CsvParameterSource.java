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
package io.roadrunner.parameters.csv;

import io.roadrunner.api.parameters.ParameterFeed;
import io.roadrunner.api.parameters.ParameterSource;
import io.roadrunner.api.parameters.SamplerParameters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ParameterSource} that reads parameters from a CSV file.
 * <p>
 * The first row is treated as the header (parameter names). All subsequent rows
 * are data rows.
 * <p>
 * {@link #load()} opens the file and parses the header. Data rows are read lazily
 * as the returned {@link ParameterFeed} is iterated. The engine drains the feed
 * once before the benchmark loop starts and then closes it, so all I/O happens
 * outside the hot path.
 */
public final class CsvParameterSource implements ParameterSource {

    private final Path csvFile;
    private final char separator;

    public CsvParameterSource(Path csvFile, char separator) {
        this.csvFile = csvFile;
        this.separator = separator;
    }

    @Override
    public ParameterFeed load() throws IOException {
        var format = CSVFormat.DEFAULT
                .builder()
                .setDelimiter(separator)
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();
        return CsvParameterFeed.of(csvFile, format);
    }

    private static class CsvParameterFeed implements ParameterFeed {

        private static final Logger LOG = LoggerFactory.getLogger(CsvParameterFeed.class);

        static ParameterFeed of(Path csvFile, CSVFormat format) throws IOException {
            LOG.info("Loading CSV parameters from file {}", csvFile);
            var reader = Files.newBufferedReader(csvFile);
            try {
                return new CsvParameterFeed(format.parse(reader));
            } catch (Exception e) {
                reader.close();
                throw e;
            }
        }

        private final CSVParser csvParser;

        public CsvParameterFeed(CSVParser csvParser) {
            this.csvParser = csvParser;
        }

        @Override
        public Iterator<SamplerParameters> iterator() {
            return csvParser.stream()
                    .map(record -> SamplerParameters.of(new LinkedHashMap<>(record.toMap())))
                    .iterator();
        }

        @Override
        public void close() throws Exception {
            csvParser.close();
        }
    }
}
