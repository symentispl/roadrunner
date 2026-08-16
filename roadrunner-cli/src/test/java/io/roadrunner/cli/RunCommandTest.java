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

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.latency.recording.PauseDetectorKind;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

class RunCommandTest {

    private static final Instant STARTED_AT = Instant.parse("2026-07-31T13:44:22Z");

    static class DummySamplerOptions {
        @Parameters(paramLabel = "expression")
        String expression = "GET(\"http://localhost/\")";

        @Option(names = "--timeout")
        long timeout = 5000;
    }

    private static CommandSpec samplerCommandSpec(String name) {
        return CommandSpec.forAnnotatedObject(new DummySamplerOptions()).name(name);
    }

    @Test
    void closedWorldManifestBlanksTheOpenWorldFields() {
        var runCommand = new RunCommand();
        runCommand.loadModel = new RunCommand.LoadModelArgs();
        runCommand.loadModel.closedWorld = new RunCommand.ClosedWorldArgs();
        runCommand.loadModel.closedWorld.concurrency = 10;
        runCommand.loadModel.closedWorld.numberOfRequests = 2000;

        var manifest = runCommand.buildManifest(samplerCommandSpec("http"), STARTED_AT, "0.3.1-test");

        assertThat(manifest.sampler()).isEqualTo("http");
        assertThat(manifest.loadModel()).isEqualTo("closed");
        assertThat(manifest.loadConcurrency()).isEqualTo("10");
        assertThat(manifest.loadRequests()).isEqualTo("2000");
        assertThat(manifest.loadRate()).isEmpty();
        assertThat(manifest.loadDuration()).isEmpty();
        assertThat(manifest.pauseDetectors()).isEmpty();
        assertThat(manifest.parametersSource()).isEmpty();
        assertThat(manifest.startedAt()).isEqualTo(STARTED_AT.toString());
        assertThat(manifest.roadrunnerVersion()).isEqualTo("0.3.1-test");
    }

    @Test
    void openWorldManifestCarriesPauseDetectorsAndParametersSource() throws IOException {
        var runCommand = new RunCommand();
        runCommand.loadModel = new RunCommand.LoadModelArgs();
        runCommand.loadModel.openWorld = new RunCommand.OpenWorldArgs();
        runCommand.loadModel.openWorld.rate = 50;
        runCommand.loadModel.openWorld.duration = Duration.ofSeconds(30);
        runCommand.pauseDetectors = EnumSet.of(PauseDetectorKind.VT_SCHEDULING, PauseDetectorKind.JVM_PAUSE);
        runCommand.parametersSource = new PrefixedMap("csv", Map.of("file", "ids.csv"));

        var manifest = runCommand.buildManifest(samplerCommandSpec("http"), STARTED_AT, "0.3.1-test");

        assertThat(manifest.loadModel()).isEqualTo("open");
        assertThat(manifest.loadConcurrency()).isEmpty();
        assertThat(manifest.loadRequests()).isEmpty();
        assertThat(manifest.loadRate()).isEqualTo("50");
        assertThat(manifest.loadDuration()).isEqualTo("PT30S");
        assertThat(manifest.pauseDetectors()).isEqualTo("vt,jvm");

        var parsedParametersSource = PrefixedMap.parse(manifest.parametersSource());
        assertThat(parsedParametersSource.prefix()).isEqualTo("csv");
        assertThat(parsedParametersSource.parameters()).containsExactlyInAnyOrderEntriesOf(Map.of("file", "ids.csv"));
    }

    @Test
    void manifestWithoutAParametersSourceLeavesItBlank() {
        var runCommand = new RunCommand();
        runCommand.loadModel = new RunCommand.LoadModelArgs();
        runCommand.loadModel.closedWorld = new RunCommand.ClosedWorldArgs();
        runCommand.loadModel.closedWorld.concurrency = 1;
        runCommand.loadModel.closedWorld.numberOfRequests = 1;

        var manifest = runCommand.buildManifest(samplerCommandSpec("http"), STARTED_AT, "0.3.1-test");

        assertThat(manifest.parametersSource()).isEmpty();
    }

    @Test
    void samplerOptionsAreSerializedGenericallyFromTheCommandSpec() throws IOException {
        var runCommand = new RunCommand();
        runCommand.loadModel = new RunCommand.LoadModelArgs();
        runCommand.loadModel.closedWorld = new RunCommand.ClosedWorldArgs();
        runCommand.loadModel.closedWorld.concurrency = 1;
        runCommand.loadModel.closedWorld.numberOfRequests = 1;

        var manifest = runCommand.buildManifest(samplerCommandSpec("http"), STARTED_AT, "0.3.1-test");

        var parsed = PrefixedMap.parse(manifest.samplerOptions());
        assertThat(parsed.prefix()).isEqualTo("http");
        assertThat(parsed.parameters())
                .containsEntry("expression", "GET(\"http://localhost/\")")
                .containsEntry("--timeout", "5000");
    }

    @Test
    void manifestCarriesJvmOsAndHardwareFacts() {
        var runCommand = new RunCommand();
        runCommand.loadModel = new RunCommand.LoadModelArgs();
        runCommand.loadModel.closedWorld = new RunCommand.ClosedWorldArgs();
        runCommand.loadModel.closedWorld.concurrency = 1;
        runCommand.loadModel.closedWorld.numberOfRequests = 1;

        var manifest = runCommand.buildManifest(samplerCommandSpec("http"), STARTED_AT, "0.3.1-test");

        assertThat(manifest.jvmVersion()).isEqualTo(Runtime.version().toString());
        assertThat(manifest.osName()).isEqualTo(System.getProperty("os.name"));
        assertThat(manifest.osVersion()).isEqualTo(System.getProperty("os.version"));
        assertThat(manifest.osArch()).isEqualTo(System.getProperty("os.arch"));
        assertThat(manifest.hardwareCpus())
                .isEqualTo(String.valueOf(Runtime.getRuntime().availableProcessors()));
        assertThat(manifest.hardwareMemory()).isNotBlank();
    }
}
