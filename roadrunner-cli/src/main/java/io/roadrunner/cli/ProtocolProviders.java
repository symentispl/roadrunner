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
package io.roadrunner.cli;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import io.roadrunner.protocols.spi.ProtocolProvider;
import java.util.Collection;
import java.util.Map;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProtocolProviders implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ProtocolProviders.class);
    private final Map<String, ProtocolProvider> protocolProviders;

    ProtocolProviders(Map<String, ProtocolProvider> protocolProviders) {
        this.protocolProviders = protocolProviders;
    }

    static ProtocolProviders load() {
        LOG.debug("loading protocol providers");
        var protocols = ServiceLoader.load(ProtocolProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .peek(protocolProvider -> LOG.debug("found protocol {}", protocolProvider.name()))
                .collect(toMap(ProtocolProvider::name, identity()));
        return new ProtocolProviders(protocols);
    }

    @Override
    public void close() {
        LOG.info("closing protocol providers");
        for (ProtocolProvider protocolProvider : protocolProviders.values()) {
            try {
                protocolProvider.close();
            } catch (Exception e) {
                LOG.error("cannot close protocol provider {}", protocolProvider.name());
            }
        }
    }

    public Collection<ProtocolProvider> all() {
        return protocolProviders.values();
    }
}
