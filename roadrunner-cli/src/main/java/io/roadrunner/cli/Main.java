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

import static java.lang.Math.round;
import static java.time.Duration.ofNanos;

import io.roadrunner.api.measurments.Measurements;
import io.roadrunner.charts.ChartGenerator;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.options.CliOptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

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
                .build();

        var remainingArgs = optionsBinding.args();
        var protocolName = remainingArgs[0];
        var protocolArgs = new String[remainingArgs.length - 1];
        System.arraycopy(remainingArgs, 1, protocolArgs, 0, protocolArgs.length);

        var protocolProviders = ProtocolProviders.load();

        var protocol = protocolProviders.get(protocolName);
        var requestOptions = protocol.requestOptions(protocolArgs);
        var request = protocol.request(requestOptions);
        try {
            var measurements = roadrunner.execute(() -> request::execute);
            prettyPrintHistogramSummary(measurements);

            if (roadrunnerOptions.chartsDir() != null) {
                new ChartGenerator().generateChart(roadrunnerOptions.chartsDir(), Paths.get("output.csv"));
            }
        } finally {
            protocolProviders.close();
        }
    }

    public static void prettyPrintHistogramSummary(Measurements measurements) {
        System.out.println("HdrHistogram Summary:");
        System.out.println("=====================");
        System.out.printf("Total Count    : %d%n", measurements.totalCount());
        System.out.printf("Min Value (ms) : %d ms%n", getMillis(measurements.minValue()));
        System.out.printf("Max Value (ms) : %d ms%n", getMillis(measurements.maxValue()));
        System.out.printf("Mean Value (ms): %d ms%n", getMillis(measurements.mean()));
        System.out.printf("50th Percentile: %d ms%n", getMillis(measurements.p50()));
        System.out.printf("90th Percentile: %d ms%n", getMillis(measurements.p90()));
        System.out.printf("99th Percentile: %d ms%n", getMillis(measurements.p99()));
        System.out.printf("99.9th Percentile: %d ms%n", getMillis(measurements.p999()));
        System.out.println("=====================");
    }

    private static long getMillis(double value) {
        return ofNanos(round(value)).toMillis();
    }
}
