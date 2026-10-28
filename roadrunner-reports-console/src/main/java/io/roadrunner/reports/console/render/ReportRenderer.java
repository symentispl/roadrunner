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
import io.roadrunner.reports.RunManifest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the same information as the HTML report as plain text: header, configuration, summary
 * figures, and the charts as bar tables. The layout is fixed-width (under 100 columns) so output is
 * deterministic whatever the terminal size.
 */
public final class ReportRenderer {

    private static final String UNKNOWN = "unknown";
    private static final int WIDTH = 78;
    private static final int BAR = 30;

    private ReportRenderer() {}

    /** Renders without run metadata: the header says "unknown" and there is no configuration section. */
    public static String render(ReportModel m, ConsoleTheme t) {
        return render(m, null, t);
    }

    public static String render(ReportModel m, RunManifest manifest, ConsoleTheme t) {
        var nl = System.lineSeparator();
        var sb = new StringBuilder();
        var rule = "=".repeat(WIDTH);

        sb.append(rule).append(nl);
        sb.append("  Roadrunner report").append(nl);
        sb.append(String.format(
                "  sampler %s | started %s | roadrunner %s%n",
                manifest == null ? UNKNOWN : manifest.sampler(),
                manifest == null ? UNKNOWN : manifest.startedAt(),
                manifest == null ? UNKNOWN : manifest.roadrunnerVersion()));
        sb.append(rule).append(nl);

        if (manifest != null) {
            configuration(sb, manifest);
        }

        var healthColor = m.errorPercentage() == 0
                ? ConsoleTheme.GREEN
                : (m.errorPercentage() < 1.0 ? ConsoleTheme.YELLOW : ConsoleTheme.RED);
        var errorRate = t.paint(String.format(Locale.ROOT, "%.2f%%", m.errorPercentage()), healthColor);
        sb.append(String.format(Locale.ROOT, "  requests    %,d%n", m.total()));
        sb.append(String.format("  error rate  %s%n", errorRate));
        sb.append(String.format(Locale.ROOT, "  throughput  %.1f req/s%n", m.throughput()));
        sb.append(String.format(Locale.ROOT, "  p50         %d ms%n", m.p50()));
        sb.append(String.format(Locale.ROOT, "  p99         %d ms%n", m.p99()));
        sb.append(String.format(Locale.ROOT, "  duration    %.1f s%n", m.durationSeconds()));
        sb.append(nl);

        // x is 1/(1-p) in the HTML chart; here the percentile is the row label.
        sb.append("  Latency by percentile").append(nl);
        var top = Math.max(m.p50(), Math.max(m.p90(), Math.max(m.p99(), m.p999())));
        percentileRow(sb, t, "p50", m.p50(), top);
        percentileRow(sb, t, "p90", m.p90(), top);
        percentileRow(sb, t, "p99", m.p99(), top);
        percentileRow(sb, t, "p99.9", m.p999(), top);
        sb.append(nl);

        var latency = m.latencyOverTime();
        var slices = m.throughputSeries().length;
        var sliceSeconds = slices == 0 ? 0 : m.durationSeconds() / slices;
        var p99Max = 0L;
        for (var v : latency.p99()) {
            p99Max = Math.max(p99Max, v);
        }
        sb.append("  Latency over time (ms; bar is p99)").append(nl);
        sb.append(String.format("  %8s %7s %7s %7s%n", "seconds", "p50", "p90", "p99"));
        for (var i = 0; i < latency.p99().length; i++) {
            var frac = p99Max == 0 ? 0.0 : (double) latency.p99()[i] / p99Max;
            sb.append(String.format(
                    Locale.ROOT,
                    "  %8.1f %7d %7d %7d %s%n",
                    sliceSeconds * i,
                    at(latency.p50(), i),
                    at(latency.p90(), i),
                    latency.p99()[i],
                    t.bar(frac, BAR)));
        }
        sb.append(nl);

        var rate = new double[slices];
        var rateMax = 0.0;
        for (var i = 0; i < slices; i++) {
            rate[i] = sliceSeconds > 0 ? m.throughputSeries()[i] / sliceSeconds : 0;
            rateMax = Math.max(rateMax, rate[i]);
        }
        sb.append("  Throughput over time (requests/s)").append(nl);
        sb.append(String.format("  %8s %10s%n", "seconds", "requests/s"));
        for (var i = 0; i < slices; i++) {
            var frac = rateMax == 0 ? 0.0 : rate[i] / rateMax;
            sb.append(String.format(
                    Locale.ROOT, "  %8.1f %10.1f %s%n", sliceSeconds * i, rate[i], t.bar(frac, BAR)));
        }
        sb.append(nl);

        sb.append("  Status").append(nl);
        if (m.statusBreakdown().isEmpty()) {
            sb.append("  no status recorded").append(nl);
        } else {
            m.statusBreakdown().forEach((code, count) -> {
                var frac = m.total() == 0 ? 0.0 : (double) count / m.total();
                sb.append(String.format(Locale.ROOT, "  %-8s %s %,d%n", code, t.bar(frac, 20), count));
            });
        }

        sb.append(rule);
        return sb.toString();
    }

    private static long at(long[] values, int i) {
        return i < values.length ? values[i] : 0;
    }

    private static void percentileRow(StringBuilder sb, ConsoleTheme t, String label, long value, long max) {
        var frac = max == 0 ? 0.0 : (double) value / max;
        sb.append(String.format(Locale.ROOT, "  %-6s %s %d ms%n", label, t.bar(frac, BAR), value));
    }

    /** What the run was: sampler and its options, load model, environment. Empty fields are left out. */
    private static void configuration(StringBuilder sb, RunManifest m) {
        var sampler = new LinkedHashMap<String, String>();
        sampler.put("sampler", m.sampler());
        sampler.putAll(m.samplerOptions());
        sampler.put("parameters source", m.parametersSource());

        var load = new LinkedHashMap<String, String>();
        load.put("model", m.loadModel());
        load.put("concurrency", m.loadConcurrency());
        load.put("requests", m.loadRequests());
        load.put("rate", m.loadRate());
        load.put("duration", m.loadDuration());
        load.put("pause detectors", m.pauseDetectors());

        var environment = new LinkedHashMap<String, String>();
        environment.put("java", m.jvmVersion());
        environment.put("jvm args", m.jvmArgs());
        environment.put("os", "%s %s (%s)".formatted(m.osName(), m.osVersion(), m.osArch()));
        environment.put("cpus", m.hardwareCpus());
        environment.put("memory", m.hardwareMemory());

        panel(sb, "Sampler", sampler);
        panel(sb, "Load model", load);
        panel(sb, "Environment", environment);
    }

    private static void panel(StringBuilder sb, String title, Map<String, String> fields) {
        var nl = System.lineSeparator();
        sb.append("  ").append(title).append(nl);
        fields.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                sb.append(String.format("    %-18s %s%n", key, value));
            }
        });
        sb.append(nl);
    }
}
