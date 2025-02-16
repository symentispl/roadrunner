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

import io.roadrunner.api.options.CliOptionsBuilder;
import io.roadrunner.protocols.spi.ProtocolProvider;
import io.roadrunner.protocols.spi.ProtocolRequest;
import java.util.concurrent.CompletableFuture;

public class VmProtocolProvider implements ProtocolProvider<VmProtocolOptions> {
    @Override
    public String name() {
        return "vm";
    }

    @Override
    public VmProtocolOptions requestOptions(String[] protocolArgs) {
        var optionsBuilder = new CliOptionsBuilder();
        var optionsBinding = optionsBuilder.build(VmProtocolOptions.class);
        try {
            var vmProtocolOptions = optionsBinding.newInstance(protocolArgs);
            return vmProtocolOptions;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ProtocolRequest request(VmProtocolOptions requestOptions) {
        return () -> {
            CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(requestOptions.sleepTime());
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .join();
        };
    }

    @Override
    public void close() throws Exception {}
}
