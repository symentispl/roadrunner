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

import io.roadrunner.api.parameters.ParameterSourceProvider;
import java.nio.file.Path;
import java.util.Map;

/**
 * {@link ParameterSourceProvider} for CSV files.
 * <p>
 * Configuration keys:
 * <ul>
 *   <li>{@code file} — path to the CSV file (required)</li>
 *   <li>{@code separator} — single-character column separator (default: {@code ,})</li>
 * </ul>
 * <p>
 * CLI example: {@code --parameters-source csv:file=data.csv,separator=;}
 */
public final class CsvParameterSourceProvider implements ParameterSourceProvider {

    @Override
    public String name() {
        return "csv";
    }

    @Override
    public CsvParameterSource create(Map<String, String> configuration) {
        var filePath = configuration.get("file");
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(
                    "CSV parameter source requires 'file' configuration key, e.g. csv:file=data.csv");
        }
        var separatorStr = configuration.getOrDefault("separator", ",");
        if (separatorStr.length() != 1) {
            throw new IllegalArgumentException(
                    "CSV separator must be a single character, got: '%s'".formatted(separatorStr));
        }
        return new CsvParameterSource(Path.of(filePath), separatorStr.charAt(0));
    }
}
