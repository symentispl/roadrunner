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

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public final class ConsoleTheme {
    public static final int GREEN = AttributedStyle.GREEN;
    public static final int YELLOW = AttributedStyle.YELLOW;
    public static final int RED = AttributedStyle.RED;
    public static final int CYAN = AttributedStyle.CYAN;

    private final Terminal terminal;
    private final boolean color;
    private final boolean unicode;
    private final int width;

    private ConsoleTheme(Terminal terminal, boolean color, boolean unicode, int width) {
        this.terminal = terminal;
        this.color = color;
        this.unicode = unicode;
        this.width = width;
    }

    public static ConsoleTheme of(Terminal terminal) {
        var noColor = System.getenv("NO_COLOR") != null;
        var type = terminal.getType() == null ? "" : terminal.getType();
        var dumb = type.contains("dumb");
        var color = !noColor && !dumb;
        var unicode = !dumb;
        var w = terminal.getWidth();
        var width = w <= 0 ? 80 : Math.max(40, Math.min(120, w));
        return new ConsoleTheme(terminal, color, unicode, width);
    }

    public boolean unicode() {
        return unicode;
    }

    public int width() {
        return width;
    }

    public String paint(String text, int ansiColor) {
        if (!color) {
            return text;
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(ansiColor))
                .append(text)
                .toAnsi(terminal);
    }

    public String bar(double fraction, int cells) {
        return Bars.horizontal(fraction, cells, unicode);
    }

    public String sparkline(long[] values) {
        return Bars.sparkline(values, unicode);
    }
}
