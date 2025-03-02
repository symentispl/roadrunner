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

import io.roadrunner.api.charts.ChartGeneratorProvider;
import java.util.Map;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ChartGeneratorProviders {

    private static final Logger LOG = LoggerFactory.getLogger(ChartGeneratorProviders.class);
    private final Map<String, ChartGeneratorProvider> chartGeneratorProviders;

    ChartGeneratorProviders(Map<String, ChartGeneratorProvider> chartGeneratorProviders) {
        this.chartGeneratorProviders = chartGeneratorProviders;
    }

    static ChartGeneratorProviders load() {
        var protocols = ServiceLoader.load(ChartGeneratorProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .peek(protocolProvider -> LOG.info("found chart generator {}", protocolProvider.name()))
                .collect(toMap(ChartGeneratorProvider::name, identity()));
        return new ChartGeneratorProviders(protocols);
    }

    public ChartGeneratorProvider get(String protocolName) {
        return chartGeneratorProviders.get(protocolName);
    }
}
