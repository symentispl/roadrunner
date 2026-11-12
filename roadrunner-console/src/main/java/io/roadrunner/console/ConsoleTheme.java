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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ConsoleTheme {
    public static final int GREEN = 2;
    public static final int YELLOW = 3;
    public static final int RED = 1;
    public static final int CYAN = 6;

    /** Status glyphs; plain ASCII stand-ins when the theme is not unicode. */
    public enum Icon {
        OK("✔", "+"),
        FAIL("✘", "x"),
        WARN("⚠", "!");

        private final String unicode;
        private final String ascii;

        Icon(String unicode, String ascii) {
            this.unicode = unicode;
            this.ascii = ascii;
        }
    }

    private static final Pattern ANSI = Pattern.compile("\u001b\\[[0-9;]*m");

    private final boolean color;
    private final boolean unicode;
    private final int width;

    private ConsoleTheme(boolean color, boolean unicode, int width) {
        this.color = color;
        this.unicode = unicode;
        this.width = width;
    }

    /** Colour and charset are chosen by the caller, width is fixed: output never depends on a terminal. */
    public static ConsoleTheme of(boolean color, boolean unicode, int width) {
        return new ConsoleTheme(color, unicode, width);
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
        return "\u001b[3" + ansiColor + "m" + text + "\u001b[0m";
    }

    public String bold(String text) {
        return color ? "\u001b[1m" + text + "\u001b[0m" : text;
    }

    public String icon(Icon icon) {
        return unicode ? icon.unicode : icon.ascii;
    }

    /** A coloured status glyph: green OK, red FAIL and yellow WARN. */
    public String coloredIcon(Icon icon) {
        var c =
                switch (icon) {
                    case OK -> GREEN;
                    case FAIL -> RED;
                    case WARN -> YELLOW;
                };
        return paint(icon(icon), c);
    }

    /**
     * Soft-wraps plain text to at most {@code width} columns: at spaces where possible, mid-word (after a {@code /},
     * {@code ,} or {@code =} when one is near) when a single word is wider than a line.
     */
    public static List<String> wrap(String text, int width) {
        var lines = new ArrayList<String>();
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
        return stripAnsi(text).length();
    }

    public static String stripAnsi(String text) {
        return ANSI.matcher(text).replaceAll("");
    }

    /**
     * A Turbo Vision style frame: double-line border (plain {@code + = | } characters when not unicode), the title
     * set into the top edge, every line padded to the theme's width.
     */
    public String box(String title, List<String> lines) {
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
        var wrapped = new ArrayList<String>();
        for (var line : lines) {
            // a line too wide for the frame is wrapped by visible width; colour is dropped from wrapped lines
            if (visibleLength(line) > inner - 2) {
                wrapped.addAll(wrap(stripAnsi(line), inner - 2));
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
