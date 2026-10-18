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
package io.roadrunner.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ComparabilityTest {

    @Test
    void identicalClosedModelManifestsAreComparable() {
        var baseline = closedManifest("http", Map.of("url", "http://localhost/"), "10", "2000", "vt,jvm");
        var current = closedManifest("http", Map.of("url", "http://localhost/"), "10", "2000", "vt,jvm");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isTrue();
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    void identicalOpenModelManifestsAreComparable() {
        var baseline = openManifest("http", Map.of("url", "http://localhost/"), "50", "PT30S", "vt,jvm");
        var current = openManifest("http", Map.of("url", "http://localhost/"), "50", "PT30S", "vt,jvm");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isTrue();
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    void reportsSamplerMismatch() {
        var baseline = closedManifest("http", Map.of(), "10", "1000", "none");
        var current = closedManifest("vm", Map.of(), "10", "1000", "none");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isFalse();
        assertThat(result.mismatches())
                .containsExactly("Sampler mismatch: baseline used 'http', but current used 'vm'");
    }

    @Test
    void reportsLoadModelMismatchWithoutCheckingIrrelevantParameters() {
        var baseline = closedManifest("http", Map.of(), "10", "1000", "none");
        var current = openManifest("http", Map.of(), "50", "PT30S", "none");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isFalse();
        assertThat(result.mismatches())
                .containsExactly("Load model mismatch: baseline used 'closed', but current used 'open'");
    }

    @Test
    void reportsClosedModelConcurrencyAndRequestsMismatches() {
        var baseline = closedManifest("http", Map.of(), "10", "1000", "none");
        var current = closedManifest("http", Map.of(), "20", "2000", "none");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isFalse();
        assertThat(result.mismatches())
                .containsExactly(
                        "Load concurrency mismatch: baseline used '10', but current used '20'",
                        "Load requests mismatch: baseline used '1000', but current used '2000'");
    }

    @Test
    void doesNotCompareOpenParametersForClosedModelRuns() {
        // Even if irrelevant open parameters differ in closed-model runs, they should be ignored
        var baseline = new RunManifest(
                "0.4.1",
                "2026-10-18T10:00:00Z",
                "http",
                Map.of(),
                "closed",
                "10",
                "1000",
                "100",
                "PT1M",
                "",
                "",
                "25",
                "",
                "Linux",
                "6",
                "amd64",
                "8",
                "16G");
        var current = new RunManifest(
                "0.4.1",
                "2026-10-18T10:00:00Z",
                "http",
                Map.of(),
                "closed",
                "10",
                "1000",
                "200",
                "PT2M",
                "",
                "",
                "25",
                "",
                "Linux",
                "6",
                "amd64",
                "8",
                "16G");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isTrue();
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    void reportsOpenModelRateAndDurationMismatches() {
        var baseline = openManifest("http", Map.of(), "50", "PT30S", "none");
        var current = openManifest("http", Map.of(), "100", "PT60S", "none");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isFalse();
        assertThat(result.mismatches())
                .containsExactly(
                        "Load rate mismatch: baseline used '50', but current used '100'",
                        "Load duration mismatch: baseline used 'PT30S', but current used 'PT60S'");
    }

    @Test
    void doesNotCompareClosedParametersForOpenModelRuns() {
        // Even if irrelevant closed parameters differ in open-model runs, they should be ignored
        var baseline = new RunManifest(
                "0.4.1",
                "2026-10-18T10:00:00Z",
                "http",
                Map.of(),
                "open",
                "10",
                "1000",
                "50",
                "PT30S",
                "",
                "",
                "25",
                "",
                "Linux",
                "6",
                "amd64",
                "8",
                "16G");
        var current = new RunManifest(
                "0.4.1",
                "2026-10-18T10:00:00Z",
                "http",
                Map.of(),
                "open",
                "20",
                "2000",
                "50",
                "PT30S",
                "",
                "",
                "25",
                "",
                "Linux",
                "6",
                "amd64",
                "8",
                "16G");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isTrue();
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    void reportsPauseDetectorsMismatch() {
        var rawBaseline = closedManifest("http", Map.of(), "10", "1000", "");
        var correctedCurrent = closedManifest("http", Map.of(), "10", "1000", "vt,jvm");

        var result = Comparability.compare(rawBaseline, correctedCurrent);

        assertThat(result.isComparable()).isFalse();
        assertThat(result.mismatches())
                .containsExactly("Pause detectors mismatch: baseline used 'none', but current used 'vt,jvm'");
    }

    @Test
    void reportsSamplerOptionsMismatch() {
        var baseline = closedManifest("http", Map.of("url", "http://localhost:8080/"), "10", "1000", "");
        var current = closedManifest("http", Map.of("url", "http://localhost:9090/"), "10", "1000", "");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isFalse();
        assertThat(result.mismatches())
                .containsExactly(
                        "Sampler options mismatch: baseline used {url=http://localhost:8080/}, but current used {url=http://localhost:9090/}");
    }

    @Test
    void reportsMultipleMismatchesTogether() {
        var baseline = closedManifest("http", Map.of("url", "http://a"), "10", "1000", "vt");
        var current = closedManifest("vm", Map.of("url", "http://b"), "20", "1000", "jvm");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isFalse();
        assertThat(result.mismatches()).hasSize(4);
        assertThat(result.mismatches())
                .containsExactly(
                        "Sampler mismatch: baseline used 'http', but current used 'vm'",
                        "Sampler options mismatch: baseline used {url=http://a}, but current used {url=http://b}",
                        "Load concurrency mismatch: baseline used '10', but current used '20'",
                        "Pause detectors mismatch: baseline used 'vt', but current used 'jvm'");
    }

    @Test
    void ignoresEnvironmentalFieldDifferences() {
        var baseline = new RunManifest(
                "0.3.1",
                "2026-07-31T10:00:00Z",
                "http",
                Map.of(),
                "closed",
                "10",
                "1000",
                "",
                "",
                "vt",
                "csv:ids.csv",
                "25.0.1",
                "-Xmx1g",
                "Linux",
                "6.1",
                "amd64",
                "8",
                "16G");
        var current = new RunManifest(
                "0.4.1",
                "2026-10-18T10:00:00Z",
                "http",
                Map.of(),
                "closed",
                "10",
                "1000",
                "",
                "",
                "vt",
                "csv:other.csv",
                "25.0.3",
                "-Xmx2g",
                "Windows",
                "11",
                "x86_64",
                "16",
                "32G");

        var result = Comparability.compare(baseline, current);

        assertThat(result.isComparable()).isTrue();
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    void handlesMissingOrNullManifests() {
        var manifest = closedManifest("http", Map.of(), "10", "1000", "");

        var bothNull = Comparability.compare(null, null);
        assertThat(bothNull.isComparable()).isFalse();
        assertThat(bothNull.mismatches()).containsExactly("Baseline and current run manifests are missing");

        var baselineNull = Comparability.compare(null, manifest);
        assertThat(baselineNull.isComparable()).isFalse();
        assertThat(baselineNull.mismatches()).containsExactly("Baseline run manifest is missing");

        var currentNull = Comparability.compare(manifest, null);
        assertThat(currentNull.isComparable()).isFalse();
        assertThat(currentNull.mismatches()).containsExactly("Current run manifest is missing");
    }

    @Test
    void returnsImmutableMismatchesList() {
        var result = Comparability.incompatible(java.util.List.of("Mismatch"));

        assertThatThrownBy(() -> result.mismatches().add("Another")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static RunManifest closedManifest(
            String sampler, Map<String, String> options, String concurrency, String requests, String pauseDetectors) {
        return new RunManifest(
                "0.4.1",
                "2026-10-18T10:00:00Z",
                sampler,
                options,
                "closed",
                concurrency,
                requests,
                "",
                "",
                pauseDetectors,
                "",
                "25.0.3",
                "-Xmx1g",
                "Linux",
                "6.17.0-41-generic",
                "amd64",
                "8",
                "16000000000");
    }

    private static RunManifest openManifest(
            String sampler, Map<String, String> options, String rate, String duration, String pauseDetectors) {
        return new RunManifest(
                "0.4.1",
                "2026-10-18T10:00:00Z",
                sampler,
                options,
                "open",
                "",
                "",
                rate,
                duration,
                pauseDetectors,
                "",
                "25.0.3",
                "-Xmx1g",
                "Linux",
                "6.17.0-41-generic",
                "amd64",
                "8",
                "16000000000");
    }
}
