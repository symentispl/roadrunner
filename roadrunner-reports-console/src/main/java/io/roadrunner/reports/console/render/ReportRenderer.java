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

import io.roadrunner.console.ConsoleTheme;
import io.roadrunner.reports.ReportModel;
import java.util.Locale;

public final class ReportRenderer {

    private ReportRenderer() {}

    public static String render(ReportModel m, ConsoleTheme t) {
        var nl = System.lineSeparator();
        var sb = new StringBuilder();
        var rule = "=".repeat(Math.min(t.width(), 60));

        sb.append(rule).append(nl);
        sb.append("  Load test summary").append(nl);
        sb.append(rule).append(nl);

        var healthColor = m.errorPercentage() == 0
                ? ConsoleTheme.GREEN
                : (m.errorPercentage() < 1.0 ? ConsoleTheme.YELLOW : ConsoleTheme.RED);
        var health =
                t.paint(String.format(Locale.ROOT, "%d errors (%.2f%%)", m.errors(), m.errorPercentage()), healthColor);

        sb.append(String.format("  requests   %d total   %d ok   %s%n", m.total(), m.success(), health));
        sb.append(String.format(
                Locale.ROOT, "  duration   %.2f s   throughput %.2f req/s%n", m.durationSeconds(), m.throughput()));
        sb.append(nl);

        sb.append("  latency (ms)").append(nl);
        appendLatencyRow(sb, t, "min ", m.minMs(), m.maxMs());
        appendLatencyRow(sb, t, "mean", m.meanMs(), m.maxMs());
        appendLatencyRow(sb, t, "p50 ", m.p50(), m.maxMs());
        appendLatencyRow(sb, t, "p90 ", m.p90(), m.maxMs());
        appendLatencyRow(sb, t, "p99 ", m.p99(), m.maxMs());
        appendLatencyRow(sb, t, "p999", m.p999(), m.maxMs());
        appendLatencyRow(sb, t, "max ", m.maxMs(), m.maxMs());
        sb.append(nl);

        sb.append("  distribution ").append(t.sparkline(m.latencyBuckets())).append(nl);
        sb.append("  throughput   ").append(t.sparkline(m.throughputSeries())).append(nl);

        if (!m.statusBreakdown().isEmpty()) {
            sb.append(nl).append("  status").append(nl);
            m.statusBreakdown().forEach((code, count) -> {
                var frac = m.total() == 0 ? 0.0 : (double) count / m.total();
                sb.append(String.format("  %-5s %s %d%n", code, t.bar(frac, 20), count));
            });
        }

        sb.append(rule);
        return sb.toString();
    }

    private static void appendLatencyRow(StringBuilder sb, ConsoleTheme t, String label, long value, long max) {
        var frac = max == 0 ? 0.0 : (double) value / max;
        sb.append(String.format("  %s %s %d%n", label, t.bar(frac, 30), value));
    }
}
