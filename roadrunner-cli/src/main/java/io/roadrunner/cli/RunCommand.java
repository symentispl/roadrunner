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

import io.roadrunner.api.measurments.MeasurementProgress;
import io.roadrunner.api.parameters.ParameterSource;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.latency.recording.PauseDetectorKind;
import io.roadrunner.reports.RunManifest;
import java.lang.management.ManagementFactory;
import io.roadrunner.logging.LoggingFacade;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;

@Command(description = "Run a load test")
class RunCommand {

    private static final Logger LOG = LoggingFacade.getLogger(RunCommand.class);

    @ArgGroup(multiplicity = "1")
    LoadModelArgs loadModel;

    static class LoadModelArgs {
        @ArgGroup(exclusive = false, heading = "Closed-world model options:%n")
        ClosedWorldArgs closedWorld;

        @ArgGroup(exclusive = false, heading = "Open-world model options:%n")
        OpenWorldArgs openWorld;
    }

    static class ClosedWorldArgs {
        @Option(names = "-c", description = "Number of concurrent users", required = true)
        int concurrency;

        @Option(names = "-n", description = "Total number of requests to make", required = true)
        int numberOfRequests;
    }

    static class OpenWorldArgs {
        @Option(names = "--rate", description = "Target request rate (requests/second)", required = true)
        int rate;

        @Option(
                names = "--duration",
                description = "Test duration (e.g. 30s, 5m, 2h)",
                required = true,
                converter = DurationConverter.class)
        Duration duration;
    }

    @Option(names = "-s", description = "Directory to write the load test results to")
    Path outputDir;

    @Option(
            names = "-r",
            description = "Report format to generate: console (default) or html",
            converter = PrefixedMap.Converter.class)
    PrefixedMap report = new PrefixedMap("console", Map.of());

    @Option(
            names = "--pause-detectors",
            description =
                    "Correct latency measurements for pauses that would otherwise distort them: vt (virtual-thread pauses), jvm (JVM garbage-collection pauses), or vt,jvm for both. Leave unset to record raw latencies only.",
            converter = PauseDetectorKindConverter.class)
    EnumSet<PauseDetectorKind> pauseDetectors = EnumSet.noneOf(PauseDetectorKind.class);

    @Option(
            names = "--raw-latency",
            description =
                    "Always build reports from raw per-request latencies, even when pause-corrected data is available.")
    boolean rawLatency;

    @Option(
            names = "--parameters-source",
            description =
                    "Where to read per-request parameters from, in 'type:key=value' format (e.g. csv:file=data.csv)",
            converter = PrefixedMap.Converter.class)
    PrefixedMap parametersSource;

    public void run(CommandSpec samplerCommandSpec, SamplerProvider samplerProvider) throws Exception {
        if (!pauseDetectors.isEmpty() && loadModel.closedWorld != null) {
            throw new IllegalArgumentException(
                    "--pause-detectors is only supported with the open-world load model (--rate/--duration)");
        }

        var startedAt = Instant.now();

        var bootstrap = new Bootstrap().withOutputDir(outputDir).withPauseDetectorKinds(pauseDetectors);

        if (parametersSource != null) {
            var paramProviders = ParameterSourceProviders.load();
            var paramProvider = paramProviders.get(parametersSource.prefix());
            if (paramProvider == null) {
                throw new IllegalArgumentException("Unknown parameter source prefix '%s', supported types: %s"
                        .formatted(parametersSource.prefix(), paramProviders.supportedSourceTypes()));
            }
            ParameterSource source = paramProvider.create(parametersSource.parameters());
            bootstrap.withParameterSource(source);
        }

        MeasurementProgress progress;
        if (loadModel.closedWorld != null) {
            progress = new ProgressBar(100, 0, loadModel.closedWorld.numberOfRequests);
            bootstrap
                    .withClosedWorldModel(loadModel.closedWorld.concurrency, loadModel.closedWorld.numberOfRequests)
                    .withMeasurementProgress(progress);
        } else {
            progress = new TimeBasedProgressBar(loadModel.openWorld.duration);
            bootstrap
                    .withOpenWorldModel(loadModel.openWorld.rate, loadModel.openWorld.duration)
                    .withMeasurementProgress(progress);
        }

        try (var roadrunner = bootstrap.build()) {
            buildManifest(samplerCommandSpec, startedAt, Version.full()).writeTo(bootstrap.outputDir());

            LOG.debug("loading report generators");
            var chartGeneratorProviders = ChartGeneratorProviders.load();
            var reportGeneratorProvider = chartGeneratorProviders.get(report.prefix());
            if (reportGeneratorProvider == null) {
                throw new IllegalArgumentException("report generator %s unknown, supported report formats %s"
                        .formatted(report.prefix(), chartGeneratorProviders.supportedReportFormats()));
            }

            var reportConfiguration = report.parameters();
            var reportConfig = new HashMap<>(reportConfiguration);
            reportConfig.put("outputDir", bootstrap.outputDir().toString());
            reportConfig.put("rawLatency", Boolean.toString(rawLatency));

            var chartGenerator = reportGeneratorProvider.create(reportConfig);
            var measurements = roadrunner.execute(samplerProvider);
            // Release the progress bar's terminal before the report renders, otherwise the
            // report can't open its own system terminal and falls back to a dumb (ASCII) one.
            if (progress instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            chartGenerator.generateChart(measurements.samplesReader());
        }
    }

    RunManifest buildManifest(CommandSpec samplerCommandSpec, Instant startedAt, String version) {
        String loadModelName;
        var concurrency = "";
        var requests = "";
        var rate = "";
        var duration = "";
        if (loadModel.closedWorld != null) {
            loadModelName = "closed";
            concurrency = String.valueOf(loadModel.closedWorld.concurrency);
            requests = String.valueOf(loadModel.closedWorld.numberOfRequests);
        } else {
            loadModelName = "open";
            rate = String.valueOf(loadModel.openWorld.rate);
            duration = loadModel.openWorld.duration.toString();
        }

        var pauseDetectorsStr = pauseDetectors.stream().map(RunCommand::toToken).collect(Collectors.joining(","));
        var parametersSourceStr = parametersSource == null ? "" : parametersSource.toConfigString();

        var operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        var totalMemory = operatingSystem instanceof com.sun.management.OperatingSystemMXBean sunOperatingSystem
                ? String.valueOf(sunOperatingSystem.getTotalMemorySize())
                : "unknown";

        return new RunManifest(
                version,
                startedAt.toString(),
                samplerCommandSpec.name(),
                samplerOptionsConfigString(samplerCommandSpec),
                loadModelName,
                concurrency,
                requests,
                rate,
                duration,
                pauseDetectorsStr,
                parametersSourceStr,
                Runtime.version().toString(),
                String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments()),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch"),
                String.valueOf(Runtime.getRuntime().availableProcessors()),
                totalMemory);
    }

    // Generic over any sampler's options: picocli already knows every @Option/@Parameters field and
    // its current value, so no sampler module needs a custom serializer.
    private static String samplerOptionsConfigString(CommandSpec samplerCommandSpec) {
        var options = new LinkedHashMap<String, String>();
        for (var option : samplerCommandSpec.options()) {
            if (option.usageHelp() || option.versionHelp()) {
                continue;
            }
            Object value = option.getValue();
            if (value != null) {
                options.put(option.longestName(), String.valueOf(value));
            }
        }
        for (var positional : samplerCommandSpec.positionalParameters()) {
            Object value = positional.getValue();
            if (value != null) {
                options.put(positional.paramLabel(), String.valueOf(value));
            }
        }
        return new PrefixedMap(samplerCommandSpec.name(), options).toConfigString();
    }

    private static String toToken(PauseDetectorKind kind) {
        return switch (kind) {
            case VT_SCHEDULING -> "vt";
            case JVM_PAUSE -> "jvm";
        };
    }
}
