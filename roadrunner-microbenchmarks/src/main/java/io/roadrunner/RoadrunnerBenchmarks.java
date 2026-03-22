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
import io.roadrunner.api.protocol.Protocol;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.protocols.vm.VmProtocolProvider;

import java.io.IOException;
import java.time.Duration;

import org.openjdk.jmh.annotations.*;

public class RoadrunnerBenchmarks {


    @State(Scope.Benchmark)
    public static class ClosedWorldBenchmark {
        private Roadrunner roadrunner;
        private VmProtocolProvider vmProtocol;
        private Protocol request;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = new Bootstrap().withClosedWorldModel(1, 10).build();
            vmProtocol = VmProtocolProvider.from(Duration.ofMillis(100));
            request = vmProtocol.newProtocol();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            roadrunner.close();
            vmProtocol.close();
        }

    }

    @State(Scope.Benchmark)
    public static class OpenWorldBenchmark {
        private Roadrunner roadrunner;
        private VmProtocolProvider vmProtocol;
        private Protocol request;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = new Bootstrap().withOpenWorldModel(10, Duration.ofSeconds(1)).build();
            vmProtocol = VmProtocolProvider.from(Duration.ofMillis(100));
            request = vmProtocol.newProtocol();
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            roadrunner.close();
            vmProtocol.close();
        }

    }

    @Benchmark
    @Fork(value = 1, warmups = 1, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
    public void closedWorldBenchmark(ClosedWorldBenchmark benchmark) {
        benchmark.roadrunner.execute(() -> benchmark.request::execute);
    }

    @Benchmark
    @Fork(value = 1, warmups = 1, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
    public void openWorldBenchmark(OpenWorldBenchmark benchmark) {
        benchmark.roadrunner.execute(() -> benchmark.request::execute);
    }
}
