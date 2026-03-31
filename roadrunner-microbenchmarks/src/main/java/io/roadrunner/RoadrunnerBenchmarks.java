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
package io.roadrunner;

import io.roadrunner.api.Roadrunner;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.samplers.vm.VmSamplerProvider;
import java.io.IOException;
import java.time.Duration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

public class RoadrunnerBenchmarks {

    @State(Scope.Benchmark)
    public static class ClosedWorldBenchmark {
        private Roadrunner roadrunner;
        private VmSamplerProvider samplerProvider;
        private Sampler sampler;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = new Bootstrap().withClosedWorldModel(1, 10).build();
            samplerProvider = VmSamplerProvider.from(Duration.ofMillis(100));
            sampler = samplerProvider.newSampler();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            roadrunner.close();
            samplerProvider.close();
        }
    }

    @State(Scope.Benchmark)
    public static class OpenWorldBenchmark {
        private Roadrunner roadrunner;
        private VmSamplerProvider samplerProvider;
        private Sampler sampler;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = new Bootstrap()
                    .withOpenWorldModel(10, Duration.ofSeconds(1))
                    .build();
            samplerProvider = VmSamplerProvider.from(Duration.ofMillis(100));
            sampler = samplerProvider.newSampler();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            roadrunner.close();
            samplerProvider.close();
        }
    }

    @Benchmark
    @Fork(value = 1, warmups = 1, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
    public void closedWorldBenchmark(ClosedWorldBenchmark benchmark) {
        benchmark.roadrunner.execute(() -> benchmark.sampler::execute);
    }

    @Benchmark
    @Fork(value = 1, warmups = 1, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
    public void openWorldBenchmark(OpenWorldBenchmark benchmark) {
        benchmark.roadrunner.execute(() -> benchmark.sampler::execute);
    }
}
