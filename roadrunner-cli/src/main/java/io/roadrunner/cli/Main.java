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

import io.roadrunner.protocols.spi.ProtocolPlugin;

import java.nio.file.Paths;
import org.slf4j.Logger;
import picocli.CommandLine;

public class Main {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        LOG.info("Java home: " + System.getProperty("java.home"));

        try (var protocolProviders =
                ProtocolPlugins.load(new Preferences(Paths.get(System.getProperty("user.home"))))) {

            var commandSpec = createCommandSpec(protocolProviders);

            var commandLine = new CommandLine(commandSpec);
            var parseResult = commandLine.parseArgs(args);

            if (parseResult.isUsageHelpRequested()) {
                commandLine.usage(System.out);
                return;
            }

            var subcommand = parseResult.subcommand();
            if (subcommand.isUsageHelpRequested()) {
                subcommand.commandSpec().commandLine().usage(System.out);
                return;
            }

            if (subcommand.commandSpec().userObject() instanceof RunCommand runCommand) {
                var protocolSubCmd = subcommand.subcommand();
                if (protocolSubCmd.commandSpec().userObject() instanceof ProtocolPlugin protocolPlugin) {
                    runCommand.run(protocolPlugin);
                }
            }
        }
    }

    private static CommandSpec createCommandSpec(ProtocolPlugins protocolPlugins) {
        var commandSpec = CommandSpec.create();

        var runCommand = forAnnotatedObject(new RunCommand()).mixinStandardHelpOptions(true);

        for (var protocolProvider : protocolPlugins.all()) {
            runCommand
                    .addSubcommand(protocolProvider.name(), forAnnotatedObject(protocolProvider))
                    .mixinStandardHelpOptions(true);
        }

        commandSpec.mixinStandardHelpOptions(true);
        commandSpec.addSubcommand("run", runCommand);
        return commandSpec;
    }
}
