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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roadrunner.api.parameters.SamplerParameters;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvParameterSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void loadParametersFromCsv() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, """
                name,value
                alice,1
                bob,2
                """);

        var source = new CsvParameterSource(csvFile, ',');
        try (var feed = source.load()) {
            List<SamplerParameters> rows = new ArrayList<>();
            feed.forEach(rows::add);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).valueOf("name")).isEqualTo("alice");
            assertThat(rows.get(0).valueOf("value")).isEqualTo("1");
            assertThat(rows.get(1).valueOf("name")).isEqualTo("bob");
            assertThat(rows.get(1).valueOf("value")).isEqualTo("2");
        }
    }

    @Test
    void loadParametersWithCustomSeparator() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, "name;value\nalice;1\nbob;2\n");

        var source = new CsvParameterSource(csvFile, ';');
        try (var feed = source.load()) {
            List<SamplerParameters> rows = new ArrayList<>();
            feed.forEach(rows::add);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).valueOf("name")).isEqualTo("alice");
            assertThat(rows.get(1).valueOf("name")).isEqualTo("bob");
        }
    }

    @Test
    void throwWhenFileNotFound() {
        var source = new CsvParameterSource(tempDir.resolve("nonexistent.csv"), ',');
        assertThatThrownBy(source::load).isInstanceOf(IOException.class);
    }

    @Test
    void providerThrowsWhenFileKeyMissing() {
        var provider = new CsvParameterSourceProvider();
        assertThatThrownBy(() -> provider.create(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file");
    }

    @Test
    void providerThrowsWhenSeparatorIsMultiChar() {
        var provider = new CsvParameterSourceProvider();
        assertThatThrownBy(() -> provider.create(Map.of("file", "data.csv", "separator", ",,")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separator");
    }

    @Test
    void providerUsesDefaultCommaSeparator() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, """
                key
                val
                """);

        var provider = new CsvParameterSourceProvider();
        var source = provider.create(Map.of("file", csvFile.toString()));
        try (var feed = source.load()) {
            List<SamplerParameters> rows = new ArrayList<>();
            feed.forEach(rows::add);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).valueOf("key")).isEqualTo("val");
        }
    }

    @Test
    void yieldNoRowsForHeaderOnlyFile() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, "name,value\n");

        var source = new CsvParameterSource(csvFile, ',');
        try (var feed = source.load()) {
            List<SamplerParameters> rows = new ArrayList<>();
            feed.forEach(rows::add);
            assertThat(rows).isEmpty();
        }
    }

    @Test
    void preserveValueType() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, """
                name,value:int,report:file
                alice,1,report1.txt
                bob,2,report2.txt
                """);

        var source = new CsvParameterSource(csvFile, ',');
        try (var feed = source.load()) {
            List<SamplerParameters> rows = new ArrayList<>();
            feed.forEach(rows::add);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).satisfies(row -> {
                assertThat(row.valueOf("name")).isEqualTo("alice");
                assertThat(row.valueOf("value")).isEqualTo(1);
                assertThat(row.valueOf("report")).isEqualTo(new File("report1.txt"));
            });

            assertThat(rows.get(1)).satisfies(row -> {
                assertThat(row.valueOf("name")).isEqualTo("bob");
                assertThat(row.valueOf("value")).isEqualTo(2);
                assertThat(row.valueOf("report")).isEqualTo(new File("report2.txt"));
            });
        }
    }

    private record Bound(int index, Class<?> type, Object value) {}

    @Test
    void preserveEmptyValues() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, """
                name,value
                alice,
                ,2
                """);

        var source = new CsvParameterSource(csvFile, ',');
        try (var feed = source.load()) {
            List<SamplerParameters> rows = new ArrayList<>();
            feed.forEach(rows::add);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).valueOf("name")).isEqualTo("alice");
            assertThat(rows.get(0).valueOf("value")).isEqualTo("");
            assertThat(rows.get(1).valueOf("name")).isEqualTo("");
            assertThat(rows.get(1).valueOf("value")).isEqualTo("2");
        }
    }

    @Test
    void bindTypedValuesByColumnOrderRegardlessOfHeaderOrder() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, """
                report:file,name,value:int
                report1.txt,alice,1
                """);

        var source = new CsvParameterSource(csvFile, ',');
        try (var feed = source.load()) {
            List<SamplerParameters> rows = new ArrayList<>();
            feed.forEach(rows::add);

            assertThat(rows).hasSize(1);
            List<Bound> bound = new ArrayList<>();
            rows.get(0).forEach((index, type, value) -> bound.add(new Bound(index, type, value)));

            assertThat(bound)
                    .containsExactly(
                            new Bound(0, File.class, new File("report1.txt")),
                            new Bound(1, String.class, "alice"),
                            new Bound(2, Integer.class, 1));
        }
    }

    @Test
    void preserveBlankValueForTypedColumn() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, """
                name,value:int
                alice,
                """);

        var source = new CsvParameterSource(csvFile, ',');
        try (var feed = source.load()) {
            List<SamplerParameters> rows = new ArrayList<>();
            feed.forEach(rows::add);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).valueOf("value")).isEqualTo("");
        }
    }

    @Test
    void throwsForUnknownColumnType() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, """
                value:notatype
                1
                """);

        var source = new CsvParameterSource(csvFile, ',');
        assertThatThrownBy(source::load)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("notatype");
    }

    @Test
    void throwsForDuplicateColumnNames() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, """
                value:int,value:long
                1,2
                """);

        var source = new CsvParameterSource(csvFile, ',');
        assertThatThrownBy(source::load).isInstanceOf(IllegalArgumentException.class);
    }
}
