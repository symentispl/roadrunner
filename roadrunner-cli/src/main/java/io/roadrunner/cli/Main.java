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
import io.roadrunner.options.CliOptionsBuilder;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        LOG.info("loading protocol providers");

        var cliOptionsBuilder = new CliOptionsBuilder();
        var optionsBinding = cliOptionsBuilder.from(RoadrunnerOptions.class);
        var roadrunnerOptions = optionsBinding.newInstance(args);

        var bootstrap = new Bootstrap();
        var roadrunner = bootstrap
                .withConcurrency(roadrunnerOptions.concurrency())
                .withRequests(roadrunnerOptions.numberOfRequests())
                .withMeasurementProgress(new ProgressBar(100, 0, roadrunnerOptions.numberOfRequests()))
                .withOutputDir(roadrunnerOptions.outputDir())
                .build();

        var remainingArgs = optionsBinding.args();
        var protocolName = remainingArgs[0];
        var protocolArgs = new String[remainingArgs.length - 1];
        System.arraycopy(remainingArgs, 1, protocolArgs, 0, protocolArgs.length);

        var protocolProviders = ProtocolProviders.load();

        var protocol = protocolProviders.get(protocolName);
        var requestOptions = protocol.requestOptions(protocolArgs);
        try {
            var measurements = roadrunner.execute(() -> protocol.request(requestOptions));

            var chartGeneratorProviders = ChartGeneratorProviders.load();

            var chartGeneratorProvider = chartGeneratorProviders.get("console");
            var chartGenerator = chartGeneratorProvider.create(new Properties());

            chartGenerator.generateChart(measurements.measurementsReader());
            //
            //            new HtmlChartGenerator().generateChart(measurements.measurementsReader());
            //            LOG.info(
            //                    "results at {}",
            //                    bootstrap.outputDir().resolve("index.html").toAbsolutePath().toUri());
            //            new ConsoleChartGenerator().generateChart(measurements.measurementsReader());
        } finally {
            protocolProviders.close();
        }
    }
}
