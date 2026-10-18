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
        var tolerances = Tolerances.parse("p99:+10%,throughput:-5%,errors:+0.5%");

        assertThat(tolerances.size()).isEqualTo(3);
        assertThat(tolerances.isEmpty()).isFalse();

        var p99 = tolerances.get(ComparisonMetric.P99).orElseThrow();
        assertThat(p99.limit()).isEqualTo(10.0);
        assertThat(p99.direction()).isEqualTo(Tolerance.Direction.UPPER);

        var throughput = tolerances.get(ComparisonMetric.THROUGHPUT).orElseThrow();
        assertThat(throughput.limit()).isEqualTo(-5.0);
        assertThat(throughput.direction()).isEqualTo(Tolerance.Direction.LOWER);

        var errors = tolerances.get(ComparisonMetric.ERRORS).orElseThrow();
        assertThat(errors.limit()).isEqualTo(0.5);
        assertThat(errors.direction()).isEqualTo(Tolerance.Direction.UPPER);

        assertThat(tolerances.get(ComparisonMetric.MEAN)).isEmpty();
    }

    @Test
    void evaluatesUpperBoundTolerance() {
        var tolerance = Tolerance.upper(10.0);

        assertThat(tolerance.direction()).isEqualTo(Tolerance.Direction.UPPER);
        assertThat(tolerance.limit()).isEqualTo(10.0);

        assertThat(tolerance.isSatisfied(9.9)).isTrue();
        assertThat(tolerance.isSatisfied(10.0)).isTrue();
        assertThat(tolerance.isSatisfied(10.1)).isFalse();

        assertThat(tolerance.verdict(10.0)).isEqualTo(Verdict.PASSED);
        assertThat(tolerance.verdict(10.1)).isEqualTo(Verdict.FAILED);
    }

    @Test
    void evaluatesLowerBoundTolerance() {
        var tolerance = Tolerance.lower(-5.0);

        assertThat(tolerance.direction()).isEqualTo(Tolerance.Direction.LOWER);
        assertThat(tolerance.limit()).isEqualTo(-5.0);

        assertThat(tolerance.isSatisfied(-4.9)).isTrue();
        assertThat(tolerance.isSatisfied(-5.0)).isTrue();
        assertThat(tolerance.isSatisfied(-5.1)).isFalse();

        assertThat(tolerance.verdict(-5.0)).isEqualTo(Verdict.PASSED);
        assertThat(tolerance.verdict(-5.1)).isEqualTo(Verdict.FAILED);
    }

    @Test
    void allowsSurroundingWhitespace() {
        var tolerances = Tolerances.parse("  p99  :  +10%  ,  throughput  :  -5%  ,  errors  :  +0.5%  ");

        assertThat(tolerances.size()).isEqualTo(3);
        assertThat(tolerances.get(ComparisonMetric.P99)).contains(Tolerance.upper(10.0));
        assertThat(tolerances.get(ComparisonMetric.THROUGHPUT)).contains(Tolerance.lower(-5.0));
        assertThat(tolerances.get(ComparisonMetric.ERRORS)).contains(Tolerance.upper(0.5));
    }

    @Test
    void allowsWhitespaceBeforePercentSuffix() {
        var tolerances = Tolerances.parse("p99: +10 %");
        assertThat(tolerances.get(ComparisonMetric.P99)).contains(Tolerance.upper(10.0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "  \t\n  "})
    void nullOrBlankInputProducesEmptyTolerances(String input) {
        var tolerances = Tolerances.parse(input);

        assertThat(tolerances.isEmpty()).isTrue();
        assertThat(tolerances.size()).isZero();
        assertThat(tolerances.get(ComparisonMetric.P99)).isEmpty();
    }

    @Test
    void rejectsUnknownMetric() {
        assertThatThrownBy(() -> Tolerances.parse("unknown:+10%"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown comparison metric 'unknown'");
    }

    @Test
    void rejectsMissingSign() {
        assertThatThrownBy(() -> Tolerances.parse("p99:10%"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("malformed tolerance value '10%': missing sign (+ or -)");
    }

    @Test
    void rejectsMissingPercentSuffix() {
        assertThatThrownBy(() -> Tolerances.parse("p99:+10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("malformed tolerance value '+10': missing '%' suffix");
    }

    @ParameterizedTest
    @ValueSource(strings = {"p99:+abc%", "p99:+%", "p99:-%", "p99:+1.2.3%"})
    void rejectsInvalidNumericValue(String input) {
        assertThatThrownBy(() -> Tolerances.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid numeric value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"p99:+10%,,throughput:-5%", ",p99:+10%", "p99:+10%,", ",", "p99:+10%,   ,throughput:-5%"})
    void rejectsEmptyEntries(String input) {
        assertThatThrownBy(() -> Tolerances.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty entry is not allowed");
    }

    @Test
    void rejectsDuplicateMetrics() {
        assertThatThrownBy(() -> Tolerances.parse("p99:+10%,p99:+5%"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate tolerance configuration for metric 'p99'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"p99:+NaN%", "p99:-NaN%", "p99:+Infinity%", "p99:-Infinity%"})
    void rejectsNanAndInfinityInParser(String input) {
        assertThatThrownBy(() -> Tolerances.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN and Infinity are not allowed");
    }

    @Test
    void rejectsNanAndInfinityInToleranceConstructor() {
        assertThatThrownBy(() -> new Tolerance(Double.NaN, Tolerance.Direction.UPPER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN or infinite");

        assertThatThrownBy(() -> new Tolerance(Double.POSITIVE_INFINITY, Tolerance.Direction.LOWER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NaN or infinite");
    }

    @Test
    void acceptsNegativeZeroAsLowerBoundAtZero() {
        var tolerances = Tolerances.parse("throughput:-0%");
        var tolerance = tolerances.get(ComparisonMetric.THROUGHPUT).orElseThrow();

        assertThat(tolerance.direction()).isEqualTo(Tolerance.Direction.LOWER);
        assertThat(tolerance.limit()).isEqualTo(0.0);

        // Lower bound at 0: must not decrease (delta < 0 is failure)
        assertThat(tolerance.isSatisfied(0.0)).isTrue();
        assertThat(tolerance.isSatisfied(1.5)).isTrue();
        assertThat(tolerance.isSatisfied(-0.01)).isFalse();
        assertThat(tolerance.verdict(-0.01)).isEqualTo(Verdict.FAILED);
        assertThat(tolerance.verdict(0.0)).isEqualTo(Verdict.PASSED);
    }

    @Test
    void acceptsPositiveZeroAsUpperBoundAtZero() {
        var tolerances = Tolerances.parse("p99:+0.0%");
        var tolerance = tolerances.get(ComparisonMetric.P99).orElseThrow();

        assertThat(tolerance.direction()).isEqualTo(Tolerance.Direction.UPPER);
        assertThat(tolerance.limit()).isEqualTo(0.0);

        // Upper bound at 0: must not increase (delta > 0 is failure)
        assertThat(tolerance.isSatisfied(0.0)).isTrue();
        assertThat(tolerance.isSatisfied(-1.5)).isTrue();
        assertThat(tolerance.isSatisfied(0.01)).isFalse();
        assertThat(tolerance.verdict(0.01)).isEqualTo(Verdict.FAILED);
        assertThat(tolerance.verdict(0.0)).isEqualTo(Verdict.PASSED);
    }

    @Test
    void returnsImmutableEntriesMap() {
        var tolerances = Tolerances.parse("p99:+10%");
        var entries = tolerances.entries();

        assertThatThrownBy(() -> entries.put(ComparisonMetric.THROUGHPUT, Tolerance.lower(-5.0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"p99+10%", "p99", ":+10%"})
    void rejectsMissingOrMalformedDelimiters(String input) {
        assertThatThrownBy(() -> Tolerances.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }
}
