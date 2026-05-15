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
package io.roadrunner.app.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.LINUX)
public class RoadrunnerRunIT {

    private Path roadrunnerBin;

    @BeforeEach
    void ensureExecutable() throws Exception {
        // JReleaser archives use a versioned root directory (e.g. roadrunner-0.1.0/bin/roadrunner),
        // so we locate the binary by walking the unpacked directory tree.
        Path appDir = Path.of("target/roadrunner-app");
        try (var paths = Files.walk(appDir, 3)) {
            roadrunnerBin = paths.filter(p -> p.getFileName().toString().equals("roadrunner")
                            && Files.isRegularFile(p))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("roadrunner binary not found under " + appDir));
        }
        Files.setPosixFilePermissions(roadrunnerBin, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    @Test
    void closedWorldRunWithoutPauseDetectors(@TempDir Path outputDir) throws Exception {
        // Regression: passing no --pause-detectors used to NPE because the EnumSet field was null.
        var process = new ProcessBuilder(
                        ROADRUNNER_BIN.toString(),
                        "run",
                        "-n",
                        "10",
                        "-c",
                        "2",
                        "-s",
                        outputDir.toString(),
                        "vm",
                        "--sleep-time",
                        "1")
                .redirectErrorStream(true)
                .start();
        var stdout = new String(process.getInputStream().readAllBytes());
        var exitCode = process.waitFor();
        assertThat(exitCode).as("exit code; stdout was:%n%s", stdout).isEqualTo(0);
        assertThat(stdout).doesNotContain("NullPointerException");
        assertThat(outputDir.resolve("output.csv")).exists();
        assertThat(outputDir.resolve("latency.hgrm")).doesNotExist();
    }

    @Test
    void openWorldRunWithVtPauseDetector(@TempDir Path outputDir) throws Exception {
        var process = new ProcessBuilder(
                        roadrunnerBin.toString(),
                        "run",
                        "--rate",
                        "20",
                        "--duration",
                        "1s",
                        "--pause-detectors",
                        "vt",
                        "-s",
                        outputDir.toString(),
                        "vm",
                        "--sleep-time",
                        "1")
                .redirectErrorStream(true)
                .start();
        var stdout = new String(process.getInputStream().readAllBytes());
        var exitCode = process.waitFor();
        assertThat(exitCode).as("exit code; stdout was:%n%s", stdout).isEqualTo(0);
        assertThat(outputDir.resolve("output.csv")).exists();
        assertThat(outputDir.resolve("latency.hgrm")).exists();
    }

    @Test
    void closedWorldRejectsPauseDetectors(@TempDir Path outputDir) throws Exception {
        var process = new ProcessBuilder(
                        roadrunnerBin.toString(),
                        "run",
                        "-n",
                        "10",
                        "-c",
                        "2",
                        "--pause-detectors",
                        "vt",
                        "-s",
                        outputDir.toString(),
                        "vm",
                        "--sleep-time",
                        "1")
                .redirectErrorStream(true)
                .start();
        var stdout = new String(process.getInputStream().readAllBytes());
        var exitCode = process.waitFor();
        assertThat(exitCode).as("exit code; stdout was:%n%s", stdout).isNotEqualTo(0);
        assertThat(stdout).contains("--pause-detectors is only supported with the open-world load model");
    }
}
