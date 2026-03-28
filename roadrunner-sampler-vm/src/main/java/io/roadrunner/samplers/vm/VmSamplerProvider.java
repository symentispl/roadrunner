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
package io.roadrunner.samplers.vm;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.samplers.spi.SamplerProvider;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        description = "In-VM sampler, used as baseline to calculate roadrunner overhead",
        mixinStandardHelpOptions = true)
public class VmSamplerProvider implements SamplerProvider {

    private final ExecutorService executorService;

    @Option(names = "--sleep-time", description = "sleep time in ms", required = true)
    long sleepTime;

    public VmSamplerProvider() {
        executorService = Executors.newCachedThreadPool();
    }

    // provided for testing
    public static VmSamplerProvider from(Duration sleepTime) {
        var samplerProvider = new VmSamplerProvider();
        samplerProvider.sleepTime = sleepTime.toMillis();
        return samplerProvider;
    }

    @Override
    public String name() {
        return "vm";
    }

    @Override
    public Sampler newSampler() {
        return () -> CompletableFuture.supplyAsync(
                        () -> {
                            var startTime = System.nanoTime();
                            try {
                                Thread.sleep(sleepTime);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            var stopTime = System.nanoTime();
                            return SamplerResponse.empty(startTime, stopTime);
                        },
                        executorService)
                .join();
    }

    @Override
    public void close() {
        executorService.close();
    }
}
