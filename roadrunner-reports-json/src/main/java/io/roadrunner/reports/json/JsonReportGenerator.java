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
package io.roadrunner.reports.json;

import static java.util.Objects.requireNonNull;

import io.roadrunner.reports.ReportGenerator;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.RunDirectory;
import io.roadrunner.reports.RunManifest;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

/**
 * Writes {@code report.json} into {@code outputDir}: the same information as the HTML report, in
 * machine-friendly types. Only aggregated series are written, never per-request rows.
 *
 * <p>Schema (version 1). Fields marked {@code ?} may be {@code null}:
 *
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "sampler": string?,             // null without run.properties
 *   "startedAt": string?,           // ISO-8601 instant
 *   "roadrunnerVersion": string?,
 *   "configuration": null | {       // null without run.properties
 *     "sampler": { "name": string?, "options": {string: string}, "parametersSource": string? },
 *     "loadModel": { "model": string?, "concurrency": number?, "requests": number?,
 *                    "rate": number?, "duration": string? (ISO-8601 as recorded),
 *                    "pauseDetectors": [string] },
 *     "environment": { "java": string?, "jvmArgs": string?,
 *                      "os": { "name": string?, "version": string?, "arch": string? },
 *                      "cpus": number?, "memory": string? }
 *   },
 *   "summary": { "requests", "errors", "errorRatePercent", "throughputPerSecond",
 *                "durationSeconds", "p50Ms", "p90Ms", "p99Ms", "p999Ms",
 *                "minMs", "meanMs", "maxMs" },      // all numbers
 *   "latencyPercentiles": [ { "percentile": 50, "latencyMs": n }, ... 90, 99, 99.9 ],
 *   "latencyOverTime": { "sliceSeconds": n, "seconds": [n], "p50Ms": [n], "p90Ms": [n], "p99Ms": [n] },
 *   "throughputOverTime": { "sliceSeconds": n, "seconds": [n], "requestsPerSecond": [n] },
 *   "status": { "<status>": count, ... }
 * }
 * </pre>
 *
 * Series entries are indexed by time slice; {@code seconds[i]} is the slice start, counted from run start.
 */
public class JsonReportGenerator implements ReportGenerator {

    static final int SCHEMA_VERSION = 1;

    private final Path outputDir;

    public JsonReportGenerator(Map<String, String> configuration) {
        outputDir = Paths.get(
                requireNonNull(configuration.get("outputDir"), "missing required outputDir configuration property"));
    }

    @Override
    public void generate(ReportModel model, PrintWriter out) throws IOException {
        var reportJson = outputDir.resolve("report.json");
        Files.createDirectories(outputDir);
        Files.writeString(reportJson, render(model, manifest()));
        out.printf("Roadrunner JSON report at %s%n", reportJson);
        out.flush();
    }

    private RunManifest manifest() throws IOException {
        try {
            return RunDirectory.of(outputDir).manifest();
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    static String render(ReportModel model, RunManifest m) {
        var j = new Json();
        j.object();
        j.field("schemaVersion").number(SCHEMA_VERSION);
        j.field("sampler").string(m == null ? null : text(m.sampler()));
        j.field("startedAt").string(m == null ? null : text(m.startedAt()));
        j.field("roadrunnerVersion").string(m == null ? null : text(m.roadrunnerVersion()));
        j.field("configuration");
        if (m == null) {
            j.nul();
        } else {
            configuration(j, m);
        }

        j.field("summary").object();
        j.field("requests").number(model.total());
        j.field("errors").number(model.errors());
        j.field("errorRatePercent").number(model.errorPercentage());
        j.field("throughputPerSecond").number(model.throughput());
        j.field("durationSeconds").number(model.durationSeconds());
        j.field("p50Ms").number(model.p50());
        j.field("p90Ms").number(model.p90());
        j.field("p99Ms").number(model.p99());
        j.field("p999Ms").number(model.p999());
        j.field("minMs").number(model.minMs());
        j.field("meanMs").number(model.meanMs());
        j.field("maxMs").number(model.maxMs());
        j.end();

        j.field("latencyPercentiles").array();
        percentile(j, 50, model.p50());
        percentile(j, 90, model.p90());
        percentile(j, 99, model.p99());
        percentile(j, 99.9, model.p999());
        j.end();

        var slices = model.throughputSeries().length;
        var sliceSeconds = slices == 0 ? 0 : model.durationSeconds() / slices;
        var seconds = new double[slices];
        var rate = new double[slices];
        for (var i = 0; i < slices; i++) {
            seconds[i] = sliceSeconds * i;
            rate[i] = sliceSeconds > 0 ? model.throughputSeries()[i] / sliceSeconds : 0;
        }
        var latency = model.latencyOverTime();
        j.field("latencyOverTime").object();
        j.field("sliceSeconds").number(sliceSeconds);
        j.field("seconds").numbers(seconds);
        j.field("p50Ms").numbers(latency.p50());
        j.field("p90Ms").numbers(latency.p90());
        j.field("p99Ms").numbers(latency.p99());
        j.end();
        j.field("throughputOverTime").object();
        j.field("sliceSeconds").number(sliceSeconds);
        j.field("seconds").numbers(seconds);
        j.field("requestsPerSecond").numbers(rate);
        j.end();

        j.field("status").object();
        model.statusBreakdown().forEach((status, count) -> j.field(status).number(count));
        j.end();

        j.end();
        return j.toString() + "\n";
    }

    private static void percentile(Json j, double percentile, long latencyMs) {
        j.object();
        j.field("percentile").number(percentile);
        j.field("latencyMs").number(latencyMs);
        j.end();
    }

    private static void configuration(Json j, RunManifest m) {
        j.object();
        j.field("sampler").object();
        j.field("name").string(text(m.sampler()));
        j.field("options").object();
        new java.util.TreeMap<>(m.samplerOptions()).forEach((k, v) -> j.field(k).string(v));
        j.end();
        j.field("parametersSource").string(text(m.parametersSource()));
        j.end();

        j.field("loadModel").object();
        j.field("model").string(text(m.loadModel()));
        j.field("concurrency").numberOrString(text(m.loadConcurrency()));
        j.field("requests").numberOrString(text(m.loadRequests()));
        j.field("rate").numberOrString(text(m.loadRate()));
        j.field("duration").string(text(m.loadDuration()));
        j.field("pauseDetectors").array();
        var detectors = text(m.pauseDetectors());
        if (detectors != null) {
            for (var d : detectors.split(",")) {
                if (!d.isBlank()) {
                    j.string(d.trim());
                }
            }
        }
        j.end();
        j.end();

        j.field("environment").object();
        j.field("java").string(text(m.jvmVersion()));
        j.field("jvmArgs").string(text(m.jvmArgs()));
        j.field("os").object();
        j.field("name").string(text(m.osName()));
        j.field("version").string(text(m.osVersion()));
        j.field("arch").string(text(m.osArch()));
        j.end();
        j.field("cpus").numberOrString(text(m.hardwareCpus()));
        j.field("memory").string(text(m.hardwareMemory()));
        j.end();
        j.end();
    }

    /** Blank and the manifest reader's "unknown" placeholder both mean the field is absent. */
    private static String text(String value) {
        return value == null || value.isBlank() || "unknown".equals(value) ? null : value;
    }

    /** A tiny JSON writer: correct string escaping, locale-independent numbers, non-finite numbers as null. */
    static final class Json {
        private final StringBuilder sb = new StringBuilder();
        private final java.util.ArrayDeque<boolean[]> stack = new java.util.ArrayDeque<>();
        private boolean afterField;

        Json object() {
            open('{');
            return this;
        }

        Json array() {
            open('[');
            return this;
        }

        Json end() {
            var kind = stack.pop();
            sb.append(kind[1] ? ']' : '}');
            return this;
        }

        Json field(String name) {
            sb.append(stack.peek()[0] ? "" : ",");
            stack.peek()[0] = false;
            quote(name);
            sb.append(':');
            afterField = true;
            return this;
        }

        Json string(String value) {
            if (value == null) {
                return nul();
            }
            value(null);
            quote(value);
            return this;
        }

        Json nul() {
            value("null");
            return this;
        }

        Json number(long value) {
            value(Long.toString(value));
            return this;
        }

        Json number(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return nul();
            }
            value(new java.math.BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString());
            return this;
        }

        /** Numeric manifest fields: a number when it parses as one, else the raw text, else null. */
        Json numberOrString(String value) {
            if (value == null) {
                return nul();
            }
            try {
                return number(Long.parseLong(value.trim()));
            } catch (NumberFormatException e) {
                try {
                    return number(Double.parseDouble(value.trim()));
                } catch (NumberFormatException e2) {
                    return string(value);
                }
            }
        }

        Json numbers(double[] values) {
            array();
            for (var v : values) {
                number(v);
            }
            return end();
        }

        Json numbers(long[] values) {
            array();
            for (var v : values) {
                number(v);
            }
            return end();
        }

        private void open(char c) {
            value(String.valueOf(c));
            stack.push(new boolean[] {true, c == '['});
        }

        /** Emits a separator if needed, then the literal (if any). */
        private void value(String literal) {
            if (!afterField && !stack.isEmpty()) {
                var top = stack.peek();
                sb.append(top[0] ? "" : ",");
                top[0] = false;
            }
            afterField = false;
            if (literal != null) {
                sb.append(literal);
            }
        }

        private void quote(String s) {
            sb.append('"');
            for (var i = 0; i < s.length(); i++) {
                var c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    default -> {
                        if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
