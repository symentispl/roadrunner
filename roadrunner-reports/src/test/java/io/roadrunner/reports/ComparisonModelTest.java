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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComparisonModelTest {

    private static ReportModel createReport(
            long minMs,
            long meanMs,
            long maxMs,
            long p50,
            long p90,
            long p99,
            long p999,
            double throughput,
            double errorPercentage) {
        return new ReportModel(
                1000L,
                (long) (1000L * (1.0 - errorPercentage / 100.0)),
                (long) (1000L * (errorPercentage / 100.0)),
                10.0,
                throughput,
                errorPercentage,
                minMs,
                meanMs,
                maxMs,
                p50,
                p90,
                p99,
                p999,
                new long[20],
                new long[30],
                new long[30],
                Map.of(),
                new LatencyOverTime(new long[30], new long[30], new long[30]));
    }

    private static RunManifest createManifest(String sampler, String loadModel, String concurrency) {
        return new RunManifest(
                "0.4.1",
                "2026-10-18T10:00:00Z",
                sampler,
                Map.of(),
                loadModel,
                concurrency,
                "1000",
                "unknown",
                "unknown",
                "",
                "unknown",
                "23",
                "",
                "Windows",
                "11",
                "amd64",
                "8",
                "16GB");
    }

    @Test
    void containsAllNineMetrics() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(12, 22, 105, 20, 42, 85, 98, 980.0, 1.2);

        var model = ComparisonModel.of(baseline, current);

        assertThat(model.metrics()).hasSize(9);
        for (var metric : ComparisonMetric.values()) {
            assertThat(model.metrics()).containsKey(metric);
            assertThat(model.metric(metric)).isNotNull();
            assertThat(model.metric(metric).metric()).isEqualTo(metric);
        }
    }

    @Test
    void extractsValuesCorrectlyFromBothReports() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 2.5);
        var current = createReport(12, 22, 105, 20, 42, 85, 98, 980.0, 1.5);

        var model = ComparisonModel.of(baseline, current);

        assertThat(model.metric(ComparisonMetric.MIN).baselineValue()).isEqualTo(10.0);
        assertThat(model.metric(ComparisonMetric.MIN).currentValue()).isEqualTo(12.0);

        assertThat(model.metric(ComparisonMetric.MEAN).baselineValue()).isEqualTo(20.0);
        assertThat(model.metric(ComparisonMetric.MEAN).currentValue()).isEqualTo(22.0);

        assertThat(model.metric(ComparisonMetric.MAX).baselineValue()).isEqualTo(100.0);
        assertThat(model.metric(ComparisonMetric.MAX).currentValue()).isEqualTo(105.0);

        assertThat(model.metric(ComparisonMetric.P50).baselineValue()).isEqualTo(18.0);
        assertThat(model.metric(ComparisonMetric.P50).currentValue()).isEqualTo(20.0);

        assertThat(model.metric(ComparisonMetric.P90).baselineValue()).isEqualTo(40.0);
        assertThat(model.metric(ComparisonMetric.P90).currentValue()).isEqualTo(42.0);

        assertThat(model.metric(ComparisonMetric.P99).baselineValue()).isEqualTo(80.0);
        assertThat(model.metric(ComparisonMetric.P99).currentValue()).isEqualTo(85.0);

        assertThat(model.metric(ComparisonMetric.P999).baselineValue()).isEqualTo(95.0);
        assertThat(model.metric(ComparisonMetric.P999).currentValue()).isEqualTo(98.0);

        assertThat(model.metric(ComparisonMetric.THROUGHPUT).baselineValue()).isEqualTo(1000.0);
        assertThat(model.metric(ComparisonMetric.THROUGHPUT).currentValue()).isEqualTo(980.0);

        assertThat(model.metric(ComparisonMetric.ERRORS).baselineValue()).isEqualTo(2.5);
        assertThat(model.metric(ComparisonMetric.ERRORS).currentValue()).isEqualTo(1.5);
    }

    @Test
    void looksUpComparisonByMetric() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);

        var model = ComparisonModel.of(baseline, current);

        for (var metric : ComparisonMetric.values()) {
            assertThat(model.metric(metric)).isSameAs(model.metrics().get(metric));
        }
    }

    @Test
    void comparisonCollectionIsImmutable() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);

        var model = ComparisonModel.of(baseline, current);

        assertThatThrownBy(() -> model.metrics().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reportOnlyModeProducesUncheckedMetricsAndOverallPassed() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(20, 40, 200, 36, 80, 160, 190, 500.0, 10.0);

        // Even with huge regressions, report-only mode (no tolerances) has UNCHECKED metrics and overall PASSED
        var model = ComparisonModel.of(baseline, current);

        for (var metric : ComparisonMetric.values()) {
            assertThat(model.metric(metric).verdict()).isEqualTo(Verdict.UNCHECKED);
        }
        assertThat(model.verdict()).isEqualTo(Verdict.PASSED);
        assertThat(model.isPassed()).isTrue();
        assertThat(model.isFailed()).isFalse();
    }

    @Test
    void passingToleranceProducesOverallPassed() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(10, 20, 100, 18, 40, 85, 95, 960.0, 1.3);

        // p99: 80 -> 85 (+6.25% <= +10%), throughput: 1000 -> 960 (-4.0% >= -5%), errors: 1.0% -> 1.3% (+0.3 <= +0.5)
        var tolerances = Tolerances.parse("p99:+10%,throughput:-5%,errors:+0.5%");
        var model = ComparisonModel.of(baseline, current, tolerances);

        assertThat(model.metric(ComparisonMetric.P99).verdict()).isEqualTo(Verdict.PASSED);
        assertThat(model.metric(ComparisonMetric.THROUGHPUT).verdict()).isEqualTo(Verdict.PASSED);
        assertThat(model.metric(ComparisonMetric.ERRORS).verdict()).isEqualTo(Verdict.PASSED);
        assertThat(model.metric(ComparisonMetric.MEAN).verdict()).isEqualTo(Verdict.UNCHECKED);

        assertThat(model.verdict()).isEqualTo(Verdict.PASSED);
        assertThat(model.isPassed()).isTrue();
        assertThat(model.isFailed()).isFalse();
    }

    @Test
    void anyFailingMetricProducesOverallFailed() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        // p99 degrades by +25% (80 -> 100)
        var current = createReport(10, 20, 100, 18, 40, 100, 95, 980.0, 1.0);

        var tolerances = Tolerances.parse("p99:+10%,throughput:-5%");
        var model = ComparisonModel.of(baseline, current, tolerances);

        assertThat(model.metric(ComparisonMetric.P99).verdict()).isEqualTo(Verdict.FAILED);
        assertThat(model.metric(ComparisonMetric.THROUGHPUT).verdict()).isEqualTo(Verdict.PASSED);
        assertThat(model.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(model.isPassed()).isFalse();
        assertThat(model.isFailed()).isTrue();
    }

    @Test
    void multipleMetricFailuresStillProduceOverallFailed() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        // p99 degrades by +25% (80 -> 100) and throughput drops by -20% (1000 -> 800)
        var current = createReport(10, 20, 100, 18, 40, 100, 95, 800.0, 1.0);

        var tolerances = Tolerances.parse("p99:+10%,throughput:-5%");
        var model = ComparisonModel.of(baseline, current, tolerances);

        assertThat(model.metric(ComparisonMetric.P99).verdict()).isEqualTo(Verdict.FAILED);
        assertThat(model.metric(ComparisonMetric.THROUGHPUT).verdict()).isEqualTo(Verdict.FAILED);
        assertThat(model.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(model.isFailed()).isTrue();
    }

    @Test
    void manifestMismatchesDoNotPreventMetricComparison() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(10, 20, 100, 18, 40, 85, 95, 960.0, 1.0);

        var baselineManifest = createManifest("http", "closed", "10");
        var currentManifest = createManifest("grpc", "closed", "10"); // Sampler mismatch

        var tolerances = Tolerances.parse("p99:+10%");
        var model = ComparisonModel.of(baseline, current, tolerances, baselineManifest, currentManifest);

        // Comparability reports mismatch
        assertThat(model.comparability().isComparable()).isFalse();
        assertThat(model.comparability().mismatches())
                .anyMatch(m -> m.contains("Sampler mismatch") && m.contains("http") && m.contains("grpc"));

        // Metric comparison still computes normally and passes
        assertThat(model.metrics()).hasSize(9);
        assertThat(model.metric(ComparisonMetric.P99).verdict()).isEqualTo(Verdict.PASSED);
        assertThat(model.verdict()).isEqualTo(Verdict.PASSED);
    }

    @Test
    void handlesMissingManifestsFromRunDirectory(@TempDir Path tempDir) throws IOException {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(10, 20, 100, 18, 40, 85, 95, 960.0, 1.0);

        var baselineDir = Files.createDirectory(tempDir.resolve("baseline"));
        var currentDir = Files.createDirectory(tempDir.resolve("current"));

        // Only current directory gets a manifest; baseline directory has no run.properties
        var currentManifest = createManifest("http", "closed", "10");
        currentManifest.writeTo(currentDir);

        var model = ComparisonModel.of(
                baseline, current, Tolerances.empty(), RunDirectory.of(baselineDir), RunDirectory.of(currentDir));

        assertThat(model.comparability().isComparable()).isFalse();
        assertThat(model.comparability().mismatches()).containsExactly("Baseline run manifest is missing");

        // Metrics are still computed
        assertThat(model.metrics()).hasSize(9);
        assertThat(model.verdict()).isEqualTo(Verdict.PASSED);
    }

    @Test
    void handlesBothMissingManifestsFromRunDirectory(@TempDir Path tempDir) throws IOException {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);

        var baselineDir = Files.createDirectory(tempDir.resolve("baseline_empty"));
        var currentDir = Files.createDirectory(tempDir.resolve("current_empty"));

        var model = ComparisonModel.of(
                baseline, current, Tolerances.empty(), RunDirectory.of(baselineDir), RunDirectory.of(currentDir));

        assertThat(model.comparability().isComparable()).isFalse();
        assertThat(model.comparability().mismatches())
                .containsExactly("Baseline and current run manifests are missing");
        assertThat(model.metrics()).hasSize(9);
    }

    @Test
    void handlesMatchingManifestsFromRunDirectory(@TempDir Path tempDir) throws IOException {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);

        var baselineDir = Files.createDirectory(tempDir.resolve("baseline_ok"));
        var currentDir = Files.createDirectory(tempDir.resolve("current_ok"));

        var manifest = createManifest("http", "closed", "10");
        manifest.writeTo(baselineDir);
        manifest.writeTo(currentDir);

        var model = ComparisonModel.of(
                baseline, current, Tolerances.empty(), RunDirectory.of(baselineDir), RunDirectory.of(currentDir));

        assertThat(model.comparability().isComparable()).isTrue();
        assertThat(model.comparability().mismatches()).isEmpty();
    }

    @Test
    void validatesNullArguments() {
        var report = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);

        assertThatThrownBy(() -> ComparisonModel.of(null, report))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("baseline report cannot be null");

        assertThatThrownBy(() -> ComparisonModel.of(report, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("current report cannot be null");

        assertThatThrownBy(() -> ComparisonModel.of(report, report, null, Comparability.compatible()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("tolerances cannot be null");

        assertThatThrownBy(() -> ComparisonModel.of(report, report, Tolerances.empty(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("comparability cannot be null");

        var model = ComparisonModel.of(report, report);
        assertThatThrownBy(() -> model.metric(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("metric cannot be null");
    }

    @Test
    void canonicalConstructorRequiresAllNineMetrics() {
        var report = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var partialMap = new EnumMap<ComparisonMetric, MetricComparison>(ComparisonMetric.class);
        partialMap.put(ComparisonMetric.P99, MetricComparison.of(ComparisonMetric.P99, 10.0, 10.0));

        assertThatThrownBy(() -> new ComparisonModel(
                        report, report, Tolerances.empty(), Comparability.compatible(), partialMap, Verdict.PASSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("missing comparison for metric:");
    }

    @Test
    void rejectsPassedVerdictWhenAnyMetricFailed() {
        var report = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var metricsWithFailure =
                new LinkedHashMap<>(ComparisonModel.of(report, report).metrics());
        metricsWithFailure.put(
                ComparisonMetric.P99, MetricComparison.of(ComparisonMetric.P99, 100.0, 150.0, Tolerance.upper(10.0)));

        assertThatThrownBy(() -> new ComparisonModel(
                        report,
                        report,
                        Tolerances.empty(),
                        Comparability.compatible(),
                        metricsWithFailure,
                        Verdict.PASSED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inconsistent overall verdict: expected FAILED but was PASSED");
    }

    @Test
    void rejectsFailedVerdictWhenNoMetricsFailed() {
        var report = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var metricsWithoutFailure = ComparisonModel.of(report, report).metrics();

        assertThatThrownBy(() -> new ComparisonModel(
                        report,
                        report,
                        Tolerances.empty(),
                        Comparability.compatible(),
                        metricsWithoutFailure,
                        Verdict.FAILED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inconsistent overall verdict: expected PASSED but was FAILED");
    }

    @Test
    void allowsDirectConstructionWithConsistentVerdicts() {
        var report = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var uncheckedMetrics = ComparisonModel.of(report, report).metrics();

        var passedModel = new ComparisonModel(
                report, report, Tolerances.empty(), Comparability.compatible(), uncheckedMetrics, Verdict.PASSED);
        assertThat(passedModel.verdict()).isEqualTo(Verdict.PASSED);
        assertThat(passedModel.isPassed()).isTrue();

        var metricsWithFailure = new LinkedHashMap<>(uncheckedMetrics);
        metricsWithFailure.put(
                ComparisonMetric.P99, MetricComparison.of(ComparisonMetric.P99, 100.0, 150.0, Tolerance.upper(10.0)));

        var failedModel = new ComparisonModel(
                report, report, Tolerances.empty(), Comparability.compatible(), metricsWithFailure, Verdict.FAILED);
        assertThat(failedModel.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(failedModel.isFailed()).isTrue();
    }

    @Test
    void canonicalConstructorRejectsNullMetricComparison() {
        var report = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var metricsWithNull =
                new LinkedHashMap<>(ComparisonModel.of(report, report).metrics());
        metricsWithNull.put(ComparisonMetric.P99, null);

        assertThatThrownBy(() -> new ComparisonModel(
                        report,
                        report,
                        Tolerances.empty(),
                        Comparability.compatible(),
                        metricsWithNull,
                        Verdict.PASSED))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("comparison for metric P99 cannot be null");
    }
}
