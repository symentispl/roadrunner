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
import io.roadrunner.api.attachments.AttachmentKey;
import io.roadrunner.api.attachments.AttachmentRegistry;
import io.roadrunner.api.metrics.MetricKey;
import io.roadrunner.api.metrics.MetricRegistry;
import io.roadrunner.api.metrics.MetricUnit;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.samplers.vm.VmSamplerProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

public class RoadrunnerBenchmarks {

    @State(Scope.Benchmark)
    public static class ClosedWorldBenchmark {
        Roadrunner roadrunner;
        VmSamplerProvider samplerProvider;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = bootstrap().withClosedWorldModel(1, 10).build();
            samplerProvider = VmSamplerProvider.from(Duration.ofMillis(100));
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            roadrunner.close();
            samplerProvider.close();
        }
    }

    @State(Scope.Benchmark)
    public static class ClosedWorldInstrumentedBenchmark {
        Roadrunner roadrunner;
        InstrumentedVmSamplerProvider samplerProvider;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = bootstrap().withClosedWorldModel(1, 10).build();
            samplerProvider = new InstrumentedVmSamplerProvider(Duration.ofMillis(100));
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            roadrunner.close();
            samplerProvider.close();
        }
    }

    @State(Scope.Benchmark)
    public static class OpenWorldBenchmark {
        Roadrunner roadrunner;
        VmSamplerProvider samplerProvider;

        @Param({"1", "10", "100", "1000", "10000"})
        public int arrivalRate;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = bootstrap()
                    .withOpenWorldModel(arrivalRate, Duration.ofSeconds(10))
                    .build();
            samplerProvider = VmSamplerProvider.from(Duration.ofMillis(100));
        }

        @TearDown(Level.Trial)
        public void tearDown() throws Exception {
            roadrunner.close();
            samplerProvider.close();
        }
    }

    @State(Scope.Benchmark)
    public static class OpenWorldInstrumentedBenchmark {
        Roadrunner roadrunner;
        InstrumentedVmSamplerProvider samplerProvider;

        @Param({"1", "10", "100", "1000", "10000"})
        public int arrivalRate;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            roadrunner = bootstrap()
                    .withOpenWorldModel(arrivalRate, Duration.ofSeconds(10))
                    .build();
            samplerProvider = new InstrumentedVmSamplerProvider(Duration.ofMillis(100));
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
        benchmark.roadrunner.execute(benchmark.samplerProvider);
    }

    @Benchmark
    @Fork(value = 1, warmups = 1, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
    public void closedWorldBenchmarkWithMetrics(ClosedWorldInstrumentedBenchmark benchmark) {
        benchmark.roadrunner.execute(benchmark.samplerProvider);
    }

    @Benchmark
    @Fork(value = 1, warmups = 1, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
    public void openWorldBenchmark(OpenWorldBenchmark benchmark) {
        benchmark.roadrunner.execute(benchmark.samplerProvider);
    }

    @Benchmark
    @Fork(value = 1, warmups = 1, jvmArgsAppend = "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn")
    public void openWorldBenchmarkWithMetrics(OpenWorldInstrumentedBenchmark benchmark) {
        benchmark.roadrunner.execute(benchmark.samplerProvider);
    }

    static final class InstrumentedVmSamplerProvider implements SamplerProvider {
        private final ExecutorService executorService = Executors.newCachedThreadPool();
        private final long sleepMillis;
        private MetricKey latencyKey;
        private AttachmentKey threadNameKey;

        InstrumentedVmSamplerProvider(Duration sleepTime) {
            this.sleepMillis = sleepTime.toMillis();
        }

        @Override
        public void registerMetrics(MetricRegistry registry) {
            latencyKey = registry.register("sleep_latency", MetricUnit.NANOSECONDS);
        }

        @Override
        public void registerAttachments(AttachmentRegistry registry) {
            threadNameKey = registry.register("thread_name");
        }

        @Override
        public Sampler newSampler() {
            return (params, builder) -> CompletableFuture.supplyAsync(
                            () -> {
                                var startTime = System.nanoTime();
                                try {
                                    Thread.sleep(sleepMillis);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                var stopTime = System.nanoTime();
                                return builder.response(startTime, stopTime, sink -> {
                                    sink.add(latencyKey, stopTime - startTime);
                                    sink.attach(
                                            threadNameKey,
                                            Thread.currentThread().getName());
                                });
                            },
                            executorService)
                    .join();
        }

        @Override
        public void close() {
            executorService.close();
        }
    }

    static Bootstrap bootstrap() throws IOException {
        return new Bootstrap().withOutputDir(Files.createTempDirectory("roadrunner-benchmarks-"));
    }
}
