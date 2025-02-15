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

import static picocli.CommandLine.*;

import io.roadrunner.core.Bootstrap;
import io.roadrunner.core.internal.DefaultRoadrunner;
import io.roadrunner.protocols.spi.ProtocolProvider;
import picocli.CommandLine;

import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command()
public class Main implements Callable<Integer> {

    @Option(
            names = {"-c"},
            description = "Number of multiple requests to make at a time",
            required = true)
    private int concurrency;

    @Option(
            names = {"-n"},
            description = "Number of requests to perform",
            required = true)
    private int requests;

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        var commandSpec = Model.CommandSpec.forAnnotatedObject(new RoadrunnerOptions());
        
        var bootstrap = new Bootstrap();
        var roadrunner = new DefaultRoadrunner();
        var commandLine = new CommandLine(new Main());

        LOG.info("loading protocol providers");
        ServiceLoader.load(ProtocolProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(p -> p.commandSpec())
                .forEach(p -> commandSpec.addSubcommand("", p));

               //protocols.stream().forEach(commandLine::addSubcommand);
        
               try {
                   var exit = commandLine.execute(args);
        
                   //            var roadrunner = bootstrap.build();
                   //            roadrunner.
                   System.exit(exit);
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
