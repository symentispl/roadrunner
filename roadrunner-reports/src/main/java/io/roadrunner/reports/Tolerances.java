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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable collection of metric tolerance limits parsed from configuration (e.g. {@code --tolerance}).
 *
 * <p>Negative zero (e.g. {@code throughput:-0%} or {@code errors:-0.0%}) is accepted and treated as a lower-bound
 * limit of 0.0%. Because the sign declares the direction bounded, '-' designates a lower bound (failing if delta < 0.0),
 * while the numeric limit is normalized to 0.0 to prevent IEEE 754 negative-zero comparison anomalies.
 *
 * @param entries the immutable map of metric tolerances
 */
public record Tolerances(Map<ComparisonMetric, Tolerance> entries) {

    private static final Tolerances EMPTY = new Tolerances(Map.of());

    public Tolerances {
        entries = Map.copyOf(entries);
    }

    public static Tolerances empty() {
        return EMPTY;
    }

    public Optional<Tolerance> get(ComparisonMetric metric) {
        return Optional.ofNullable(entries.get(metric));
    }

    public boolean contains(ComparisonMetric metric) {
        return entries.containsKey(metric);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /**
     * Parses a tolerance specification string into a {@link Tolerances} instance.
     *
     * <p>Expected format: {@code "<metric>:<sign><number>%,..."} (e.g. {@code "p99:+10%,throughput:-5%,errors:+0.5%"}).
     * Surrounding whitespace around entries, metric names, colons, and values is ignored.
     * Null or blank input returns {@link #empty()}.
     *
     * @param input the tolerance configuration string
     * @return the parsed immutable Tolerances configuration
     * @throws IllegalArgumentException if the configuration is malformed, contains duplicate or unknown metrics,
     *                                  is missing signs, percent suffixes, or valid numeric values
     */
    public static Tolerances parse(String input) {
        if (input == null || input.isBlank()) {
            return empty();
        }

        var map = new LinkedHashMap<ComparisonMetric, Tolerance>();
        var tokens = input.split(",", -1);

        for (var rawToken : tokens) {
            var token = rawToken.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException(
                        "malformed tolerance entry in '%s': empty entry is not allowed".formatted(input));
            }

            var colonIdx = token.indexOf(':');
            if (colonIdx == -1) {
                throw new IllegalArgumentException(
                        "malformed tolerance entry '%s': missing ':' delimiter".formatted(token));
            }
            if (token.indexOf(':', colonIdx + 1) != -1) {
                throw new IllegalArgumentException(
                        "malformed tolerance entry '%s': multiple ':' delimiters".formatted(token));
            }

            var metricLabel = token.substring(0, colonIdx).trim();
            if (metricLabel.isEmpty()) {
                throw new IllegalArgumentException(
                        "malformed tolerance entry '%s': missing metric name".formatted(token));
            }

            var metric = ComparisonMetric.fromLabel(metricLabel);
            if (map.containsKey(metric)) {
                throw new IllegalArgumentException(
                        "duplicate tolerance configuration for metric '%s'".formatted(metric.label()));
            }

            var valueStr = token.substring(colonIdx + 1).trim();
            if (valueStr.isEmpty()) {
                throw new IllegalArgumentException("malformed tolerance entry '%s': missing value".formatted(token));
            }

            if (!valueStr.endsWith("%")) {
                throw new IllegalArgumentException(
                        "malformed tolerance value '%s': missing '%%' suffix".formatted(valueStr));
            }

            var numericStr = valueStr.substring(0, valueStr.length() - 1).trim();
            if (numericStr.isEmpty()) {
                throw new IllegalArgumentException(
                        "malformed tolerance value '%s': empty numeric part".formatted(valueStr));
            }

            var sign = numericStr.charAt(0);
            if (sign != '+' && sign != '-') {
                throw new IllegalArgumentException(
                        "malformed tolerance value '%s': missing sign (+ or -)".formatted(valueStr));
            }

            double numericValue;
            try {
                numericValue = Double.parseDouble(numericStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "malformed tolerance value '%s': invalid numeric value".formatted(valueStr), e);
            }

            if (Double.isNaN(numericValue) || Double.isInfinite(numericValue)) {
                throw new IllegalArgumentException(
                        "malformed tolerance value '%s': NaN and Infinity are not allowed".formatted(valueStr));
            }

            var direction = sign == '+' ? Tolerance.Direction.UPPER : Tolerance.Direction.LOWER;
            map.put(metric, new Tolerance(numericValue, direction));
        }

        return new Tolerances(map);
    }
}
