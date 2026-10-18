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
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MetricComparisonTest {

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

    @Test
    void computesRelativePositiveAndNegativeChangesForStandardMetrics() {
        var increase = MetricComparison.of(ComparisonMetric.P99, 200.0, 250.0);
        assertThat(increase.delta()).isCloseTo(25.0, within(0.0001));
        assertThat(increase.baselineValue()).isEqualTo(200.0);
        assertThat(increase.currentValue()).isEqualTo(250.0);
        assertThat(increase.baseline()).isEqualTo(200.0);
        assertThat(increase.current()).isEqualTo(250.0);

        var decrease = MetricComparison.of(ComparisonMetric.THROUGHPUT, 200.0, 150.0);
        assertThat(decrease.delta()).isCloseTo(-25.0, within(0.0001));

        var fiftyPercent = MetricComparison.of(ComparisonMetric.MEAN, 50.0, 75.0);
        assertThat(fiftyPercent.delta()).isCloseTo(50.0, within(0.0001));
    }

    @Test
    void evaluatesExactThresholdBoundariesForUpperBound() {
        var tolerance = Tolerance.upper(10.0);

        var atLimit = MetricComparison.of(ComparisonMetric.P99, 100.0, 110.0, tolerance);
        assertThat(atLimit.delta()).isCloseTo(10.0, within(0.0001));
        assertThat(atLimit.verdict()).isEqualTo(Verdict.PASSED);
        assertThat(atLimit.isPassed()).isTrue();
        assertThat(atLimit.isFailed()).isFalse();

        var justAboveLimit = MetricComparison.of(ComparisonMetric.P99, 100.0, 110.01, tolerance);
        assertThat(justAboveLimit.delta()).isGreaterThan(10.0);
        assertThat(justAboveLimit.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(justAboveLimit.isPassed()).isFalse();
        assertThat(justAboveLimit.isFailed()).isTrue();
    }

    @Test
    void evaluatesExactThresholdBoundariesForLowerBound() {
        var tolerance = Tolerance.lower(-5.0);

        var atLimit = MetricComparison.of(ComparisonMetric.THROUGHPUT, 1000.0, 950.0, tolerance);
        assertThat(atLimit.delta()).isCloseTo(-5.0, within(0.0001));
        assertThat(atLimit.verdict()).isEqualTo(Verdict.PASSED);
        assertThat(atLimit.isPassed()).isTrue();
        assertThat(atLimit.isFailed()).isFalse();

        var justBelowLimit = MetricComparison.of(ComparisonMetric.THROUGHPUT, 1000.0, 949.9, tolerance);
        assertThat(justBelowLimit.delta()).isLessThan(-5.0);
        assertThat(justBelowLimit.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(justBelowLimit.isPassed()).isFalse();
        assertThat(justBelowLimit.isFailed()).isTrue();
    }

    @Test
    void evaluatesLatencyUpperBoundTolerance() {
        var tolerance = Tolerance.upper(5.0);

        var passing = MetricComparison.of(ComparisonMetric.P90, 50.0, 52.0, tolerance);
        assertThat(passing.delta()).isCloseTo(4.0, within(0.0001));
        assertThat(passing.verdict()).isEqualTo(Verdict.PASSED);
        assertThat(passing.isPassed()).isTrue();

        var failing = MetricComparison.of(ComparisonMetric.P90, 50.0, 55.0, tolerance);
        assertThat(failing.delta()).isCloseTo(10.0, within(0.0001));
        assertThat(failing.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(failing.isFailed()).isTrue();
    }

    @Test
    void evaluatesThroughputLowerBoundTolerance() {
        var tolerance = Tolerance.lower(-10.0);

        var passing = MetricComparison.of(ComparisonMetric.THROUGHPUT, 500.0, 480.0, tolerance);
        assertThat(passing.delta()).isCloseTo(-4.0, within(0.0001));
        assertThat(passing.verdict()).isEqualTo(Verdict.PASSED);
        assertThat(passing.isPassed()).isTrue();

        var failing = MetricComparison.of(ComparisonMetric.THROUGHPUT, 500.0, 400.0, tolerance);
        assertThat(failing.delta()).isCloseTo(-20.0, within(0.0001));
        assertThat(failing.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(failing.isFailed()).isTrue();
    }

    @Test
    void computesErrorsInPercentagePointsNotRelativePercentage() {
        // baseline = 2.0%, current = 3.0%: relative change would be +50%, but delta MUST be +1.0 percentage point
        var comparison = MetricComparison.of(ComparisonMetric.ERRORS, 2.0, 3.0, Tolerance.upper(1.5));
        assertThat(comparison.delta()).isEqualTo(1.0);
        assertThat(comparison.verdict()).isEqualTo(Verdict.PASSED);

        var failing = MetricComparison.of(ComparisonMetric.ERRORS, 2.0, 3.0, Tolerance.upper(0.5));
        assertThat(failing.delta()).isEqualTo(1.0);
        assertThat(failing.verdict()).isEqualTo(Verdict.FAILED);

        // Error reduction: baseline = 5.0%, current = 1.0% -> delta = -4.0 percentage points
        var reduction = MetricComparison.of(ComparisonMetric.ERRORS, 5.0, 1.0, Tolerance.upper(0.0));
        assertThat(reduction.delta()).isEqualTo(-4.0);
        assertThat(reduction.verdict()).isEqualTo(Verdict.PASSED);
    }

    @Test
    void handlesBaselineErrorsOfZeroPercent() {
        // Error rate increases from 0.0% to 0.5% -> delta is +0.5 percentage points
        var increaseFromZero = MetricComparison.of(ComparisonMetric.ERRORS, 0.0, 0.5, Tolerance.upper(0.5));
        assertThat(increaseFromZero.delta()).isEqualTo(0.5);
        assertThat(increaseFromZero.verdict()).isEqualTo(Verdict.PASSED);

        var failingIncrease = MetricComparison.of(ComparisonMetric.ERRORS, 0.0, 0.5, Tolerance.upper(0.2));
        assertThat(failingIncrease.delta()).isEqualTo(0.5);
        assertThat(failingIncrease.verdict()).isEqualTo(Verdict.FAILED);

        // Zero baseline and zero current error rate
        var zeroErrors = MetricComparison.of(ComparisonMetric.ERRORS, 0.0, 0.0, Tolerance.upper(0.0));
        assertThat(zeroErrors.delta()).isEqualTo(0.0);
        assertThat(zeroErrors.verdict()).isEqualTo(Verdict.PASSED);
    }

    @Test
    void handlesZeroBaselineWithZeroCurrentValueForStandardMetrics() {
        var comparison = MetricComparison.of(ComparisonMetric.MIN, 0.0, 0.0, Tolerance.upper(10.0));
        assertThat(comparison.delta()).isEqualTo(0.0);
        assertThat(comparison.verdict()).isEqualTo(Verdict.PASSED);

        var lowerTol = MetricComparison.of(ComparisonMetric.THROUGHPUT, 0.0, 0.0, Tolerance.lower(-5.0));
        assertThat(lowerTol.delta()).isEqualTo(0.0);
        assertThat(lowerTol.verdict()).isEqualTo(Verdict.PASSED);

        var unchecked = MetricComparison.of(ComparisonMetric.MAX, 0.0, 0.0);
        assertThat(unchecked.delta()).isEqualTo(0.0);
        assertThat(unchecked.verdict()).isEqualTo(Verdict.UNCHECKED);
    }

    @Test
    void handlesZeroBaselineWithPositiveCurrentValueForStandardMetrics() {
        // Increase from zero cannot be divided by zero; delta is POSITIVE_INFINITY (not NaN)
        var comparison = MetricComparison.of(ComparisonMetric.P99, 0.0, 15.0, Tolerance.upper(10.0));
        assertThat(comparison.delta()).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(Double.isNaN(comparison.delta())).isFalse();
        // Positive upper-bound tolerance MUST fail for an increase from zero
        assertThat(comparison.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(comparison.isFailed()).isTrue();

        // But an infinite increase satisfies a lower bound (e.g. throughput rose from 0 to 15)
        var lowerBound = MetricComparison.of(ComparisonMetric.THROUGHPUT, 0.0, 15.0, Tolerance.lower(-5.0));
        assertThat(lowerBound.delta()).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(lowerBound.verdict()).isEqualTo(Verdict.PASSED);
    }

    @Test
    void producesUncheckedWhenToleranceIsMissing() {
        var unchecked1 = MetricComparison.of(ComparisonMetric.P50, 100.0, 120.0);
        assertThat(unchecked1.tolerance()).isEmpty();
        assertThat(unchecked1.verdict()).isEqualTo(Verdict.UNCHECKED);
        assertThat(unchecked1.isUnchecked()).isTrue();
        assertThat(unchecked1.isPassed()).isFalse();
        assertThat(unchecked1.isFailed()).isFalse();

        var unchecked2 = MetricComparison.of(ComparisonMetric.P50, 100.0, 120.0, (Tolerance) null);
        assertThat(unchecked2.tolerance()).isEmpty();
        assertThat(unchecked2.verdict()).isEqualTo(Verdict.UNCHECKED);

        var unchecked3 = MetricComparison.of(ComparisonMetric.P50, 100.0, 120.0, Optional.empty());
        assertThat(unchecked3.tolerance()).isEmpty();
        assertThat(unchecked3.verdict()).isEqualTo(Verdict.UNCHECKED);
    }

    @Test
    void evaluatesPassingToleranceAsPassedAndFailingAsFailed() {
        var passed = MetricComparison.of(ComparisonMetric.MEAN, 100.0, 102.0, Tolerance.upper(5.0));
        assertThat(passed.verdict()).isEqualTo(Verdict.PASSED);
        assertThat(passed.isPassed()).isTrue();
        assertThat(passed.isFailed()).isFalse();
        assertThat(passed.isUnchecked()).isFalse();

        var failed = MetricComparison.of(ComparisonMetric.MEAN, 100.0, 110.0, Tolerance.upper(5.0));
        assertThat(failed.verdict()).isEqualTo(Verdict.FAILED);
        assertThat(failed.isPassed()).isFalse();
        assertThat(failed.isFailed()).isTrue();
        assertThat(failed.isUnchecked()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ComparisonMetric.class)
    void extractsValuesFromReportModelForAllMetrics(ComparisonMetric metric) {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 250.0, 2.5);
        var current = createReport(12, 22, 105, 20, 42, 85, 98, 260.0, 1.5);

        double expectedBaseline = switch (metric) {
            case MIN -> 10.0;
            case MEAN -> 20.0;
            case MAX -> 100.0;
            case P50 -> 18.0;
            case P90 -> 40.0;
            case P99 -> 80.0;
            case P999 -> 95.0;
            case THROUGHPUT -> 250.0;
            case ERRORS -> 2.5;
        };

        double expectedCurrent = switch (metric) {
            case MIN -> 12.0;
            case MEAN -> 22.0;
            case MAX -> 105.0;
            case P50 -> 20.0;
            case P90 -> 42.0;
            case P99 -> 85.0;
            case P999 -> 98.0;
            case THROUGHPUT -> 260.0;
            case ERRORS -> 1.5;
        };

        assertThat(MetricComparison.valueOf(metric, baseline)).isEqualTo(expectedBaseline);
        assertThat(MetricComparison.valueOf(metric, current)).isEqualTo(expectedCurrent);

        var comparison = MetricComparison.from(metric, baseline, current, Tolerances.empty());
        assertThat(comparison.metric()).isEqualTo(metric);
        assertThat(comparison.baselineValue()).isEqualTo(expectedBaseline);
        assertThat(comparison.currentValue()).isEqualTo(expectedCurrent);
        assertThat(comparison.verdict()).isEqualTo(Verdict.UNCHECKED);
    }

    @Test
    void integratesWithTolerancesEntries() {
        var baseline = createReport(10, 20, 100, 18, 40, 80, 95, 1000.0, 1.0);
        var current = createReport(10, 20, 100, 18, 40, 85, 95, 960.0, 1.4);

        var tolerances = Tolerances.parse("p99:+10%,throughput:-5%,errors:+0.5%");

        // p99: 80 -> 85 (+6.25%), tolerance +10% -> PASSED
        var p99Comp = MetricComparison.from(ComparisonMetric.P99, baseline, current, tolerances);
        assertThat(p99Comp.delta()).isCloseTo(6.25, within(0.001));
        assertThat(p99Comp.verdict()).isEqualTo(Verdict.PASSED);

        // throughput: 1000 -> 960 (-4.0%), tolerance -5% -> PASSED
        var tpComp = MetricComparison.from(ComparisonMetric.THROUGHPUT, baseline, current, tolerances);
        assertThat(tpComp.delta()).isCloseTo(-4.0, within(0.001));
        assertThat(tpComp.verdict()).isEqualTo(Verdict.PASSED);

        // errors: 1.0% -> 1.4% (+0.4 percentage points), tolerance +0.5% -> PASSED
        var errComp = MetricComparison.from(ComparisonMetric.ERRORS, baseline, current, tolerances);
        assertThat(errComp.delta()).isCloseTo(0.4, within(0.001));
        assertThat(errComp.verdict()).isEqualTo(Verdict.PASSED);

        // p50: not in tolerances -> UNCHECKED
        var p50Comp = MetricComparison.from(ComparisonMetric.P50, baseline, current, tolerances);
        assertThat(p50Comp.verdict()).isEqualTo(Verdict.UNCHECKED);
    }

    @Test
    void validatesArguments() {
        assertThatThrownBy(() -> MetricComparison.of(null, 1.0, 2.0))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("metric cannot be null");

        assertThatThrownBy(() -> MetricComparison.calculateDelta(null, 1.0, 2.0))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("metric cannot be null");

        var report = createReport(1, 2, 3, 4, 5, 6, 7, 8.0, 9.0);
        assertThatThrownBy(() -> MetricComparison.valueOf(null, report))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("metric cannot be null");

        assertThatThrownBy(() -> MetricComparison.valueOf(ComparisonMetric.P99, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("report cannot be null");

        assertThatThrownBy(() -> MetricComparison.from(null, report, report, Tolerances.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("metric cannot be null");

        assertThatThrownBy(() -> MetricComparison.from(ComparisonMetric.P99, null, report, Tolerances.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("baseline report cannot be null");

        assertThatThrownBy(() -> MetricComparison.from(ComparisonMetric.P99, report, null, Tolerances.empty()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("current report cannot be null");
    }
}
