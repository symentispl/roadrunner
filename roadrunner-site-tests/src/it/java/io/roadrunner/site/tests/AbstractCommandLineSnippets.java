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
package io.roadrunner.site.tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.LINUX)
@ExtendWith(SnippetsInvocationContextProvider.class)
public abstract class AbstractCommandLineSnippets {

    private static Path roadrunnerBin;

    @BeforeAll
    static void setUp() throws Exception {
        roadrunnerBin = Path.of("target/dependency/roadrunner-jars/bin/roadrunner");
        if (!Files.exists(roadrunnerBin)) {
            throw new FileNotFoundException("roadrunner bin doesn't exists at  %s".formatted(roadrunnerBin));
        }
        if (!Files.isExecutable(roadrunnerBin)) {
            throw new IOException("roadrunner bin is not executable at %s".formatted(roadrunnerBin));
        }
    }


    // every example lives in its own dir: <name>/snippet.sh, optional snippet.properties and extra files (csv, json...)
    @TestTemplate
    void runCommandLineSnippets(Path exampleDir, @TempDir Path tmp) throws Exception {
        copyDirectory(exampleDir, tmp);
        var script = tmp.resolve("snippet.sh");
        Files.writeString(script, fillPlaceholders(script));

        var processBuilder = new ProcessBuilder("bash", "-e", script.toString());
        var env = processBuilder.environment();
        env.put("PATH", roadrunnerBin.toAbsolutePath().getParent() + File.pathSeparator + env.getOrDefault("PATH", ""));
        var r = processBuilder.directory(tmp.toFile()).inheritIO().start();
        assertThat(r.waitFor(60, TimeUnit.SECONDS)).as("finished within timeout").isTrue();
        assertThat(r.exitValue()).isZero();
    }

    /**
     * Replaces every {@code <key>} with its value from snippet.properties and {@link #placeholders()}.
     */
    private static void copyDirectory(Path from, Path to) throws IOException {
        try (var files = Files.walk(from)) {
            for (var source : (Iterable<Path>) files::iterator) {
                var target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Placeholder values provided by the running system under test (container host, mapped ports...); win over snippet.properties. */
    protected Map<String, String> placeholders() {
        return Map.of();
    }

    private String fillPlaceholders(Path script) throws IOException {
        var text = Files.readString(script);
        var propertiesFile = Path.of(script.toString().replaceFirst("\\.sh$", ".properties"));
        var properties = new Properties();
        if (Files.exists(propertiesFile)) {
            try (var in = Files.newBufferedReader(propertiesFile)) {
                properties.load(in);
            }
        }
        var values = new HashMap<String, String>();
        properties.stringPropertyNames().forEach(key -> values.put(key, properties.getProperty(key)));
        values.putAll(placeholders());
        // values may refer to other placeholders, so substitute until nothing changes
        String previous;
        do {
            previous = text;
            for (var entry : values.entrySet()) {
                text = text.replace("<" + entry.getKey() + ">", entry.getValue());
            }
        } while (!text.equals(previous));
        return text;
    }

}
