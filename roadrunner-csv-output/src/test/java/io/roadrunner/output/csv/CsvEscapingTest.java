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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvEscapingTest {

    @Test
    void leavesPlainValuesUnquoted() {
        var target = new StringBuilder();
        CsvEscaping.appendEscaped(target, "plain-value");
        assertThat(target.toString()).isEqualTo("plain-value");
    }

    @Test
    void quotesAndDoublesEmbeddedQuotes() {
        var target = new StringBuilder();
        CsvEscaping.appendEscaped(target, "a\"b");
        assertThat(target.toString()).isEqualTo("\"a\"\"b\"");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"plain", "with,comma", "with\"quote", "with\nnewline", "with\rcarriage", "a,b\"c\nd\re", ""})
    void survivesRoundTripThroughCsvFormatDefault(String value) throws Exception {
        var row = new StringBuilder("prefix,");
        CsvEscaping.appendEscaped(row, value);
        row.append(",suffix");

        var record = CSVFormat.DEFAULT
                .parse(new StringReader(row.toString()))
                .getRecords()
                .get(0);

        assertThat(record.get(0)).isEqualTo("prefix");
        assertThat(record.get(1)).isEqualTo(value);
        assertThat(record.get(2)).isEqualTo("suffix");
    }
}
