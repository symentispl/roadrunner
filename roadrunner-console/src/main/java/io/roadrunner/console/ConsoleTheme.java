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

import java.util.List;
import java.util.regex.Pattern;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

public final class ConsoleTheme {
    public static final int GREEN = AttributedStyle.GREEN;
    public static final int YELLOW = AttributedStyle.YELLOW;
    public static final int RED = AttributedStyle.RED;
    public static final int CYAN = AttributedStyle.CYAN;
    public static final int BLUE = AttributedStyle.BLUE;
    public static final int MAGENTA = AttributedStyle.MAGENTA;
    public static final int WHITE = AttributedStyle.WHITE;

    /** Status glyphs; plain ASCII stand-ins when the theme is not unicode. */
    public enum Icon {
        OK("✔", "+"),
        FAIL("✘", "x"),
        WARN("⚠", "!"),
        INFO("ℹ", "i");

        private final String unicode;
        private final String ascii;

        Icon(String unicode, String ascii) {
            this.unicode = unicode;
            this.ascii = ascii;
        }
    }

    private static final Pattern ANSI = Pattern.compile("\u001b\\[[0-9;]*m");

    private final Terminal terminal; // null: emit plain ANSI escapes
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

    /** A theme that needs no terminal: colour and charset are chosen by the caller, width is fixed. */
    public static ConsoleTheme of(boolean color, boolean unicode, int width) {
        return new ConsoleTheme(null, color, unicode, width);
    }

    public boolean color() {
        return color;
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
        if (terminal == null) {
            return "\u001b[3" + ansiColor + "m" + text + "\u001b[0m";
        }
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(ansiColor))
                .append(text)
                .toAnsi(terminal);
    }

    public String bold(String text) {
        return color ? "\u001b[1m" + text + "\u001b[0m" : text;
    }

    public String icon(Icon icon) {
        return unicode ? icon.unicode : icon.ascii;
    }

    /** A coloured status glyph: green OK, red FAIL, yellow WARN, cyan INFO. */
    public String coloredIcon(Icon icon) {
        var c =
                switch (icon) {
                    case OK -> GREEN;
                    case FAIL -> RED;
                    case WARN -> YELLOW;
                    case INFO -> CYAN;
                };
        return paint(icon(icon), c);
    }

    /**
     * Soft-wraps plain text to at most {@code width} columns: at spaces where possible, mid-word (after a {@code /},
     * {@code ,} or {@code =} when one is near) when a single word is wider than a line.
     */
    public static List<String> wrap(String text, int width) {
        var lines = new java.util.ArrayList<String>();
        var line = new StringBuilder();
        for (var word : text.split(" +")) {
            while (word.length() > width) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                var cut = width;
                for (var i = width - 1; i > width / 2; i--) {
                    if ("/,=:;".indexOf(word.charAt(i - 1)) >= 0) {
                        cut = i;
                        break;
                    }
                }
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() > 0 ? " " : "").append(word);
        }
        lines.add(line.toString());
        return lines;
    }

    /** Length as seen on screen: ANSI colour escapes take no room. */
    public static int visibleLength(String text) {
        return ANSI.matcher(text).replaceAll("").length();
    }

    /**
     * A Turbo Vision style frame: double-line border (plain {@code + = | } characters when not unicode), the title
     * set into the top edge, every line padded to {@code width} columns.
     */
    public String box(String title, List<String> lines, int width) {
        var h = unicode ? "═" : "=";
        var v = unicode ? "║" : "|";
        var inner = width - 2;
        var sb = new StringBuilder();
        var head = title.isEmpty() ? "" : " " + bold(paint(title, CYAN)) + " ";
        var left = title.isEmpty() ? 0 : 1;
        var fill = Math.max(0, inner - left - visibleLength(head));
        sb.append(unicode ? "╔" : "+")
                .append(h.repeat(left))
                .append(head)
                .append(h.repeat(fill))
                .append(unicode ? "╗" : "+")
                .append('\n');
        var wrapped = new java.util.ArrayList<String>();
        for (var line : lines) {
            // a line too wide for the frame is wrapped by visible width; colour is dropped from wrapped lines
            if (visibleLength(line) > inner - 2) {
                wrapped.addAll(wrap(ANSI.matcher(line).replaceAll(""), inner - 2));
            } else {
                wrapped.add(line);
            }
        }
        for (var line : wrapped) {
            var content = " " + line;
            sb.append(v)
                    .append(content)
                    .append(" ".repeat(Math.max(0, inner - visibleLength(content))))
                    .append(v)
                    .append('\n');
        }
        sb.append(unicode ? "╚" : "+").append(h.repeat(inner)).append(unicode ? "╝" : "+");
        return sb.toString();
    }

    public String bar(double fraction, int cells) {
        return Bars.horizontal(fraction, cells, unicode);
    }

    public String sparkline(long[] values) {
        return Bars.sparkline(values, unicode);
    }
}
