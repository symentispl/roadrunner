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

import java.util.Arrays;

/**
 * The canonical metrics eligible for baseline-versus-current comparison and tolerance verdicts.
 */
public enum Metric {
    MIN("min"),
    MEAN("mean"),
    MAX("max"),
    P50("p50"),
    P90("p90"),
    P99("p99"),
    P999("p999"),
    THROUGHPUT("throughput", true),
    ERRORS("errors");

    private final String label;
    private final boolean higherIsBetter;

    Metric(String label) {
        this(label, false);
    }

    Metric(String label, boolean higherIsBetter) {
        this.label = label;
        this.higherIsBetter = higherIsBetter;
    }

    /** Whether a bigger value is an improvement (throughput) rather than a regression (latency, errors). */
    public boolean higherIsBetter() {
        return higherIsBetter;
    }

    /** Whether a change of the given delta is an improvement for this metric. */
    public boolean isImprovement(double delta) {
        return higherIsBetter ? delta > 0 : delta < 0;
    }

    public String label() {
        return label;
    }

    public static Metric fromLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("metric label cannot be null");
        }
        return Arrays.stream(values())
                .filter(metric -> metric.label.equals(label))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown comparison metric '%s', expected one of: min, mean, max, p50, p90, p99, p999, throughput, errors"
                                .formatted(label)));
    }
}
