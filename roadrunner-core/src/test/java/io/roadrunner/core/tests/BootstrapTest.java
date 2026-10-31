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
package io.roadrunner.core.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import io.roadrunner.api.measurments.ProgressSnapshot;
import io.roadrunner.api.parameters.ParameterSource;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.core.Bootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootstrapTest {

    @TempDir
    Path tempDir;

    @Test
    void createsTimestampedOutputDirectoryByDefault() throws Exception {
        var bootstrap = new Bootstrap().withClosedWorldModel(1, 1);

        try (var roadrunner = bootstrap.build()) {
            assertThat(bootstrap.outputDir()).isDirectory();
            assertThat(bootstrap.outputDir().getFileName().toString())
                    .matches("roadrunner-\\d{8}-\\d{6}(-\\d+)?");
        } finally {
            if (bootstrap.outputDir() != null) {
                Files.deleteIfExists(bootstrap.outputDir());
            }
        }
    }

    @Test
    void createsOutputDirectoryUsingConfiguredNamingStrategy() throws Exception {
        var expected = tempDir.resolve("custom-run");
        var bootstrap = new Bootstrap()
                .withClosedWorldModel(1, 1)
                .withOutputNamingStrategy(() -> expected.toString());

        try (var roadrunner = bootstrap.build()) {
            assertThat(bootstrap.outputDir()).isEqualTo(expected).isDirectory();
        }
    }

    @Test
    void appendsSuffixWhenGeneratedOutputDirectoryAlreadyExists() throws Exception {
        var existing = Files.createDirectory(tempDir.resolve("custom-run"));
        var marker = Files.writeString(existing.resolve("keep.txt"), "previous run");
        var bootstrap = new Bootstrap()
                .withClosedWorldModel(1, 1)
                .withOutputNamingStrategy(() -> existing.toString());

        try (var roadrunner = bootstrap.build()) {
            assertThat(bootstrap.outputDir()).isEqualTo(tempDir.resolve("custom-run-2")).isDirectory();
            assertThat(marker).hasContent("previous run");
        }
    }

    @Test
    void usesExplicitOutputDirectoryWithoutInvokingNamingStrategy() throws Exception {
        var bootstrap = new Bootstrap()
                .withClosedWorldModel(1, 1)
                .withOutputDir(tempDir)
                .withOutputNamingStrategy(() -> {
                    throw new AssertionError("Explicit output directory must take precedence");
                });

        try (var roadrunner = bootstrap.build()) {
            assertThat(bootstrap.outputDir()).isEqualTo(tempDir);
        }
    }

    @Test
    void rejectsBuildWithoutLoadStrategy() {
        var bootstrap = new Bootstrap().withOutputDir(tempDir);

        assertThatIllegalStateException()
                .isThrownBy(bootstrap::build)
                .withMessage("Load strategy must be configured");
    }

    @Test
    void passesConfiguredMeasurementProgressToRoadrunner() throws Exception {
        var progress = new AtomicReference<ProgressSnapshot>();
        var bootstrap = new Bootstrap()
                .withClosedWorldModel(1, 1)
                .withOutputDir(tempDir)
                .withMeasurementProgress(progress::set);
        Sampler sampler = (parameters, builder) -> {
            long now = System.nanoTime();
            return builder.response(now, now);
        };

        try (var roadrunner = bootstrap.build()) {
            roadrunner.execute(() -> sampler);
        }

        assertThat(progress.get()).isNotNull();
        assertThat(progress.get().processed()).isEqualTo(1);
        assertThat(progress.get().errors()).isZero();
    }

    @Test
    void passesConfiguredParametersToSampler() throws Exception {
        var expected = SamplerParameters.of("name", "bootstrap-test");
        ParameterSource source = () -> () -> List.of(expected).iterator();
        var received = new AtomicReference<SamplerParameters>();
        var bootstrap = new Bootstrap()
                .withClosedWorldModel(1, 1)
                .withOutputDir(tempDir)
                .withParameterSource(source);
        Sampler sampler = (parameters, builder) -> {
            received.set(parameters);
            long now = System.nanoTime();
            return builder.response(now, now);
        };

        try (var roadrunner = bootstrap.build()) {
            roadrunner.execute(() -> sampler);
        }

        assertThat(received.get()).isNotNull();
        assertThat(received.get().valueOf("name")).isEqualTo("bootstrap-test");
    }
}
