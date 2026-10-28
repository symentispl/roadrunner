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

import io.roadrunner.console.ConsoleTheme;
import io.roadrunner.reports.ReportGenerator;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.console.render.ReportRenderer;
import io.roadrunner.reports.RunDirectory;
import io.roadrunner.reports.RunManifest;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.jline.terminal.TerminalBuilder;

final class ConsoleReportGenerator implements ReportGenerator {

    private final Path outputDir;

    ConsoleReportGenerator(Map<String, String> configuration) {
        var dir = configuration.get("outputDir");
        outputDir = dir == null ? null : Paths.get(dir);
    }

    @Override
    public void generate(ReportModel model, PrintWriter out) throws IOException {
        // Ensure the progress bar line is terminated before the report starts
        out.println();

        try (var terminal = TerminalBuilder.builder().dumb(true).build()) {
            out.println(ReportRenderer.render(model, manifest(), ConsoleTheme.of(terminal)));
        }
    }

    private RunManifest manifest() throws IOException {
        if (outputDir == null) {
            return null;
        }
        try {
            return RunDirectory.of(outputDir).manifest();
        } catch (FileNotFoundException e) {
            // Runs from before manifests existed still get a report, with an unknown header.
            return null;
        }
    }
}
