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
 * A configured tolerance limit for a benchmark metric and the direction it bounds.
 *
 * <p>A positive limit represents an upper bound: a metric's delta must not exceed the limit.
 * A negative limit represents a lower bound: a metric's delta must not fall below the limit.
 * Zero can be configured with either '+' (upper bound, delta <= 0.0) or '-' (lower bound, delta >= 0.0).
 *
 * @param limit the configured numeric limit threshold
 * @param direction the boundary direction (UPPER or LOWER)
 */
public record Tolerance(double limit, Direction direction) {

    public enum Direction {
        UPPER,
        LOWER
    }

    public Tolerance {
        requireNonNull(direction, "direction cannot be null");
        if (Double.isNaN(limit) || Double.isInfinite(limit)) {
            throw new IllegalArgumentException("tolerance limit cannot be NaN or infinite: %s".formatted(limit));
        }
        // Normalize -0.0 to 0.0 so that value equality and hashing behave consistently
        if (limit == 0.0) {
            limit = 0.0;
        }
    }

    public static Tolerance upper(double limit) {
        return new Tolerance(limit, Direction.UPPER);
    }

    public static Tolerance lower(double limit) {
        return new Tolerance(limit, Direction.LOWER);
    }

    /**
     * Tolerance comparison epsilon used to prevent false rejections at inclusive boundaries
     * due to floating-point rounding errors.
     */
    public static final double EPSILON = 1e-9;

    /**
     * Checks whether the measured delta satisfies this tolerance limit.
     *
     * @param delta the measured change
     * @return true if within tolerance, false if violated
     */
    public boolean isSatisfied(double delta) {
        return switch (direction) {
            case UPPER -> delta <= limit + EPSILON;
            case LOWER -> delta >= limit - EPSILON;
        };
    }

    /**
     * Evaluates this tolerance against the measured delta and returns the corresponding verdict.
     *
     * @param delta the measured change
     * @return {@link Verdict#PASSED} if satisfied, {@link Verdict#FAILED} if violated
     */
    public Verdict verdict(double delta) {
        return isSatisfied(delta) ? Verdict.PASSED : Verdict.FAILED;
    }
}
