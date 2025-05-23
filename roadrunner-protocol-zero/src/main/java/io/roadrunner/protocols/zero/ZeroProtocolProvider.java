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
package io.roadrunner.protocols.zero;

import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.protocol.Protocol;
import io.roadrunner.protocols.spi.ProtocolProvider;
import picocli.CommandLine.Command;

@Command(description = "Zero protocol")
public class ZeroProtocolProvider implements ProtocolProvider {

    public ZeroProtocolProvider() {}

    @Override
    public String name() {
        return "zero";
    }

    @Override
    public Protocol newProtocol() {
        return () -> {
            var nanoTime = System.nanoTime();
            return ProtocolResponse.empty(nanoTime, nanoTime);
        };
    }

    @Override
    public void close() {}
}
