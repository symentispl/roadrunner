package io.roadrunner.core.tests;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.Measurements;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.samplers.vm.VmSamplerProvider;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RoadrunnerTests {
    @Test
    void generateOpenWorldLoad(@TempDir Path tempDir) throws Exception {
        Measurements measurements;
        try (var roadrunner = new Bootstrap()
                .withOpenWorldModel(5, Duration.ofSeconds(2))
                .withOutputDir(tempDir)
                .build()) {
            try (var samplerProvider = VmSamplerProvider.from(Duration.ofMillis(10))) {
                var sampler = samplerProvider.newSampler();
                measurements = roadrunner.execute(() -> sampler::execute);
            }
            assertThat(measurements.samplesReader())
                    .first(type(UserEvent.Enter.class))
                    .satisfies(e -> {
                        assertThat(e.timestamp()).isGreaterThan(0);
                    });
            // 5 rps * 2s = 10 expected; allow ±4 tolerance for scheduling jitter
            assertThat(measurements.samplesReader())
                    .filteredOn(SamplerResponse.class::isInstance)
                    .asInstanceOf(collection(SamplerResponse.Response.class))
                    .hasSizeBetween(6, 20)
                    .allSatisfy(m -> {
                        assertThat(m.scheduledStartTime())
                                .isLessThanOrEqualTo(m.timestamp())
                                .isGreaterThan(0);
                        assertThat(m.timestamp()).isGreaterThan(0);
                        assertThat(m.stopTime()).isGreaterThan(m.timestamp());
                        assertThat(m.latency()).isGreaterThanOrEqualTo(0);
                    });
            assertThat(measurements.samplesReader())
                    .filteredOn(UserEvent.class::isInstance)
                    .hasSizeBetween(6, 20)
                    .allSatisfy(e -> {
                        assertThat(e.timestamp()).isGreaterThan(0);
                    });
        }
    }

    @Test
    void generateCloseWorldLoad(@TempDir Path tempDir) throws Exception {
        Measurements measurements;
        try (var roadrunner = new Bootstrap()
                .withClosedWorldModel(1, 10)
                .withOutputDir(tempDir)
                .build()) {
            try (var samplerProvider = VmSamplerProvider.from(Duration.ofMillis(100))) {
                var sampler = samplerProvider.newSampler();
                measurements = roadrunner.execute(() -> sampler::execute);
            }
            assertThat(measurements.samplesReader())
                    .first(type(UserEvent.Enter.class))
                    .satisfies(e -> {
                        assertThat(e.timestamp()).isGreaterThan(0);
                    });
            assertThat(measurements.samplesReader())
                    .filteredOn(SamplerResponse.class::isInstance)
                    .asInstanceOf(collection(SamplerResponse.Response.class))
                    .hasSize(10)
                    .allSatisfy(m -> {
                        assertThat(m.scheduledStartTime())
                                .isLessThanOrEqualTo(m.timestamp())
                                .isGreaterThan(0);
                        assertThat(m.timestamp()).isGreaterThan(0);
                        assertThat(m.stopTime()).isGreaterThan(m.timestamp());
                        assertThat(m.latency()).isGreaterThan(0);
                    });
            assertThat(measurements.samplesReader())
                    .last(type(UserEvent.Exit.class))
                    .satisfies(e -> {
                        assertThat(e.timestamp()).isGreaterThan(0);
                    });
        }
    }
}
