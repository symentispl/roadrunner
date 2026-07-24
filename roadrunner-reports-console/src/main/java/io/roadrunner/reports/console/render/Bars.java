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

final class Bars {
    private static final String UNICODE_SPARK = "▁▂▃▄▅▆▇█";
    private static final String ASCII_SPARK = ".:-=+*#";

    private Bars() {}

    static String horizontal(double fraction, int cells, boolean unicode) {
        var f = Math.max(0.0, Math.min(1.0, fraction));
        var full = (int) Math.round(f * cells);
        var fullChar = unicode ? '█' : '#';
        var emptyChar = unicode ? '░' : '.';
        var sb = new StringBuilder(cells);
        for (var i = 0; i < cells; i++) {
            sb.append(i < full ? fullChar : emptyChar);
        }
        return sb.toString();
    }

    static String sparkline(long[] values, boolean unicode) {
        var ramp = unicode ? UNICODE_SPARK : ASCII_SPARK;
        if (values.length == 0) {
            return "";
        }
        var max = 0L;
        for (var v : values) {
            max = Math.max(max, v);
        }
        var sb = new StringBuilder(values.length);
        for (var v : values) {
            var idx = max == 0 ? 0 : (int) Math.round((double) (ramp.length() - 1) * v / max);
            sb.append(ramp.charAt(idx));
        }
        return sb.toString();
    }
}
