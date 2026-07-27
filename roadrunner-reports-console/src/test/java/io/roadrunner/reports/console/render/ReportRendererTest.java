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
package io.roadrunner.reports.console.render;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.console.ConsoleTheme;
import io.roadrunner.reports.console.ReportModel;
import java.util.Map;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

class ReportRendererTest {

    private static ConsoleTheme dumbTheme() throws Exception {
        var terminal = TerminalBuilder.builder().dumb(true).build();
        return ConsoleTheme.of(terminal);
    }

    private static ReportModel sampleModel() {
        return new ReportModel(
                100,
                95,
                5,
                10.0,
                10.0,
                5.0,
                1,
                8,
                120,
                5,
                15,
                90,
                118,
                new long[] {50, 30, 15, 5},
                new long[] {8, 10, 12, 9},
                Map.of("200", 95L, "500", 5L));
    }

    @Test
    void rendersKeyNumbersInPlainText() throws Exception {
        var out = ReportRenderer.render(sampleModel(), dumbTheme());
        assertThat(out).contains("100"); // total
        assertThat(out).contains("95"); // success
        assertThat(out).contains("5.00%"); // error percentage
        assertThat(out).contains("200"); // status code
        assertThat(out).doesNotContain("["); // no ANSI escapes in dumb terminal
    }
}
