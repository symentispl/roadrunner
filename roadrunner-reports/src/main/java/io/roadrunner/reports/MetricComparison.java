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

import java.util.Optional;

/**
 * The comparison result for a single {@link ComparisonMetric} between a baseline and a current benchmark run.
 *
 * <p>For standard metrics (latency metrics and throughput), the delta represents the relative percentage change:
 * {@code ((current - baseline) / baseline) * 100}. If the baseline is zero, a zero current value yields a delta of
 * {@code 0.0}, whereas a positive current value yields {@link Double#POSITIVE_INFINITY} so that any positive
 * upper-bound tolerance fails without producing {@link Double#NaN}.
 *
 * <p>For the {@link ComparisonMetric#ERRORS} metric, the delta represents the difference in error percentage
 * ({@link ReportModel#errorPercentage()}) measured in percentage points: {@code current - baseline}.
 *
 * <p>If a {@link Tolerance} is present, the verdict is determined by evaluating the delta against that tolerance.
 * If no tolerance is configured, the verdict is {@link Verdict#UNCHECKED}.
 *
 * @param metric the metric being compared
 * @param baselineValue the value from the baseline run
 * @param currentValue the value from the current run
 * @param delta the calculated change (relative percentage for standard metrics; percentage points for errors)
 * @param tolerance the optional tolerance limit applied to this metric
 * @param verdict the evaluation verdict (PASSED, FAILED, or UNCHECKED)
 */
public record MetricComparison(
        ComparisonMetric metric,
        double baselineValue,
        double currentValue,
        double delta,
        Optional<Tolerance> tolerance,
        Verdict verdict) {

    public MetricComparison {
        requireNonNull(metric, "metric cannot be null");
        requireNonNull(verdict, "verdict cannot be null");
        tolerance = tolerance != null ? tolerance : Optional.empty();
        if (delta == 0.0) {
            delta = 0.0;
        }
    }

    public double baseline() {
        return baselineValue;
    }

    public double current() {
        return currentValue;
    }

    public boolean isPassed() {
        return verdict == Verdict.PASSED;
    }

    public boolean isFailed() {
        return verdict == Verdict.FAILED;
    }

    public boolean isUnchecked() {
        return verdict == Verdict.UNCHECKED;
    }

    /**
     * Calculates the delta between baseline and current values for the given metric.
     *
     * @param metric the comparison metric
     * @param baseline the baseline numeric value
     * @param current the current numeric value
     * @return the calculated delta (relative % for standard metrics, percentage points for errors)
     */
    public static double calculateDelta(ComparisonMetric metric, double baseline, double current) {
        requireNonNull(metric, "metric cannot be null");
        if (metric == ComparisonMetric.ERRORS) {
            var diff = current - baseline;
            return diff == 0.0 ? 0.0 : diff;
        }
        if (baseline == 0.0) {
            if (current == 0.0) {
                return 0.0;
            }
            return current > 0.0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        var delta = ((current - baseline) / baseline) * 100.0;
        return delta == 0.0 ? 0.0 : delta;
    }

    /**
     * Extracts the raw numeric value corresponding to the specified metric from a report model.
     *
     * @param metric the comparison metric
     * @param report the report model
     * @return the numeric value of the metric
     */
    public static double valueOf(ComparisonMetric metric, ReportModel report) {
        requireNonNull(metric, "metric cannot be null");
        requireNonNull(report, "report cannot be null");
        return switch (metric) {
            case MIN -> report.minMs();
            case MEAN -> report.meanMs();
            case MAX -> report.maxMs();
            case P50 -> report.p50();
            case P90 -> report.p90();
            case P99 -> report.p99();
            case P999 -> report.p999();
            case THROUGHPUT -> report.throughput();
            case ERRORS -> report.errorPercentage();
        };
    }

    /**
     * Creates a {@link MetricComparison} from raw numeric values and an optional tolerance.
     */
    public static MetricComparison of(
            ComparisonMetric metric, double baselineValue, double currentValue, Optional<Tolerance> tolerance) {
        requireNonNull(metric, "metric cannot be null");
        var tol = tolerance != null ? tolerance : Optional.<Tolerance>empty();
        var delta = calculateDelta(metric, baselineValue, currentValue);
        var verdict = tol.map(t -> t.verdict(delta)).orElse(Verdict.UNCHECKED);
        return new MetricComparison(metric, baselineValue, currentValue, delta, tol, verdict);
    }

    /**
     * Creates a {@link MetricComparison} from raw numeric values and a tolerance (which may be null).
     */
    public static MetricComparison of(
            ComparisonMetric metric, double baselineValue, double currentValue, Tolerance tolerance) {
        return of(metric, baselineValue, currentValue, Optional.ofNullable(tolerance));
    }

    /**
     * Creates an unchecked {@link MetricComparison} from raw numeric values without a tolerance.
     */
    public static MetricComparison of(ComparisonMetric metric, double baselineValue, double currentValue) {
        return of(metric, baselineValue, currentValue, Optional.empty());
    }

    /**
     * Compares a single metric between two report models using the configured tolerances.
     *
     * @param metric the metric to compare
     * @param baseline the baseline report model
     * @param current the current report model
     * @param tolerances the tolerances configuration (or null for empty tolerances)
     * @return the comparison result
     */
    public static MetricComparison from(
            ComparisonMetric metric, ReportModel baseline, ReportModel current, Tolerances tolerances) {
        requireNonNull(metric, "metric cannot be null");
        requireNonNull(baseline, "baseline report cannot be null");
        requireNonNull(current, "current report cannot be null");
        var tol = tolerances != null ? tolerances.get(metric) : Optional.<Tolerance>empty();
        return of(metric, valueOf(metric, baseline), valueOf(metric, current), tol);
    }

    /**
     * Compares a single metric between two report models with an explicit optional tolerance.
     */
    public static MetricComparison from(
            ComparisonMetric metric, ReportModel baseline, ReportModel current, Optional<Tolerance> tolerance) {
        requireNonNull(metric, "metric cannot be null");
        requireNonNull(baseline, "baseline report cannot be null");
        requireNonNull(current, "current report cannot be null");
        return of(metric, valueOf(metric, baseline), valueOf(metric, current), tolerance);
    }
}
