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
package io.roadrunner.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleThemeTest {

    @Test
    void visibleLengthIgnoresColourEscapes() {
        var theme = ConsoleTheme.of(true, true, 40);
        assertThat(ConsoleTheme.visibleLength(theme.paint("abc", ConsoleTheme.RED))).isEqualTo(3);
    }

    @Test
    void paintIsNoOpWithoutColour() {
        assertThat(ConsoleTheme.of(false, true, 40).paint("abc", ConsoleTheme.RED)).isEqualTo("abc");
    }

    @Test
    void boxIsAlignedInUnicodeAndAscii() {
        for (var unicode : new boolean[] {true, false}) {
            var theme = ConsoleTheme.of(true, unicode, 30);
            var box = theme.box("Title", List.of("one", theme.paint("two", ConsoleTheme.GREEN)));
            assertThat(box.lines().map(ConsoleTheme::visibleLength).distinct()).containsExactly(30);
        }
    }

    @Test
    void asciiBoxUsesPlainCharacters() {
        var box = ConsoleTheme.of(false, false, 20).box("T", List.of("x"));
        assertThat(box).startsWith("+= T =").contains("| x").endsWith("+");
    }

    @Test
    void iconsFallBackToAscii() {
        assertThat(ConsoleTheme.of(false, true, 20).icon(ConsoleTheme.Icon.OK)).isEqualTo("✔");
        assertThat(ConsoleTheme.of(false, false, 20).icon(ConsoleTheme.Icon.OK)).isEqualTo("+");
    }

    @Test
    void wrapBreaksAtSpacesAndSplitsOverlongWords() {
        assertThat(ConsoleTheme.wrap("aaa bbb ccc", 7)).containsExactly("aaa bbb", "ccc");
        var lines = ConsoleTheme.wrap("-Dx=1 /very/long/path/that/does/not/fit/anywhere", 12);
        assertThat(lines).allSatisfy(l -> assertThat(l.length()).isLessThanOrEqualTo(12));
        assertThat(String.join("", lines).replace(" ", "")).isEqualTo("-Dx=1/very/long/path/that/does/not/fit/anywhere");
    }

    @Test
    void boxWrapsLinesThatAreTooWide() {
        var box = ConsoleTheme.of(false, true, 20).box("T", List.of("one two three four five six"));
        assertThat(box.lines().map(String::length).distinct()).containsExactly(20);
    }
}
