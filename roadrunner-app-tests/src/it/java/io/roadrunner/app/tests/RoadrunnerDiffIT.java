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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.LINUX)
public class RoadrunnerDiffIT {

    private static final int TOLERANCE_BREACH = 3;

    private Path roadrunnerBin;

    @BeforeEach
    void ensureExecutable() throws Exception {
        Path appDir = Path.of("target/roadrunner-app");
        try (var paths = Files.walk(appDir, 3)) {
            roadrunnerBin = paths.filter(p -> p.getFileName().toString().equals("roadrunner")
                            && Files.isRegularFile(p))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("roadrunner binary not found under " + appDir));
        }
        Files.setPosixFilePermissions(roadrunnerBin, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    // one request every millisecond, each taking latencyMillis
    private static Path run(Path dir, long latencyMillis) throws IOException {
        var latency = latencyMillis * 1_000_000;
        var rows = IntStream.range(0, 100)
                .mapToObj(i -> "REQ,%d,%d,%d,%d,OK"
                        .formatted(i * 1_000_000L, i * 1_000_000L, i * 1_000_000L + latency, latency))
                .collect(Collectors.joining("\n"));
        Files.writeString(dir.resolve("output.csv"), "type,scheduled,start,stop,latency,status\n" + rows + "\n");
        return dir;
    }

    private record Result(int exitCode, String output) {}

    private Result roadrunner(String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add(roadrunnerBin.toString());
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes());
        return new Result(process.waitFor(), output);
    }

    @Test
    void diffWithoutTolerancesReportsAndSucceeds(@TempDir Path baseline, @TempDir Path current) throws Exception {
        var result = roadrunner("diff", run(baseline, 10).toString(), run(current, 20).toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Roadrunner comparison").containsPattern("p99 +10 ms +20 ms +\\+100\\.0%");
    }

    @Test
    void diffWithinToleranceSucceeds(@TempDir Path baseline, @TempDir Path current) throws Exception {
        var result = roadrunner(
                "diff", "--tolerance", "p99:+10%", run(baseline, 10).toString(), run(current, 10).toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).containsPattern("overall +PASSED");
    }

    @Test
    void diffBeyondToleranceExitsWithBreachCode(@TempDir Path baseline, @TempDir Path current) throws Exception {
        var result = roadrunner(
                "diff", "--tolerance", "p99:+10%", run(baseline, 10).toString(), run(current, 20).toString());

        assertThat(result.exitCode()).as(result.output()).isEqualTo(TOLERANCE_BREACH);
        assertThat(result.output()).containsPattern("p99 .* FAILED").containsPattern("overall +FAILED");
        assertThat(result.output()).contains("tolerance breached: p99");
    }

    @Test
    void diffOfAnUnreadableDirectoryIsAFailureNotABreach(@TempDir Path baseline, @TempDir Path missing)
            throws Exception {
        var result = roadrunner("diff", run(baseline, 10).toString(), missing.resolve("nope").toString());

        assertThat(result.exitCode()).as(result.output()).isEqualTo(1);
        assertThat(result.output()).contains("current run directory").contains("output.csv");
    }

    @Test
    void diffRejectsAMalformedTolerance(@TempDir Path baseline, @TempDir Path current) throws Exception {
        var result = roadrunner(
                "diff", "--tolerance", "p99:ten", run(baseline, 10).toString(), run(current, 10).toString());

        assertThat(result.exitCode()).as(result.output()).isEqualTo(1);
    }

    @Test
    void runAgainstABaselineComparesAndGatesOnTheVerdict(@TempDir Path baseline, @TempDir Path output)
            throws Exception {
        // the baseline took 1 ms per request, the vm sampler below sleeps for 50 ms
        var result = roadrunner(
                "run",
                "-n",
                "5",
                "-c",
                "1",
                "-s",
                output.toString(),
                "--baseline",
                run(baseline, 1).toString(),
                "--tolerance",
                "p99:+10%",
                "vm",
                "--sleep-time",
                "50");

        assertThat(result.exitCode()).as(result.output()).isEqualTo(TOLERANCE_BREACH);
        assertThat(result.output()).contains("Roadrunner comparison").containsPattern("overall +FAILED");
        assertThat(output.resolve("output.csv")).exists();
    }

    @Test
    void toleranceWithoutBaselineIsRejectedBeforeTheRun(@TempDir Path output) throws Exception {
        var result = roadrunner(
                "run", "-n", "5", "-c", "1", "-s", output.toString(), "--tolerance", "p99:+10%", "vm", "--sleep-time", "1");

        assertThat(result.exitCode()).as(result.output()).isEqualTo(1);
        assertThat(result.output()).contains("--tolerance needs --baseline");
        assertThat(output.resolve("output.csv")).doesNotExist();
    }
}
