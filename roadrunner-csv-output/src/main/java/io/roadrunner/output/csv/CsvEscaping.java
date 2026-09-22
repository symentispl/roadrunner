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

/**
 * RFC 4180 quoting compatible with {@link org.apache.commons.csv.CSVFormat#DEFAULT}, which
 * {@link CsvOutputEventReader} uses to parse both header and data rows. Shared by every place
 * {@link CsvOutputEventListener} writes a field, so the writer and reader can't drift apart.
 * <p>
 * Hand-rolled instead of {@code org.apache.commons.text.StringEscapeUtils.escapeCsv} /
 * {@code org.apache.commons.lang3.StringEscapeUtils.escapeCsv} on purpose: both always allocate a new
 * {@code String} (via an internal {@code StringWriter}), even when the value needs no escaping at all.
 * {@code CsvOutputEventListener} writes one row per event onto a reused {@code StringBuilder} to stay
 * allocation-conscious on that hot path; this fast-paths the common case (no comma/quote/CR/LF) with a
 * direct {@code append} and zero extra allocation.
 */
final class CsvEscaping {

    private CsvEscaping() {}

    static void appendEscaped(StringBuilder target, String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            target.append(value);
            return;
        }
        target.append('"').append(value.replace("\"", "\"\"")).append('"');
    }
}
