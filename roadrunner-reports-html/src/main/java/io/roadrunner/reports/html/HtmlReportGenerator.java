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
package io.roadrunner.reports.html;

import static java.util.Objects.requireNonNull;

import io.roadrunner.reports.ReportGenerator;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.RunDirectory;
import io.roadrunner.reports.RunManifest;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.StringSubstitutor;

/**
 * Writes a single self-contained {@code index.html}: Chart.js and the data are inlined, so the
 * report works offline and under a strict CSP. Only aggregated series are embedded, never
 * per-request rows, so the file size does not depend on the size of the run.
 */
public class HtmlReportGenerator implements ReportGenerator {

    private static final String TEMPLATE = "/reports/html/index.html.tmpl";
    private static final String CHARTJS = "/reports/html/chartjs/chart.umd.min.js";
    private static final String UNKNOWN = "unknown";

    private final Path outputDir;

    public HtmlReportGenerator(Map<String, String> configuration) {
        outputDir = Paths.get(
                requireNonNull(configuration.get("outputDir"), "missing required outputDir configuration property"));
    }

    @Override
    public void generate(ReportModel model, PrintWriter out) throws IOException {
        var indexHtml = outputDir.resolve("index.html");
        Files.createDirectories(outputDir);

        var html = new StringSubstitutor(values(model, manifest()))
                .replace(resource(TEMPLATE))
                // String.replace is literal, unlike the substitutor: the library may contain "${".
                .replace("/*CHARTJS*/", resource(CHARTJS).replace("</script", "<\\/script"))
                .replace("/*DATA*/", data(model));
        Files.writeString(indexHtml, html);

        out.printf("Roadrunner HTML report at %s%n", indexHtml);
        out.flush();
    }

    private RunManifest manifest() throws IOException {
        try {
            return RunDirectory.of(outputDir).manifest();
        } catch (FileNotFoundException e) {
            // Runs from before manifests existed still get a report, with an unknown header.
            return null;
        }
    }

    private static Map<String, String> values(ReportModel model, RunManifest manifest) {
        var values = new HashMap<String, String>();
        values.put("sampler", escape(manifest == null ? UNKNOWN : manifest.sampler()));
        values.put("configuration", manifest == null ? "" : configuration(manifest));
        values.put("startedAt", escape(manifest == null ? UNKNOWN : manifest.startedAt()));
        values.put("version", escape(manifest == null ? UNKNOWN : manifest.roadrunnerVersion()));
        values.put("requests", String.format(Locale.ROOT, "%,d", model.total()));
        values.put("errorRate", String.format(Locale.ROOT, "%.2f%%", model.errorPercentage()));
        values.put("throughput", String.format(Locale.ROOT, "%.1f req/s", model.throughput()));
        values.put("p50", model.p50() + " ms");
        values.put("p99", model.p99() + " ms");
        values.put("duration", String.format(Locale.ROOT, "%.1f s", model.durationSeconds()));
        values.put("statusRows", statusRows(model));
        return values;
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

        return "<section class=\"config\">"
                + panel("Sampler", sampler)
                + panel("Load model", load)
                + panel("Environment", environment)
                + "</section>";
    }

    private static String panel(String title, Map<String, String> fields) {
        var rows = fields.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> "<dt>%s</dt><dd>%s</dd>".formatted(escape(e.getKey()), escape(e.getValue())))
                .collect(Collectors.joining());
        return "<div class=\"panel\"><h2>%s</h2><dl>%s</dl></div>".formatted(escape(title), rows);
    }

    private static String statusRows(ReportModel model) {
        if (model.statusBreakdown().isEmpty()) {
            return "<tr><td colspan=\"2\">no status recorded</td></tr>";
        }
        return model.statusBreakdown().entrySet().stream()
                .map(e -> "<tr><td>%s</td><td class=\"num\">%,d</td></tr>".formatted(escape(e.getKey()), e.getValue()))
                .collect(Collectors.joining());
    }

    /** The only data in the page: a few dozen numbers per series, whatever the run size. */
    private static String data(ReportModel model) {
        var slices = model.throughputSeries().length;
        var sliceSeconds = slices == 0 ? 0 : model.durationSeconds() / slices;
        var seconds = new double[slices];
        var rate = new double[slices];
        for (var i = 0; i < slices; i++) {
            seconds[i] = sliceSeconds * i;
            rate[i] = sliceSeconds > 0 ? model.throughputSeries()[i] / sliceSeconds : 0;
        }
        var latency = model.latencyOverTime();
        return String.format(
                Locale.ROOT,
                "{\"seconds\":%s,\"p50\":%s,\"p90\":%s,\"p99\":%s,\"rate\":%s,\"percentiles\":%s}",
                array(seconds),
                array(latency.p50()),
                array(latency.p90()),
                array(latency.p99()),
                array(rate),
                // x is 1/(1-p): 50% -> 2, 90% -> 10, 99% -> 100, 99.9% -> 1000.
                "[{\"x\":2,\"y\":%d},{\"x\":10,\"y\":%d},{\"x\":100,\"y\":%d},{\"x\":1000,\"y\":%d}]"
                        .formatted(model.p50(), model.p90(), model.p99(), model.p999()));
    }

    private static String array(double[] values) {
        var sb = new StringBuilder("[");
        for (var i = 0; i < values.length; i++) {
            sb.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.2f", values[i]));
        }
        return sb.append(']').toString();
    }

    private static String array(long[] values) {
        return java.util.Arrays.stream(values).mapToObj(Long::toString).collect(Collectors.joining(",", "[", "]"));
    }

    private static String escape(String value) {
        return StringEscapeUtils.escapeHtml4(value);
    }

    private static String resource(String name) throws IOException {
        try (var in = requireNonNull(HtmlReportGenerator.class.getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
