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

import io.roadrunner.samplers.spi.SamplerProvider;
import java.nio.file.Paths;
import picocli.CommandLine;

public class Main {

    static void main(String[] args) throws Exception {
        try (var samplerProviders =
                SamplerProviders.load(new Preferences(Paths.get(System.getProperty("user.home"))))) {

            var commandSpec = createCommandSpec(samplerProviders);

            var commandLine = new CommandLine(commandSpec);

            CommandLine.ParseResult parseResult;
            try {
                parseResult = commandLine.parseArgs(args);
            } catch (CommandLine.ParameterException e) {
                for (String arg : args) {
                    if ("--help".equals(arg) || "-h".equals(arg)) {
                        e.getCommandLine().usage(System.out);
                        return;
                    }
                }
                throw e;
            }

            if (parseResult.isUsageHelpRequested()) {
                commandLine.usage(System.out);
                return;
            }

            if (parseResult.isVersionHelpRequested()) {
                commandLine.printVersionHelp(System.out);
                return;
            }

            var subcommand = parseResult.subcommand();
            if (subcommand.isUsageHelpRequested()) {
                subcommand.commandSpec().commandLine().usage(System.out);
                return;
            }

            if (subcommand.commandSpec().userObject() instanceof RunCommand runCommand) {
                var samplerSubCmd = subcommand.subcommand();
                if (samplerSubCmd != null) {
                    if (samplerSubCmd.isUsageHelpRequested()) {
                        samplerSubCmd.commandSpec().commandLine().usage(System.out);
                        return;
                    }
                    if (samplerSubCmd.commandSpec().userObject() instanceof SamplerProvider samplerProvider) {
                        runCommand.run(samplerProvider);
                    }
                }
            }
        }
    }

    private static CommandSpec createCommandSpec(SamplerProviders samplerProviders) {
        var commandSpec = CommandSpec.create();
        commandSpec.versionProvider(() -> new String[] {"Roadrunner, a simplistic load generator"});
        var runCommand = forAnnotatedObject(new RunCommand()).mixinStandardHelpOptions(true);

        for (var samplerProvider : samplerProviders.all()) {
            runCommand
                    .addSubcommand(samplerProvider.name(), forAnnotatedObject(samplerProvider))
                    .mixinStandardHelpOptions(true);
        }

        commandSpec.mixinStandardHelpOptions(true);
        commandSpec.addSubcommand("run", runCommand);
        return commandSpec;
    }
}
