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

import io.roadrunner.samplers.spi.SamplerOptions;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine;

public class Main {

    static void main(String[] args) throws Exception {
        try (var samplerProviders = SamplerPlugins.load(new Preferences(Paths.get(System.getProperty("user.home"))))) {

            var commandSpec = createCommandSpec(samplerProviders);

            var commandLine = new CommandLine(commandSpec);
            // required options are validated per-command as soon as that command's own args are parsed,
            // regardless of --help further down the subcommand chain (picocli only skips validation for the
            // command level --help was passed to). Collecting errors instead of throwing lets parsing finish
            // building the whole subcommand tree so we can still detect a nested --help and print its usage.
            enableCollectErrors(commandLine);

            var parseResult = commandLine.parseArgs(args);

            if (parseResult.isUsageHelpRequested()) {
                commandLine.usage(System.out);
                return;
            }

            if (parseResult.isVersionHelpRequested()) {
                commandLine.printVersionHelp(System.out);
                return;
            }

            var subcommand = parseResult.subcommand();
            if (subcommand != null && subcommand.isUsageHelpRequested()) {
                subcommand.commandSpec().commandLine().usage(System.out);
                return;
            }

            if (subcommand != null && subcommand.commandSpec().userObject() instanceof RunCommand runCommand) {
                var samplerSubCmd = subcommand.subcommand();
                if (samplerSubCmd != null && samplerSubCmd.isUsageHelpRequested()) {
                    printSamplerUsage(
                            subcommand.commandSpec().commandLine(),
                            samplerSubCmd.commandSpec().commandLine());
                    return;
                }

                var errors = collectErrors(parseResult);
                if (!errors.isEmpty()) {
                    throw errors.get(0);
                }

                if (samplerSubCmd != null
                        && samplerSubCmd.commandSpec().userObject() instanceof SamplerOptions samplerOptions) {
                    try (var samplerProvider = samplerOptions.samplerProvider()) {
                        runCommand.run(samplerProvider);
                    }
                }
            } else {
                var errors = collectErrors(parseResult);
                if (!errors.isEmpty()) {
                    throw errors.get(0);
                }
            }
        }
    }

    /**
     * Prints combined usage help for a sampler subcommand: the sampler's own synopsis and
     * description, followed by the {@code run} command's options (concurrency/rate, report, etc.),
     * the sampler's own options, and its expression syntax, so everything needed to invoke the
     * sampler is visible in one place instead of split across {@code run --help} and
     * {@code run <sampler> --help}.
     */
    private static void printSamplerUsage(CommandLine runCommandLine, CommandLine samplerCommandLine) {
        var runHelp = runCommandLine.getHelp();
        var samplerHelp = samplerCommandLine.getHelp();
        var synopsisHeading = samplerHelp.commandSpec().usageMessage().synopsisHeading();

        var sb = new StringBuilder();
        sb.append(synopsisHeading).append(samplerHelp.synopsis(synopsisHeading.length()));
        sb.append(samplerHelp.description());
        sb.append("Run command options:%n".formatted());
        sb.append(runHelp.optionList());
        sb.append("Sampler options:%n".formatted());
        sb.append(samplerHelp.parameterList());
        sb.append(samplerHelp.optionList());
        sb.append(samplerHelp.footer());
        System.out.print(sb);
    }

    private static void enableCollectErrors(CommandLine commandLine) {
        commandLine.getCommandSpec().parser().collectErrors(true);
        for (var subcommand : commandLine.getSubcommands().values()) {
            enableCollectErrors(subcommand);
        }
    }

    private static List<Exception> collectErrors(CommandLine.ParseResult parseResult) {
        var errors = new ArrayList<Exception>();
        if (parseResult.hasSubcommand()) {
            errors.addAll(collectErrors(parseResult.subcommand()));
        }
        errors.addAll(parseResult.errors());
        return errors;
    }

    private static CommandSpec createCommandSpec(SamplerPlugins samplerPlugins) {
        var commandSpec = CommandSpec.create();
        commandSpec.versionProvider(() -> new String[] {"Roadrunner, a simplistic load generator"});
        var runCommand = forAnnotatedObject(new RunCommand()).mixinStandardHelpOptions(true);

        for (var samplerPlugin : samplerPlugins.all()) {
            var samplerCmd = forAnnotatedObject(samplerPlugin.options()).mixinStandardHelpOptions(true);
            var extensionPoints = samplerPlugin.extensionPoints();
            if (!extensionPoints.isEmpty()) {
                samplerCmd.usageMessage().footer(SamplerExtensionPointsUsage.format(extensionPoints));
            }
            runCommand.addSubcommand(samplerPlugin.name(), samplerCmd);
        }

        commandSpec.mixinStandardHelpOptions(true);
        commandSpec.addSubcommand("run", runCommand);
        return commandSpec;
    }
}
