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

import static java.util.stream.Collectors.toMap;

import io.roadrunner.api.parameters.ParameterFeed;
import io.roadrunner.api.parameters.ParameterSource;
import io.roadrunner.api.parameters.SamplerParameters;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Function;
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

            // first read header

            var reader = Files.newBufferedReader(csvFile);
            try {
                CSVParser csvRecords = format.parse(reader);
                List<String> names = csvRecords.getHeaderNames();
                SequencedMap<String, Function<String, Object>> columns = names.stream()
                        .map(columnName -> {
                            String[] strings = columnName.split(":");
                            var name = strings[0].strip();
                            if (strings.length == 1) {
                                return Map.<String, Function<String, Object>>entry(name, s -> s);
                            } else if (strings.length == 2) {
                                var type = typeOf(strings[1].strip());
                                return Map.entry(name, type);
                            } else {
                                throw new IllegalArgumentException(
                                        "Invalid column header '%s', expected 'name' or 'name:type'"
                                                .formatted(columnName));
                            }
                        })
                        .collect(toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (x, y) -> {
                                    throw new IllegalArgumentException("Duplicate column names are not allowed");
                                },
                                LinkedHashMap::new));
                return new CsvParameterFeed(csvRecords, columns);
            } catch (Exception e) {
                reader.close();
                throw e;
            }
        }

        private static Function<String, Object> typeOf(String type) {
            return switch (type) {
                case "file" -> File::new;
                case "int" -> Integer::valueOf;
                case "long" -> Long::valueOf;
                case "short" -> Short::valueOf;
                case "float" -> Float::valueOf;
                case "double" -> Double::valueOf;
                case "boolean" -> CsvParameterFeed::parseStrictBoolean;
                default ->
                    throw new IllegalArgumentException(
                            "Unknown column type '%s', expected one of: file, int, long, short, float, double, boolean"
                                    .formatted(type));
            };
        }

        private static Boolean parseStrictBoolean(String value) {
            if (value.equalsIgnoreCase("true")) {
                return Boolean.TRUE;
            }
            if (value.equalsIgnoreCase("false")) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException(
                    "Not a boolean value: '%s', expected 'true' or 'false'".formatted(value));
        }

        private final CSVParser csvParser;
        private final SequencedMap<String, Function<String, Object>> columns;

        public CsvParameterFeed(CSVParser csvParser, SequencedMap<String, Function<String, Object>> columns) {
            this.csvParser = csvParser;
            this.columns = columns;
        }

        @Override
        public Iterator<SamplerParameters> iterator() {
            // NOTE: this is looking hacky it assumes both
            // parameters and columns have exactly the same
            // order of columns
            var names = List.copyOf(columns.keySet());
            return csvParser.stream()
                    .map(record -> {
                        SequencedMap<String, String> row = new LinkedHashMap<>();
                        int i = 0;
                        for (var value : record) {
                            var name = names.get(i);
                            validate(record.getRecordNumber(), name, value);
                            row.put(name, value);
                            i++;
                        }
                        return SamplerParameters.of(row, columns);
                    })
                    .iterator();
        }

        // Converts each cell up front so a bad value fails the whole load with row/column context,
        // rather than surfacing deep in a benchmark run the first time that row happens to be read.
        // TypedSamplerParameters still converts lazily per access; this is a validating dry run.
        private void validate(long rowNumber, String name, String value) {
            if (value.isEmpty()) {
                return;
            }
            try {
                columns.get(name).apply(value);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "row %d, column '%s': cannot convert value '%s'".formatted(rowNumber, name, value), e);
            }
        }

        @Override
        public void close() throws Exception {
            csvParser.close();
        }
    }
}
