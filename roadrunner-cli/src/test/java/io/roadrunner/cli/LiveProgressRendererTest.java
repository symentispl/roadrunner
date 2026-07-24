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

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.api.measurments.ProgressSnapshot;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LiveProgressRendererTest {

    private final Locale defaultLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void showsPercentThroughputAndErrors() {
        var snapshot = new ProgressSnapshot(6300, 31, 14_000_000_000L, 8932.0, new long[] {1, 2, 3});
        var out = LiveProgressRenderer.render(0.63, snapshot, true);
        assertThat(out).contains("63%");
        assertThat(out).contains("8932");
        assertThat(out).contains("31");
    }

    @Test
    void formatsErrorPercentageWithDotUnderCommaLocale() {
        // A locale whose decimal separator is ',' (e.g. Polish) must not leak into the output.
        Locale.setDefault(Locale.forLanguageTag("pl-PL"));
        var snapshot = new ProgressSnapshot(2007, 2007, 5_000_000_000L, 364.0, new long[] {1});
        var out = LiveProgressRenderer.render(1.0, snapshot, true);
        assertThat(out).contains("(100.00%)");
        assertThat(out).doesNotContain("100,00");
    }
}
