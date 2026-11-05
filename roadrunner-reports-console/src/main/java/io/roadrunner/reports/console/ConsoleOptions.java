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
import java.util.Map;

/**
 * What the console report options ({@code -r console:nocolors=true}, {@code -r console:ascii=true}) mean. Colour is
 * also off when {@code NO_COLOR} is set or stdout is not a terminal, so CI logs and redirected files stay clean.
 */
final class ConsoleOptions {

    static final int WIDTH = 78;

    private ConsoleOptions() {}

    static ConsoleTheme theme(Map<String, String> configuration) {
        var color = !flag(configuration, "nocolors") && System.getenv("NO_COLOR") == null && isTerminal();
        return ConsoleTheme.of(color, !flag(configuration, "ascii"), WIDTH);
    }

    private static boolean flag(Map<String, String> configuration, String name) {
        // "-r console:nocolors" with no value is a request for it, too
        var value = configuration.get(name);
        return value != null && !value.equalsIgnoreCase("false");
    }

    private static boolean isTerminal() {
        var console = System.console();
        return console != null && console.isTerminal();
    }
}
