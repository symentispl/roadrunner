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
    void shouldLoadParametersFromCsv() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, "name,value\nalice,1\nbob,2\n");

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
    void shouldLoadParametersWithCustomSeparator() throws Exception {
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
    void shouldThrowWhenFileNotFound() {
        var source = new CsvParameterSource(tempDir.resolve("nonexistent.csv"), ',');
        assertThatThrownBy(source::load).isInstanceOf(IOException.class);
    }

    @Test
    void providerShouldThrowWhenFileKeyMissing() {
        var provider = new CsvParameterSourceProvider();
        assertThatThrownBy(() -> provider.create(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file");
    }

    @Test
    void providerShouldThrowWhenSeparatorIsMultiChar() {
        var provider = new CsvParameterSourceProvider();
        assertThatThrownBy(() -> provider.create(Map.of("file", "data.csv", "separator", ",,")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separator");
    }

    @Test
    void providerShouldUseDefaultCommaSeparator() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, "key\nval\n");

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
    void shouldYieldNoRowsForHeaderOnlyFile() throws Exception {
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
    void shouldPreserveEmptyValues() throws Exception {
        var csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, "name,value\nalice,\n,2\n");

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
}
