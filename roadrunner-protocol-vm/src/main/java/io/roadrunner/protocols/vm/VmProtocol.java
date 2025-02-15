/**
 *   Copyright 2024 Symentis.pl
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.roadrunner.protocols.vm;

import io.roadrunner.api.Roadrunner;
import io.roadrunner.protocols.spi.Protocol;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VmProtocol implements Protocol {

    private final Roadrunner roadrunner;
    private final ExecutorService executorService;

    public VmProtocol(Roadrunner roadrunner) {
        this.roadrunner = roadrunner;
        executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void run() {
        roadrunner.execute(() -> this::takeNap, 10, 1000);
    }

    @Override
    public void close() throws Exception {
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
    }

    private void takeNap() {
        CompletableFuture.runAsync(
                        () -> {
                            //                            System.out.println("Taking nap...");
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                //
                            }
                        },
                        executorService)
                .join();
    }
}
