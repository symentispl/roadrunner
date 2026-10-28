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

import static java.util.Objects.requireNonNull;

import io.roadrunner.console.ConsoleTheme;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.RunManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.text.StringSubstitutor;

/**
 * Renders the same information as the HTML report as plain text: header, configuration, summary
 * figures, and the charts as bar tables. The layout lives in {@code report.txt.tmpl}; this class
 * only computes the values and the repeated row blocks. It is fixed-width (under 100 columns) so
 * output is deterministic whatever the terminal size.
 */
public final class ReportRenderer {

    private static final String TEMPLATE = "/reports/console/report.txt.tmpl";
    private static final String UNKNOWN = "unknown";
    private static final int BAR = 30;

    private ReportRenderer() {}

    /** Renders without run metadata: the header says "unknown" and there is no configuration section. */
    public static String render(ReportModel m, ConsoleTheme t) {
        return render(m, null, t);
    }

    public static String render(ReportModel m, RunManifest manifest, ConsoleTheme t) {
        var healthColor = m.errorPercentage() == 0
                ? ConsoleTheme.GREEN
                : (m.errorPercentage() < 1.0 ? ConsoleTheme.YELLOW : ConsoleTheme.RED);
        var slices = m.throughputSeries().length;
        var sliceSeconds = slices == 0 ? 0 : m.durationSeconds() / slices;

        var values = new HashMap<String, String>();
        values.put("sampler", manifest == null ? UNKNOWN : manifest.sampler());
        values.put("startedAt", manifest == null ? UNKNOWN : manifest.startedAt());
        values.put("version", manifest == null ? UNKNOWN : manifest.roadrunnerVersion());
        values.put("configuration", manifest == null ? "" : configuration(manifest));
        values.put("requests", String.format(Locale.ROOT, "%,d", m.total()));
        values.put("errorRate", t.paint(String.format(Locale.ROOT, "%.2f%%", m.errorPercentage()), healthColor));
        values.put("throughput", String.format(Locale.ROOT, "%.1f req/s", m.throughput()));
        values.put("p50", m.p50() + " ms");
        values.put("p99", m.p99() + " ms");
        values.put("duration", String.format(Locale.ROOT, "%.1f s", m.durationSeconds()));
        values.put("percentileRows", percentileRows(m, t));
        values.put("latencyRows", latencyRows(m, sliceSeconds, t));
        values.put("throughputRows", throughputRows(m, slices, sliceSeconds, t));
        values.put("statusRows", statusRows(m, t));

        // No substitution inside values: sampler options and status codes may contain "${...}".
        var substitutor = new StringSubstitutor(values);
        substitutor.setDisableSubstitutionInValues(true);
        return substitutor.replace(template());
    }

    private static String percentileRows(ReportModel m, ConsoleTheme t) {
        var top = Math.max(m.p50(), Math.max(m.p90(), Math.max(m.p99(), m.p999())));
        return String.join(
                "\n",
                percentileRow(t, "p50", m.p50(), top),
                percentileRow(t, "p90", m.p90(), top),
                percentileRow(t, "p99", m.p99(), top),
                percentileRow(t, "p99.9", m.p999(), top));
    }

    private static String percentileRow(ConsoleTheme t, String label, long value, long max) {
        var frac = max == 0 ? 0.0 : (double) value / max;
        return String.format(Locale.ROOT, "  %-6s %s %d ms", label, t.bar(frac, BAR), value);
    }

    private static String latencyRows(ReportModel m, double sliceSeconds, ConsoleTheme t) {
        var latency = m.latencyOverTime();
        var p99Max = 0L;
        for (var v : latency.p99()) {
            p99Max = Math.max(p99Max, v);
        }
        var max = p99Max;
        return IntStream.range(0, latency.p99().length)
                .mapToObj(i -> String.format(
                        Locale.ROOT,
                        "  %7.1f %7d %7d %7d %s",
                        sliceSeconds * i,
                        at(latency.p50(), i),
                        at(latency.p90(), i),
                        latency.p99()[i],
                        t.bar(max == 0 ? 0.0 : (double) latency.p99()[i] / max, BAR)))
                .collect(Collectors.joining("\n"));
    }

    private static String throughputRows(ReportModel m, int slices, double sliceSeconds, ConsoleTheme t) {
        var rate = new double[slices];
        var rateMax = 0.0;
        for (var i = 0; i < slices; i++) {
            rate[i] = sliceSeconds > 0 ? m.throughputSeries()[i] / sliceSeconds : 0;
            rateMax = Math.max(rateMax, rate[i]);
        }
        var max = rateMax;
        return IntStream.range(0, slices)
                .mapToObj(i -> String.format(
                        Locale.ROOT,
                        "  %7.1f %10.1f %s",
                        sliceSeconds * i,
                        rate[i],
                        t.bar(max == 0 ? 0.0 : rate[i] / max, BAR)))
                .collect(Collectors.joining("\n"));
    }

    private static String statusRows(ReportModel m, ConsoleTheme t) {
        if (m.statusBreakdown().isEmpty()) {
            return "  no status recorded";
        }
        return m.statusBreakdown().entrySet().stream()
                .map(e -> String.format(
                        Locale.ROOT,
                        "  %-8s %s %,d",
                        e.getKey(),
                        t.bar(m.total() == 0 ? 0.0 : (double) e.getValue() / m.total(), 20),
                        e.getValue()))
                .collect(Collectors.joining("\n"));
    }

    private static long at(long[] values, int i) {
        return i < values.length ? values[i] : 0;
    }

    /** What the run was: sampler and its options, load model, environment. Empty fields are left out. */
    private static String configuration(RunManifest m) {
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

        return panel("Sampler", sampler) + panel("Load model", load) + panel("Environment", environment);
    }

    private static String panel(String title, Map<String, String> fields) {
        var rows = fields.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> String.format("    %-18s %s\n", e.getKey(), e.getValue()))
                .collect(Collectors.joining());
        return "  " + title + "\n" + rows + "\n";
    }

    private static String template() {
        try (var in = requireNonNull(ReportRenderer.class.getResourceAsStream(TEMPLATE), TEMPLATE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
