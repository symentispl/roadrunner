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

import io.roadrunner.api.Measurements;
import io.roadrunner.options.CliOptionsBuilder;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.protocols.spi.ProtocolProvider;
import io.roadrunner.protocols.spi.ProtocolRequest;
import io.roadrunner.protocols.spi.ProtocolRequestOptions;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        LOG.info("loading protocol providers");
        var protocols = ServiceLoader.load(ProtocolProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .peek(protocolProvider -> LOG.info("found protocol {}", protocolProvider.name()))
                .collect(toMap(p -> p.name(), identity()));

        var cliOptionsBuilder = new CliOptionsBuilder();
        var optionsBinding = cliOptionsBuilder.build(RoadrunnerOptions.class);
        var roadrunnerOptions = optionsBinding.newInstance(args);

        var bootstrap = new Bootstrap();
        var roadrunner = bootstrap
                .withConcurrency(roadrunnerOptions.concurrency())
                .withRequests(roadrunnerOptions.numberOfRequests())
                .build();

        var remainingArgs = optionsBinding.args();
        var protocolName = remainingArgs[0];
        var protocolArgs = new String[remainingArgs.length - 1];
        System.arraycopy(remainingArgs, 1, protocolArgs, 0, protocolArgs.length);

        var protocol = protocols.get(protocolName);
        ProtocolRequestOptions requestOptions = protocol.requestOptions(protocolArgs);
        ProtocolRequest request = protocol.request(requestOptions);

        try {
            var measurements = roadrunner.execute(() -> request::execute);
            prettyPrintHistogramSummary(measurements);

        } finally {
            LOG.info("closing protocol providers");
            protocols.values().stream().forEach(p -> {
                try {
                    p.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public static void prettyPrintHistogramSummary(Measurements measurements) {
        System.out.println("HdrHistogram Summary:");
        System.out.println("=====================");
        System.out.printf("Total Count    : %d%n", measurements.totalCount());
        System.out.printf("Min Value (ms) : %d ms%n", measurements.minValue());
        System.out.printf("Max Value (ms) : %d ms%n", measurements.maxValue());
        System.out.printf("Mean Value (ms): %.2f ms%n", measurements.mean());
        System.out.printf("50th Percentile: %.2f ms%n", measurements.p50());
        System.out.printf("90th Percentile: %.2f ms%n", measurements.p90());
        System.out.printf("99th Percentile: %.2f ms%n", measurements.p99());
        System.out.printf("99.9th Percentile: %.2f ms%n", measurements.p999());
        System.out.println("=====================");
    }
}
