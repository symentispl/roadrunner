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

import io.roadrunner.logging.LoggingFacade;
import io.roadrunner.samplers.spi.SamplerOptions;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Model.OptionSpec;

public class Main {

    static void main(String[] args) {
        // loggers are static fields, so -X has to be honoured before the first class holding one is loaded,
        // which happens well before picocli gets to parse it. Only the args ahead of the subcommand are ours,
        // -X after it belongs to the sampler (the ab sampler uses it for its proxy server).
        var subcommandAt = List.of(args).indexOf("run");
        var globalArgs = List.of(subcommandAt < 0 ? args : Arrays.copyOfRange(args, 0, subcommandAt));
        var stackTraces = globalArgs.contains("-X");
        if (stackTraces) {
            LoggingFacade.disableOmitThrowables();
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> report(throwable, stackTraces));

        try {
            execute(args);
        } catch (Exception e) {
            report(e, stackTraces);
            System.exit(1);
        }
    }

    // without -X a failure is a single line, the stack trace of a bad option or a broken connection is noise
    private static void report(Throwable throwable, boolean stackTraces) {
        if (stackTraces) {
            throwable.printStackTrace();
            return;
        }

        var messages = new ArrayList<String>();
        // capped, a deeply wrapped exception says all it has to say in the first few causes
        for (var cause = throwable; cause != null && messages.size() < 4; cause = cause.getCause()) {
            messages.add(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        }
        System.err.println("error: " + String.join(": ", messages));
        System.err.println("run with -X to see the full stack trace");
    }

    private static void execute(String[] args) throws Exception {
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

            var samplerSubCmd = subcommand != null ? subcommand.subcommand() : null;
            if (samplerSubCmd != null && samplerSubCmd.isUsageHelpRequested()) {
                printSamplerUsage(
                        subcommand.commandSpec().commandLine(),
                        samplerSubCmd.commandSpec().commandLine());
                return;
            }

            var errors = collectErrors(parseResult);
            if (!errors.isEmpty()) {
                throw errors.getFirst();
            }

            if (subcommand != null
                    && subcommand.commandSpec().userObject() instanceof RunCommand runCommand
                    && samplerSubCmd != null
                    && samplerSubCmd.commandSpec().userObject() instanceof SamplerOptions samplerOptions) {
                try (var samplerProvider = samplerOptions.samplerProvider()) {
                    runCommand.run(samplerProvider);
                }
            }
        }
    }

    // combines run + sampler + expression-syntax help into one printout
    private static void printSamplerUsage(CommandLine runCommandLine, CommandLine samplerCommandLine) {
        var runHelp = runCommandLine.getHelp();
        var samplerHelp = samplerCommandLine.getHelp();
        var synopsisHeading = samplerHelp.commandSpec().usageMessage().synopsisHeading();

        System.out.print(synopsisHeading + samplerHelp.synopsis(synopsisHeading.length()));
        System.out.print(samplerHelp.description());
        System.out.printf("Run command options:%n");
        System.out.print(runHelp.optionList());
        System.out.printf("Sampler options:%n");
        System.out.print(samplerHelp.parameterList());
        System.out.print(optionListExcludingHelpOptions(samplerHelp));
        System.out.print(samplerHelp.footer());
    }

    // -h/-V are already shown under "Run command options:"; skip them here to avoid listing them twice.
    private static String optionListExcludingHelpOptions(CommandLine.Help help) {
        var groupedOptions = help.optionSectionGroups().stream()
                .flatMap(g -> g.allOptionsNested().stream())
                .toList();
        var options = help.commandSpec().options().stream()
                .filter(o -> !groupedOptions.contains(o) && !o.usageHelp() && !o.versionHelp())
                .toList();
        return help.optionListExcludingGroups(options) + help.optionListGroupSections();
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
        // top level only, the ab sampler already uses -X for its proxy server
        commandSpec.addOption(OptionSpec.builder("-X")
                .description("Log full exception stack traces instead of just the exception message")
                .type(boolean.class)
                .build());
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
