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

import static io.roadrunner.BenchmarkSupport.bootstrap;

import io.roadrunner.api.Roadrunner;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.samplers.zero.ZeroSamplerProvider;
import java.io.IOException;
import java.time.Duration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@Fork(value = 1, warmups = 1, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
public class ZeroRoadrunnerBenchmarks {

    @State(Scope.Benchmark)
    public static class ClosedWorld {
        Roadrunner roadrunner;
        SamplerProvider samplerProvider;

        @Param({"100"})
        public int concurrentUsers;

        @Param({"100"})
        public int requests;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner =
                    bootstrap().withClosedWorldModel(concurrentUsers, requests).build();
            samplerProvider = new ZeroSamplerProvider();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            roadrunner.close();
            samplerProvider.close();
        }
    }

    @State(Scope.Benchmark)
    public static class OpenWorld {
        Roadrunner roadrunner;
        SamplerProvider samplerProvider;

        @Param({"100"})
        public int arrivalRate;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = bootstrap()
                    .withOpenWorldModel(arrivalRate, Duration.ofSeconds(10))
                    .build();
            samplerProvider = new ZeroSamplerProvider();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            roadrunner.close();
            samplerProvider.close();
        }
    }

    @Benchmark
    public void closedWorld(ClosedWorld state) {
        state.roadrunner.execute(state.samplerProvider);
    }

    @Benchmark
    public void openWorld(OpenWorld state) {
        state.roadrunner.execute(state.samplerProvider);
    }
}
