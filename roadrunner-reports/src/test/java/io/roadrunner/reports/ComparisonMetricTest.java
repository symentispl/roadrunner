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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ComparisonMetricTest {

    @ParameterizedTest
    @CsvSource({
        "min, MIN",
        "mean, MEAN",
        "max, MAX",
        "p50, P50",
        "p90, P90",
        "p99, P99",
        "p999, P999",
        "throughput, THROUGHPUT",
        "errors, ERRORS"
    })
    void parsesValidLabels(String label, ComparisonMetric expected) {
        var metric = ComparisonMetric.fromLabel(label);
        assertThat(metric).isEqualTo(expected);
        assertThat(metric.label()).isEqualTo(label);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "unknown", "p95", "latency", "MIN", "Throughput", "error"})
    void rejectsUnknownLabels(String invalidLabel) {
        assertThatThrownBy(() -> ComparisonMetric.fromLabel(invalidLabel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "unknown comparison metric '%s', expected one of: min, mean, max, p50, p90, p99, p999, throughput, errors",
                        invalidLabel);
    }

    @Test
    void rejectsNullLabel() {
        assertThatThrownBy(() -> ComparisonMetric.fromLabel(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metric label cannot be null");
    }

    @Test
    void hasExactlyNineMetrics() {
        assertThat(ComparisonMetric.values()).hasSize(9);
    }
}
