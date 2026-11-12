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

/**
 * The largest regression tolerated for a benchmark metric.
 *
 * <p>Which way is a regression is a property of the metric (see {@link Metric#higherIsBetter()}), so the
 * limit is a plain non-negative magnitude: latency or errors rising, or throughput falling, by more than the limit
 * violates it. Improvements always satisfy it.
 *
 * @param limit the largest tolerated regression, in percent (percentage points for errors)
 */
public record Tolerance(double limit) {

    public Tolerance {
        if (Double.isNaN(limit) || Double.isInfinite(limit) || limit < 0) {
            throw new IllegalArgumentException(
                    "tolerance limit must be a finite non-negative number: %s".formatted(limit));
        }
        // Normalize -0.0 to 0.0 so that value equality and hashing behave consistently
        if (limit == 0.0) {
            limit = 0.0;
        }
    }

    /**
     * Tolerance comparison epsilon used to prevent false rejections at inclusive boundaries
     * due to floating-point rounding errors.
     */
    public static final double EPSILON = 1e-9;

    /**
     * Checks whether the measured delta of the metric stays within this tolerance.
     *
     * @param metric the metric the delta was measured for
     * @param delta the measured change
     * @return true if within tolerance, false if violated
     */
    public boolean isSatisfied(Metric metric, double delta) {
        var regression = metric.higherIsBetter() ? -delta : delta;
        return regression <= limit + EPSILON;
    }

    /**
     * Evaluates this tolerance against the measured delta and returns the corresponding verdict.
     *
     * @return {@link Verdict#PASSED} if satisfied, {@link Verdict#FAILED} if violated
     */
    public Verdict verdict(Metric metric, double delta) {
        return isSatisfied(metric, delta) ? Verdict.PASSED : Verdict.FAILED;
    }
}
