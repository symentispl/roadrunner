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
package io.roadrunner.cli;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Preferences {

    private static final String PLUGINS_DIR = "plugins/protocols";

    private final Path baseDir;

    public Preferences(Path baseDir) throws IOException {
        this.baseDir = requireNonNull(baseDir).resolve(".roadrunner");
        if (Files.notExists(this.baseDir)) {
            Files.createDirectories(this.baseDir);
        }
    }

    public Path pluginsDir() {
        return baseDir.resolve(PLUGINS_DIR);
    }
}
