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
import io.roadrunner.api.parameters.ParameterSource;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.samplers.zero.ZeroSamplerProvider;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * End-to-end check of the engine's parameter hand-out (issue #138): the zero sampler driven by
 * the closed- and open-world models while a {@link ParameterSource} supplies {@code rows}
 * distinct parameter rows. Every sampler call goes through the engine's shared round-robin
 * counter, so throughput as {@code concurrentUsers} / {@code arrivalRate} grows shows whether
 * parameter handling matters in situ with virtual threads.
 * <p>
 * It cannot attribute cost to that counter alone: the measurement bookkeeping has its own shared
 * counter and the response queue is shared as well. Isolating {@code next()} needs a direct
 * microbenchmark of the dispatcher.
 */
@Fork(value = 1, warmups = 1, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
public class ParameterFeedRoadrunnerBenchmarks {

    /** Distinct rows, so the large case really exercises the array and heap footprint. */
    static ParameterSource rowsSource(int rows) {
        var parameters = new SamplerParameters[rows];
        for (int i = 0; i < rows; i++) {
            parameters[i] = SamplerParameters.of("id", i);
        }
        var list = List.of(parameters);
        return () -> list::iterator;
    }

    @State(Scope.Benchmark)
    public static class ClosedWorld {
        Roadrunner roadrunner;
        SamplerProvider samplerProvider;

        @Param({"1", "16", "256", "1024"})
        public int concurrentUsers;

        @Param({"1", "100000"})
        public int rows;

        @Param({"1000000"})
        public int requests;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = bootstrap()
                    .withClosedWorldModel(concurrentUsers, requests)
                    .withParameterSource(rowsSource(rows))
                    .build();
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

        @Param({"1000", "10000", "100000"})
        public int arrivalRate;

        @Param({"1", "100000"})
        public int rows;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = bootstrap()
                    .withOpenWorldModel(arrivalRate, Duration.ofSeconds(10))
                    .withParameterSource(rowsSource(rows))
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
