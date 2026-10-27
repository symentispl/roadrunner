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

import static java.util.Objects.requireNonNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of comparing a baseline {@link ReportModel} against a current {@link ReportModel}.
 *
 * <p>Contains comparisons for all nine {@link ComparisonMetric} values, evaluated against the configured
 * {@link Tolerances}. The overall {@link Verdict} is {@link Verdict#FAILED} if any individual metric comparison
 * fails; otherwise it is {@link Verdict#PASSED} (including when metrics are {@link Verdict#UNCHECKED}).
 *
 * <p>Manifest comparability is captured via {@link Comparability}. A manifest mismatch or missing manifest
 * is recorded in the comparability status but does not prevent metric comparisons from being computed.
 *
 * @param baseline the baseline report model
 * @param current the current report model
 * @param tolerances the tolerance configuration applied to the metrics
 * @param comparability the manifest comparability result
 * @param metrics the immutable map of comparisons for all nine metrics
 * @param verdict the overall verdict (PASSED or FAILED)
 */
public record ComparisonModel(
        ReportModel baseline,
        ReportModel current,
        Tolerances tolerances,
        Comparability comparability,
        Map<ComparisonMetric, MetricComparison> metrics,
        Verdict verdict) {

    public ComparisonModel {
        requireNonNull(baseline, "baseline report cannot be null");
        requireNonNull(current, "current report cannot be null");
        requireNonNull(tolerances, "tolerances cannot be null");
        requireNonNull(comparability, "comparability cannot be null");
        requireNonNull(metrics, "metrics cannot be null");
        requireNonNull(verdict, "verdict cannot be null");

        var hasFailure = false;
        for (var metric : ComparisonMetric.values()) {
            if (!metrics.containsKey(metric)) {
                throw new IllegalArgumentException("missing comparison for metric: " + metric);
            }
            var comparison = requireNonNull(metrics.get(metric), "comparison for metric " + metric + " cannot be null");
            if (comparison.verdict() == Verdict.FAILED) {
                hasFailure = true;
            }
        }
        var expectedVerdict = hasFailure ? Verdict.FAILED : Verdict.PASSED;
        if (verdict != expectedVerdict) {
            throw new IllegalArgumentException(
                    "inconsistent overall verdict: expected " + expectedVerdict + " but was " + verdict);
        }
        metrics = Map.copyOf(metrics);
    }

    /**
     * Returns the comparison result for the specified metric.
     *
     * @param metric the metric to look up
     * @return the comparison for that metric
     */
    public MetricComparison metric(ComparisonMetric metric) {
        requireNonNull(metric, "metric cannot be null");
        return metrics.get(metric);
    }

    public boolean isPassed() {
        return verdict == Verdict.PASSED;
    }

    public boolean isFailed() {
        return verdict == Verdict.FAILED;
    }

    /**
     * Builds a {@link ComparisonModel} from baseline and current reports, tolerances, and comparability.
     */
    public static ComparisonModel of(
            ReportModel baseline, ReportModel current, Tolerances tolerances, Comparability comparability) {
        requireNonNull(baseline, "baseline report cannot be null");
        requireNonNull(current, "current report cannot be null");
        requireNonNull(tolerances, "tolerances cannot be null");
        requireNonNull(comparability, "comparability cannot be null");

        var metricsMap = new LinkedHashMap<ComparisonMetric, MetricComparison>();
        var hasFailure = false;
        for (var metric : ComparisonMetric.values()) {
            var comparison = MetricComparison.from(metric, baseline, current, tolerances);
            metricsMap.put(metric, comparison);
            if (comparison.verdict() == Verdict.FAILED) {
                hasFailure = true;
            }
        }
        var overallVerdict = hasFailure ? Verdict.FAILED : Verdict.PASSED;
        return new ComparisonModel(baseline, current, tolerances, comparability, metricsMap, overallVerdict);
    }

    /**
     * Builds a {@link ComparisonModel} assuming compatible manifests.
     */
    public static ComparisonModel of(ReportModel baseline, ReportModel current, Tolerances tolerances) {
        return of(baseline, current, tolerances, Comparability.compatible());
    }

    /**
     * Builds an unchecked {@link ComparisonModel} with empty tolerances and compatible manifests.
     */
    public static ComparisonModel of(ReportModel baseline, ReportModel current) {
        return of(baseline, current, Tolerances.empty(), Comparability.compatible());
    }

    /**
     * Builds a {@link ComparisonModel} by comparing baseline and current {@link RunManifest} instances.
     */
    public static ComparisonModel of(
            ReportModel baseline,
            ReportModel current,
            Tolerances tolerances,
            RunManifest baselineManifest,
            RunManifest currentManifest) {
        return of(baseline, current, tolerances, Comparability.compare(baselineManifest, currentManifest));
    }

    /**
     * Builds a {@link ComparisonModel} by reading manifests from baseline and current {@link RunDirectory} instances.
     *
     * <p>If a run directory has no manifest (e.g. {@code run.properties} is missing), the missing manifest is
     * recorded in {@link Comparability} without throwing. Any other unexpected I/O failure is propagated.
     */
    public static ComparisonModel of(
            ReportModel baseline,
            ReportModel current,
            Tolerances tolerances,
            RunDirectory baselineDir,
            RunDirectory currentDir)
            throws IOException {
        var baselineManifest = readManifest(baselineDir);
        var currentManifest = readManifest(currentDir);
        return of(baseline, current, tolerances, Comparability.compare(baselineManifest, currentManifest));
    }

    private static RunManifest readManifest(RunDirectory dir) throws IOException {
        if (dir == null) {
            return null;
        }
        try {
            return dir.manifest();
        } catch (FileNotFoundException | NoSuchFileException e) {
            return null;
        }
    }
}
