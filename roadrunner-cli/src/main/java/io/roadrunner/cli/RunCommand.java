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

import io.roadrunner.core.Bootstrap;
import io.roadrunner.protocols.spi.ProtocolProvider;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(description = "run roadrunner load generator")
class RunCommand {

    private static final Logger LOG = LoggerFactory.getLogger(RunCommand.class);

    @Option(names = "-c", description = "Number of multiple requests to make at a time", required = true)
    int concurrency;

    @Option(names = "-n", description = "Total number of multiple requests to make", required = true)
    int numberOfRequests;

    @Option(names = "-s", description = "Output dir")
    Path outputDir;

    @Option(names = "-r", description = "Report format")
    String report;

    public void run(ProtocolProvider protocolProvider) throws Exception {
        var bootstrap = new Bootstrap();
        try (var roadrunner = bootstrap
                .withConcurrency(concurrency)
                .withRequests(numberOfRequests)
                .withMeasurementProgress(new ProgressBar(100, 0, numberOfRequests))
                .withOutputDir(outputDir)
                .build()) {
            LOG.info("loading report generators");
            var reportOpts = report;
            if (reportOpts == null) {
                reportOpts = "console";
            }
            var reportConfiguration = ReportConfiguration.parse(reportOpts);
            var chartGeneratorProviders = ChartGeneratorProviders.load();
            var reportGeneratorProvider = chartGeneratorProviders.get(reportConfiguration.reportFormat());
            if (reportGeneratorProvider == null) {
                throw new IllegalArgumentException("report generator %s unknown, supported report formats %s"
                        .formatted(
                                reportConfiguration.reportFormat(), chartGeneratorProviders.supportedReportFormats()));
            }
            var chartGenerator = reportGeneratorProvider.create(reportConfiguration.configuration());
            var measurements = roadrunner.execute(() -> protocolProvider.newProtocol());
            chartGenerator.generateChart(measurements.measurementsReader());
        }
    }
}
