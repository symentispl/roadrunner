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
import java.io.IOException;
import org.jline.terminal.TerminalBuilder;

final class ConsoleReportGenerator implements ReportGenerator {

    @Override
    public void generate(ReportModel model) throws IOException {
        // Ensure the progress bar line is terminated before the report starts
        System.out.println();

        try (var terminal = TerminalBuilder.builder().dumb(true).build()) {
            System.out.println(ReportRenderer.render(model, ConsoleTheme.of(terminal)));
        }
    }
}
