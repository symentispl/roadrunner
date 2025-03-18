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
package io.roadrunner.protocols.vm;

import io.roadrunner.api.protocol.Protocol;
import io.roadrunner.api.protocol.Response;
import io.roadrunner.protocols.spi.ProtocolProvider;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        description = "In-VM protocol, used as baseline to calculate roadrunner overhead",
        mixinStandardHelpOptions = true)
public class VmProtocolProvider implements ProtocolProvider {

    @Option(names = "--sleep-time", description = "sleep time in ms", required = true)
    long sleepTime;

    // provided for testing
    public static VmProtocolProvider from(Duration sleepTime) {
        var vmProtocolProvider = new VmProtocolProvider();
        vmProtocolProvider.sleepTime = sleepTime.toMillis();
        return vmProtocolProvider;
    }

    @Override
    public String name() {
        return "vm";
    }

    @Override
    public Protocol newProtocol() {
        return () -> CompletableFuture.supplyAsync(() -> {
                    var startTime = System.nanoTime();
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    var stopTime = System.nanoTime();
                    return Response.empty(startTime, stopTime);
                })
                .join();
    }

    @Override
    public void close() {}
}
