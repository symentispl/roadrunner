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
import io.roadrunner.reports.ComparisonModel;
import io.roadrunner.reports.console.render.ComparisonRenderer;
import io.roadrunner.reports.spi.ReportComparisonGenerator;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

final class ConsoleReportComparisonGenerator implements ReportComparisonGenerator {

    private final ConsoleTheme theme;

    ConsoleReportComparisonGenerator(Map<String, String> configuration) {
        theme = ConsoleOptions.theme(configuration);
    }

    @Override
    public void generate(ComparisonModel model, PrintWriter out) throws IOException {
        out.println(ComparisonRenderer.render(model, theme));
    }
}
