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

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.console.ConsoleTheme;
import io.roadrunner.reports.LatencyOverTime;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.RunManifest;
import java.util.Map;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.Test;

class ReportRendererTest {

    private static ConsoleTheme dumbTheme() throws Exception {
        var terminal = TerminalBuilder.builder().dumb(true).build();
        return ConsoleTheme.of(terminal);
    }

    private static ReportModel sampleModel() {
        return new ReportModel(
                100,
                95,
                5,
                10.0,
                10.0,
                5.0,
                1,
                8,
                120,
                5,
                15,
                90,
                118,
                new long[] {50, 30, 15, 5},
                new long[] {8, 10, 12, 9},
                new long[] {1, 2, 2, 1},
                Map.of("200", 95L, "500", 5L),
                new LatencyOverTime(new long[] {5, 6, 7, 8}, new long[] {15, 16, 17, 18}, new long[] {90, 91, 92, 93}));
    }

    private static RunManifest manifest() {
        return new RunManifest(
                "1.2.3",
                "2024-05-01T10:00:00Z",
                "http",
                Map.of("url", "http://x/${y}", "method", "GET"),
                "closed",
                "4",
                "100",
                "",
                "",
                "none",
                "params.csv",
                "27",
                "-Xmx1g",
                "Linux",
                "6.1",
                "amd64",
                "8",
                "16 GB");
    }

    @Test
    void rendersKeyNumbersInPlainText() throws Exception {
        var out = ReportRenderer.render(sampleModel(), dumbTheme());
        assertThat(out).contains("requests    100");
        assertThat(out).contains("error rate  5.00%");
        assertThat(out).contains("throughput  10.0 req/s");
        assertThat(out).contains("p50         5 ms");
        assertThat(out).contains("p99         90 ms");
        assertThat(out).contains("duration    10.0 s");
        assertThat(out).containsPattern("200 +\\S+ 95");
        assertThat(out).containsPattern("500 +\\S+ 5");
        assertThat(out).doesNotContain("["); // no ANSI escapes in dumb terminal
    }

    @Test
    void rendersPercentilesAndTimeSeries() throws Exception {
        var out = ReportRenderer.render(sampleModel(), dumbTheme());
        assertThat(out).contains("Latency by percentile");
        assertThat(out).containsPattern("p99\\.9 +\\S+ 118 ms");
        assertThat(out).contains("Latency over time");
        // slice 1 starts at 10s / 4 slices * 1 = 2.5s: p50 6, p90 16, p99 91
        assertThat(out).containsPattern("2\\.5 +6 +16 +91 ");
        assertThat(out).contains("Throughput over time");
        // 10 requests in a 2.5s slice = 4.0 req/s
        assertThat(out).containsPattern("2\\.5 +4\\.0 ");
    }

    @Test
    void rendersConfigurationFromManifestOmittingEmptyFields() throws Exception {
        var out = ReportRenderer.render(sampleModel(), manifest(), dumbTheme());
        assertThat(out).contains("sampler http | started 2024-05-01T10:00:00Z | roadrunner 1.2.3");
        assertThat(out).contains("Sampler").contains("Load model").contains("Environment");
        assertThat(out).containsPattern("url +http://x/\\$\\{y\\}");
        assertThat(out).containsPattern("method +GET");
        assertThat(out).containsPattern("parameters source +params.csv");
        assertThat(out).containsPattern("concurrency +4");
        assertThat(out).containsPattern("pause detectors +none");
        assertThat(out).doesNotContainPattern("(?m)^    (rate|duration) ");
        assertThat(out).containsPattern("java +27");
        assertThat(out).containsPattern("jvm args +-Xmx1g");
        assertThat(out).containsPattern("os +Linux 6.1 \\(amd64\\)");
        assertThat(out).containsPattern("cpus +8");
        assertThat(out).containsPattern("memory +16 GB");
    }

    @Test
    void withoutManifestHeaderIsUnknownAndThereIsNoConfiguration() throws Exception {
        var out = ReportRenderer.render(sampleModel(), null, dumbTheme());
        assertThat(out).contains("sampler unknown | started unknown | roadrunner unknown");
        assertThat(out).doesNotContain("Load model").doesNotContain("Environment");
    }

    @Test
    void emptyStatusBreakdownSaysSo() throws Exception {
        var m = sampleModel();
        var empty = new ReportModel(
                m.total(), m.success(), m.errors(), m.durationSeconds(), m.throughput(), m.errorPercentage(),
                m.minMs(), m.meanMs(), m.maxMs(), m.p50(), m.p90(), m.p99(), m.p999(), m.latencyBuckets(),
                m.throughputSeries(), m.usersOverTime(), Map.of(), m.latencyOverTime());
        assertThat(ReportRenderer.render(empty, dumbTheme())).contains("no status recorded");
    }

    @Test
    void degenerateRunWithNoDurationRendersZeroRates() throws Exception {
        var empty = new ReportModel(
                0, 0, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, new long[20], new long[3], new long[3], Map.of(),
                new LatencyOverTime(new long[3], new long[3], new long[3]));
        var out = ReportRenderer.render(empty, dumbTheme());
        assertThat(out).contains("requests    0").contains("duration    0.0 s");
        assertThat(out).containsPattern("0\\.0 +0\\.0 ");
        assertThat(out).contains("no status recorded");
    }
}
