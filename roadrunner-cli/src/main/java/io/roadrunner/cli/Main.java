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
package io.roadrunner.cli;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import io.roadrunner.core.Bootstrap;
import io.roadrunner.core.options.CliOptionsBuilder;
import io.roadrunner.protocols.spi.ProtocolProvider;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements Callable<Integer> {

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
        var roadrunner =
                bootstrap.withConcurrency(roadrunnerOptions.concurrency()).build();

        try {

            roadrunner.execute(() -> () -> {}, 100, 1000);

            //            var roadrunner = bootstrap.build();
            //            roadrunner.
            // System.exit(exit);
        } finally {
            LOG.info("closing protocol providers");
            //    protocols.stream().forEach(protocol -> {
            //        try {
            //            protocol.close();
            //        } catch (Exception e) {
            //            throw new RuntimeException(e);
            //        }
            //    });
        }
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("Hello");
        return 0;
    }
}
