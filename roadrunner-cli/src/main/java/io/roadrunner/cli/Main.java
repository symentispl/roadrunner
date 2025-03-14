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

import static picocli.CommandLine.Model.CommandSpec;
import static picocli.CommandLine.Model.CommandSpec.forAnnotatedObject;

import io.roadrunner.protocols.spi.ProtocolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        LOG.info("loading protocol providers");
        var protocolProviders = ProtocolProviders.load();

        var commandSpec = CommandSpec.create();

        var runCommand = forAnnotatedObject(new RunCommand()).mixinStandardHelpOptions(true);

        for (var protocolProvider : protocolProviders.all()) {
            runCommand
                    .addSubcommand(protocolProvider.name(), forAnnotatedObject(protocolProvider))
                    .mixinStandardHelpOptions(true);
        }

        commandSpec.mixinStandardHelpOptions(true);
        commandSpec.addSubcommand("run", runCommand);

        var commandLine = new CommandLine(commandSpec);
        var parseResult = commandLine.parseArgs(args);

        if (parseResult.isUsageHelpRequested()) {
            commandLine.usage(System.out);
        }

        var subcommand = parseResult.subcommand();
        if (subcommand.isUsageHelpRequested()) {
            subcommand.commandSpec().commandLine().usage(System.out);
        }

        if (subcommand.commandSpec().userObject() instanceof RunCommand r) {
            var protocolSubCmd = subcommand.subcommand();
            if (protocolSubCmd.commandSpec().userObject() instanceof ProtocolProvider protocolProvider) {
                r.run(protocolProvider);
            }
        }
    }
}
