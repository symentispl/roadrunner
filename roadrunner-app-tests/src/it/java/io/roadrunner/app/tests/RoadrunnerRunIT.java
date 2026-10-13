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
import java.util.ArrayList;
import java.util.List;
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
    void helpRejectsInvalidDocumentedCommands() throws Exception {
        for (var args : List.of(
                List.of("-c", "50", "-n", "500", "vm", "--sleep-time", "10"),
                List.of("run", "-c", "50", "-n", "500", "renamed-sampler"),
                List.of("run", "-c", "50", "-n", "500", "vm", "--unknown-option"),
                List.of("run", "--unknown-option", "vm"))) {
            var command = new ArrayList<String>();
            command.add(roadrunnerBin.toString());
            command.addAll(args);
            command.add("--help");
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var stdout = new String(process.getInputStream().readAllBytes());
            assertThat(process.waitFor())
                    .as("exit code for %s; stdout was:%n%s", args, stdout)
                    .isNotEqualTo(0);
            assertThat(stdout).contains("error:");
        }
    }

    @Test
    void helpAllowsMissingRequiredArguments() throws Exception {
        for (var args : List.of(
                List.of("--help"),
                List.of("run", "--help"),
                List.of("run", "vm", "--help"),
                List.of("run", "jdbc", "--help"),
                List.of("run", "zero", "--help"))) {
            var command = new ArrayList<String>();
            command.add(roadrunnerBin.toString());
            command.addAll(args);
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var stdout = new String(process.getInputStream().readAllBytes());
            assertThat(process.waitFor())
                    .as("exit code for %s; stdout was:%n%s", args, stdout)
                    .isEqualTo(0);
            assertThat(stdout).contains("Usage:");
        }
    }

    @Test
    void documentationCheckerHandlesSynopsesLaunchersAndContinuations(@TempDir Path docsDir) throws Exception {
        var source = Files.writeString(
                docsDir.resolve("examples.adoc"),
                """
                roadrunner run [OPTIONS] <sampler> [SAMPLER_OPTIONS]
                $ roadrunner run -c 10 -n 100 --parameters-source csv:file=missing.csv \\
                  http 'GET("http://localhost:8080/orders/${id}")'
                ./bin/roadrunner run -c 1 -n 1 vm --sleep-time 1
                jbang roadrunner@symentispl -- run -c 1 -n 1 vm
                """);
        var process = new ProcessBuilder(
                        "../etc/check-docs-commands.sh", roadrunnerBin.toAbsolutePath().toString(), source.toString())
                .redirectErrorStream(true)
                .start();
        var stdout = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as("stdout was:%n%s", stdout).isEqualTo(0);
        assertThat(stdout).contains("Checked 4 documented commands; 0 failed.");
    }

    @Test
    void documentationCheckerFailsOnInvalidCommands(@TempDir Path docsDir) throws Exception {
        var source = Files.writeString(
                docsDir.resolve("README.md"),
                """
                roadrunner -c 50 -n 500 vm --sleep-time 10
                roadrunner run -c 1 -n 1 vm --unknown-option
                roadrunner run -c 1 -n 1 renamed-sampler
                roadrunner run -c 1 -n 1 vm
                """);
        var process = new ProcessBuilder(
                        "../etc/check-docs-commands.sh", roadrunnerBin.toAbsolutePath().toString(), source.toString())
                .redirectErrorStream(true)
                .start();
        var stdout = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as("stdout was:%n%s", stdout).isNotEqualTo(0);
        assertThat(stdout).contains("Checked 4 documented commands; 3 failed.");
        assertThat(stdout).contains(source + ":1", source + ":2", source + ":3");
    }

    @Test
    void closedWorldRunWithoutPauseDetectors(@TempDir Path outputDir) throws Exception {
        // Regression: passing no --pause-detectors used to NPE because the EnumSet field was null.
        var process = new ProcessBuilder(
                roadrunnerBin.toString(),
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
        assertThat(stdout).contains("Roadrunner results available at %s".formatted(outputDir))
                .doesNotContain("Roadrunner HTML report at ");
        assertThat(outputDir.resolve("output.csv")).exists();
        assertThat(outputDir.resolve("latency.hgrm")).doesNotExist();
    }

    @Test
    void htmlReportRunPrintsResultsAndReportLocation(@TempDir Path outputDir) throws Exception {
        var process = new ProcessBuilder(
                roadrunnerBin.toString(),
                "run",
                "-n",
                "10",
                "-c",
                "2",
                "-r",
                "html",
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
        assertThat(stdout).contains("Roadrunner results available at %s".formatted(outputDir),
                "Roadrunner HTML report at %s".formatted(outputDir.resolve("index.html")));
        assertThat(outputDir.resolve("index.html")).exists();
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
