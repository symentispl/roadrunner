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
package io.roadrunner.charts.console;

import io.roadrunner.api.charts.ChartGenerator;
import io.roadrunner.api.charts.ChartGeneratorProvider;
import java.util.Properties;

public class ConsoleChartGeneratorProvider implements ChartGeneratorProvider {
    @Override
    public String name() {
        return "console";
    }

    @Override
    public ChartGenerator create(Properties properties) {
        return new ConsoleChartGenerator(properties);
    }
}
