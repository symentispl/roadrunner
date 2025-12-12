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
import java.nio.file.Files;
import java.time.Duration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class RoadrunnerBenchmarks {

    private Roadrunner roadrunner;
    private Protocol protocol;
    private VmProtocolProvider protocolProvider;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        roadrunner = new Bootstrap()
                .withConcurrency(1)
                .withRequests(1000)
                .withOutputDir(Files.createTempDirectory("roadrunner-benchmarks-"))
                .build();
        protocolProvider = VmProtocolProvider.from(Duration.ofMillis(10));
        protocol = protocolProvider.newProtocol();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        try {
            protocolProvider.close();
        } finally {
            roadrunner.close();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Fork(value = 10, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
    public void executeRoadrunnerVmProtocol() {
        roadrunner.execute(() -> protocol::execute);
    }
}
