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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TolerancesTest {

    @Test
    void parsesCanonicalExampleString() {
        var tolerances = Tolerances.parse("p99:10%,throughput:5%,errors:0.5%");

        assertThat(tolerances.size()).isEqualTo(3);
        assertThat(tolerances.isEmpty()).isFalse();
        assertThat(tolerances.get(Metric.P99)).contains(new Tolerance(10.0));
        assertThat(tolerances.get(Metric.THROUGHPUT)).contains(new Tolerance(5.0));
        assertThat(tolerances.get(Metric.ERRORS)).contains(new Tolerance(0.5));
        assertThat(tolerances.get(Metric.MEAN)).isEmpty();
    }

    @Test
    void latencyRegressesWhenItRises() {
        var tolerance = new Tolerance(10.0);
        var p99 = Metric.P99;

        assertThat(tolerance.isSatisfied(p99, 10.0)).isTrue();
        assertThat(tolerance.isSatisfied(p99, -50.0)).isTrue();
        assertThat(tolerance.isSatisfied(p99, 10.1)).isFalse();
        assertThat(tolerance.verdict(p99, 10.0)).isEqualTo(Verdict.PASSED);
        assertThat(tolerance.verdict(p99, 10.1)).isEqualTo(Verdict.FAILED);
    }

    @Test
    void throughputRegressesWhenItDrops() {
        var tolerance = new Tolerance(5.0);
        var throughput = Metric.THROUGHPUT;

        assertThat(tolerance.isSatisfied(throughput, -5.0)).isTrue();
        assertThat(tolerance.isSatisfied(throughput, 80.0)).isTrue();
        assertThat(tolerance.isSatisfied(throughput, -5.1)).isFalse();
        assertThat(tolerance.verdict(throughput, -5.1)).isEqualTo(Verdict.FAILED);
    }

    @Test
    void zeroLimitAcceptsNoRegression() {
        var tolerance = new Tolerance(0.0);

        assertThat(tolerance.isSatisfied(Metric.P99, 0.0)).isTrue();
        assertThat(tolerance.isSatisfied(Metric.P99, 0.01)).isFalse();
        assertThat(tolerance.isSatisfied(Metric.THROUGHPUT, 1.5)).isTrue();
        assertThat(tolerance.isSatisfied(Metric.THROUGHPUT, -0.01)).isFalse();
    }

    @Test
    void toleratesFloatingPointRoundingAtBoundariesWithoutHidingViolations() {
        var tolerance = new Tolerance(0.2);
        // 0.9 - 0.7 = 0.20000000000000007 in double arithmetic
        assertThat(tolerance.isSatisfied(Metric.ERRORS, 0.9 - 0.7)).isTrue();
        assertThat(tolerance.isSatisfied(Metric.ERRORS, 0.2001)).isFalse();
        // and the same for a drop in throughput
        assertThat(tolerance.isSatisfied(Metric.THROUGHPUT, 0.7 - 0.9)).isTrue();
        assertThat(tolerance.isSatisfied(Metric.THROUGHPUT, -0.2001)).isFalse();
    }

    @Test
    void allowsSurroundingWhitespace() {
        var tolerances = Tolerances.parse("  p99  :  10%  ,  throughput  :  5%  ,  errors  :  0.5%  ");

        assertThat(tolerances.size()).isEqualTo(3);
        assertThat(tolerances.get(Metric.P99)).contains(new Tolerance(10.0));
        assertThat(tolerances.get(Metric.THROUGHPUT)).contains(new Tolerance(5.0));
        assertThat(tolerances.get(Metric.ERRORS)).contains(new Tolerance(0.5));
    }

    @Test
    void allowsWhitespaceBeforePercentSuffix() {
        var tolerances = Tolerances.parse("p99: 10 %");
        assertThat(tolerances.get(Metric.P99)).contains(new Tolerance(10.0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "  \t\n  "})
    void nullOrBlankInputProducesEmptyTolerances(String input) {
        var tolerances = Tolerances.parse(input);

        assertThat(tolerances.isEmpty()).isTrue();
        assertThat(tolerances.size()).isZero();
        assertThat(tolerances.get(Metric.P99)).isEmpty();
    }

    @Test
    void rejectsUnknownMetric() {
        assertThatThrownBy(() -> Tolerances.parse("unknown:10%"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown comparison metric 'unknown'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"p99:+10%", "p99:-10%", "throughput:-5%", "throughput:+0%", "p99:-0%"})
    void rejectsSignedLimits(String input) {
        assertThatThrownBy(() -> Tolerances.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("drop the sign");
    }

    @Test
    void rejectsMissingPercentSuffix() {
        assertThatThrownBy(() -> Tolerances.parse("p99:10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("malformed tolerance value '10': missing '%' suffix");
    }

    @ParameterizedTest
    @ValueSource(strings = {"p99:abc%", "p99:1.2.3%"})
    void rejectsInvalidNumericValue(String input) {
        assertThatThrownBy(() -> Tolerances.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid numeric value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"p99:10%,,throughput:5%", ",p99:10%", "p99:10%,", ",", "p99:10%,   ,throughput:5%"})
    void rejectsEmptyEntries(String input) {
        assertThatThrownBy(() -> Tolerances.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty entry is not allowed");
    }

    @Test
    void rejectsDuplicateMetrics() {
        assertThatThrownBy(() -> Tolerances.parse("p99:10%,p99:5%"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate tolerance configuration for metric 'p99'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"p99:NaN%", "p99:Infinity%"})
    void rejectsNanAndInfinityInParser(String input) {
        assertThatThrownBy(() -> Tolerances.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN and Infinity are not allowed");
    }

    @Test
    void rejectsInvalidLimitsInToleranceConstructor() {
        assertThatThrownBy(() -> new Tolerance(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tolerance(Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tolerance(-1.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsImmutableEntriesMap() {
        var tolerances = Tolerances.parse("p99:10%");
        var entries = tolerances.entries();

        assertThatThrownBy(() -> entries.put(Metric.THROUGHPUT, new Tolerance(5.0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"p9910%", "p99", ":10%"})
    void rejectsMissingOrMalformedDelimiters(String input) {
        assertThatThrownBy(() -> Tolerances.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }
}
