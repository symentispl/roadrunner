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
package io.roadrunner.reports;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * What a run was: sampler, load model, environment. Written by the CLI at the start of a run and
 * read back here so a report can label itself and a comparison can tell whether two runs are even
 * comparable.
 *
 * <p>Backed by plain {@link Properties} on purpose: flat string fields don't need a JSON
 * dependency, and reading is free. The sampler's own options are a dynamic set of keys, so they're
 * flattened into individual {@code sampler.options.<name>} properties rather than encoded into one
 * string — a future comparison reads them back as a map directly, no parsing required.
 */
public record RunManifest(
        String roadrunnerVersion,
        String startedAt,
        String sampler,
        Map<String, String> samplerOptions,
        String loadModel,
        String loadConcurrency,
        String loadRequests,
        String loadRate,
        String loadDuration,
        String pauseDetectors,
        String parametersSource,
        String jvmVersion,
        String jvmArgs,
        String osName,
        String osVersion,
        String osArch,
        String hardwareCpus,
        String hardwareMemory) {

    static final String FILE_NAME = "run.properties";

    private static final String UNKNOWN_VALUE = "unknown";

    private static final String SAMPLER_OPTIONS_PREFIX = "sampler.options.";

    public RunManifest {
        samplerOptions = Map.copyOf(samplerOptions);
    }

    private enum Key {
        ROADRUNNER_VERSION("roadrunner.version"),
        STARTED_AT("started.at"),
        SAMPLER("sampler"),
        LOAD_MODEL("load.model"),
        LOAD_CONCURRENCY("load.concurrency"),
        LOAD_REQUESTS("load.requests"),
        LOAD_RATE("load.rate"),
        LOAD_DURATION("load.duration"),
        PAUSE_DETECTORS("pause.detectors"),
        PARAMETERS_SOURCE("parameters.source"),
        JVM_VERSION("jvm.version"),
        JVM_ARGS("jvm.args"),
        OS_NAME("os.name"),
        OS_VERSION("os.version"),
        OS_ARCH("os.arch"),
        HARDWARE_CPUS("hardware.cpus"),
        HARDWARE_MEMORY("hardware.memory");

        private final String propertyName;

        Key(String propertyName) {
            this.propertyName = propertyName;
        }
    }

    public void writeTo(Path dir) throws IOException {
        Files.createDirectories(dir);
        var properties = new Properties();
        properties.setProperty(Key.ROADRUNNER_VERSION.propertyName, roadrunnerVersion);
        properties.setProperty(Key.STARTED_AT.propertyName, startedAt);
        properties.setProperty(Key.SAMPLER.propertyName, sampler);
        samplerOptions.forEach((key, value) -> properties.setProperty(SAMPLER_OPTIONS_PREFIX + key, value));
        properties.setProperty(Key.LOAD_MODEL.propertyName, loadModel);
        properties.setProperty(Key.LOAD_CONCURRENCY.propertyName, loadConcurrency);
        properties.setProperty(Key.LOAD_REQUESTS.propertyName, loadRequests);
        properties.setProperty(Key.LOAD_RATE.propertyName, loadRate);
        properties.setProperty(Key.LOAD_DURATION.propertyName, loadDuration);
        properties.setProperty(Key.PAUSE_DETECTORS.propertyName, pauseDetectors);
        properties.setProperty(Key.PARAMETERS_SOURCE.propertyName, parametersSource);
        properties.setProperty(Key.JVM_VERSION.propertyName, jvmVersion);
        properties.setProperty(Key.JVM_ARGS.propertyName, jvmArgs);
        properties.setProperty(Key.OS_NAME.propertyName, osName);
        properties.setProperty(Key.OS_VERSION.propertyName, osVersion);
        properties.setProperty(Key.OS_ARCH.propertyName, osArch);
        properties.setProperty(Key.HARDWARE_CPUS.propertyName, hardwareCpus);
        properties.setProperty(Key.HARDWARE_MEMORY.propertyName, hardwareMemory);
        try (var out = Files.newOutputStream(dir.resolve(FILE_NAME))) {
            properties.store(out, null);
        }
    }

    public static RunManifest readFrom(Path dir) throws IOException {
        var file = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            throw new FileNotFoundException("run manifest %s file not found".formatted(file));
        }
        var properties = new Properties();
        try (var in = Files.newInputStream(file)) {
            properties.load(in);
        }
        var samplerOptions = new TreeMap<String, String>();
        for (var name : properties.stringPropertyNames()) {
            if (name.startsWith(SAMPLER_OPTIONS_PREFIX)) {
                samplerOptions.put(name.substring(SAMPLER_OPTIONS_PREFIX.length()), properties.getProperty(name));
            }
        }
        return new RunManifest(
                properties.getProperty(Key.ROADRUNNER_VERSION.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.STARTED_AT.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.SAMPLER.propertyName, UNKNOWN_VALUE),
                samplerOptions,
                properties.getProperty(Key.LOAD_MODEL.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.LOAD_CONCURRENCY.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.LOAD_REQUESTS.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.LOAD_RATE.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.LOAD_DURATION.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.PAUSE_DETECTORS.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.PARAMETERS_SOURCE.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.JVM_VERSION.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.JVM_ARGS.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.OS_NAME.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.OS_VERSION.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.OS_ARCH.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.HARDWARE_CPUS.propertyName, UNKNOWN_VALUE),
                properties.getProperty(Key.HARDWARE_MEMORY.propertyName, UNKNOWN_VALUE));
    }
}
