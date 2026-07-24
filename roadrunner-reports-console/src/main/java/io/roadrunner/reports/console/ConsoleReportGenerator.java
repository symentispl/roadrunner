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
package io.roadrunner.reports.console;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.api.reports.ReportGenerator;
import io.roadrunner.console.ConsoleTheme;
import io.roadrunner.reports.console.render.ReportRenderer;
import io.roadrunner.shaded.hdrhistogram.EncodableHistogram;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import io.roadrunner.shaded.hdrhistogram.HistogramLogReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.jline.terminal.TerminalBuilder;

final class ConsoleReportGenerator implements ReportGenerator {

    private final Map<String, String> properties;

    ConsoleReportGenerator(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public void generateChart(EventReader eventReader) throws IOException {
        // Ensure the progress bar line is terminated before the report starts
        System.out.println();

        var rawLatency = Boolean.parseBoolean(properties.getOrDefault("rawLatency", "false"));
        var outputDirProp = properties.get("outputDir");
        var snapshotPath =
                outputDirProp == null ? null : Paths.get(outputDirProp).resolve("latency.hgrm");
        var useSnapshot = !rawLatency && snapshotPath != null && Files.isRegularFile(snapshotPath);

        Histogram histogram = useSnapshot ? readSnapshotHistogram(snapshotPath) : new Histogram(3);

        // Track the first and last measurement timestamps to calculate total duration
        long firstStartTime = Long.MAX_VALUE;
        var lastStopTime = 0L;

        // Track error counts
        var totalRequests = 0L;
        var errorRequests = 0L;

        for (var event : eventReader) {
            if (event instanceof SamplerResponse<?> response) {
                totalRequests++;
                if (!useSnapshot) {
                    histogram.recordValue(response.latency());
                }
                firstStartTime = Math.min(firstStartTime, response.scheduledStartTime());
                lastStopTime = Math.max(lastStopTime, response.stopTime());
                if (response instanceof SamplerResponse.Error) {
                    errorRequests++;
                }
            }
        }

        try (var terminal = TerminalBuilder.builder().dumb(true).build()) {
            var theme = ConsoleTheme.of(terminal);
            var model = ReportModel.from(
                    eventReader, histogram, firstStartTime, lastStopTime, totalRequests, errorRequests);
            System.out.println(ReportRenderer.render(model, theme));
        }
    }

    private static Histogram readSnapshotHistogram(Path snapshotPath) throws IOException {
        var combined = new Histogram(1_000L, 3_600_000_000_000L, 3);
        try (var reader = new HistogramLogReader(snapshotPath.toFile())) {
            EncodableHistogram next;
            while ((next = reader.nextIntervalHistogram()) != null) {
                if (next instanceof Histogram h) {
                    combined.add(h);
                }
            }
        }
        return combined;
    }
}
