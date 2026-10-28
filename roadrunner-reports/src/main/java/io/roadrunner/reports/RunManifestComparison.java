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

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Comparison of a single property of two {@link RunManifest} instances.
 *
 * <p>Extracts a property from the baseline and current manifests. If the values match, no mismatches
 * are produced; if they differ, a human-readable mismatch description is returned.
 */
@FunctionalInterface
interface RunManifestComparison {

    /**
     * Compares the baseline manifest against the current manifest.
     *
     * @param baseline the baseline run manifest
     * @param current the current run manifest
     * @return mismatch descriptions, empty if the compared property matches
     */
    List<String> compare(RunManifest baseline, RunManifest current);

    /**
     * Restricts this comparison to manifest pairs satisfying a condition; otherwise it yields no
     * mismatches.
     *
     * @param condition tested with baseline and current manifests
     * @return a conditional {@link RunManifestComparison}
     */
    default RunManifestComparison onlyIf(BiPredicate<RunManifest, RunManifest> condition) {
        requireNonNull(condition, "condition cannot be null");
        return (baseline, current) -> condition.test(baseline, current) ? compare(baseline, current) : List.of();
    }

    /**
     * Creates a comparison for a string property formatted with single quotes: {@code 'value'}.
     *
     * @param propertyName the descriptive name of the property
     * @param property extracts the property from a manifest
     * @return a {@link RunManifestComparison} instance
     */
    static RunManifestComparison quoted(String propertyName, Function<RunManifest, String> property) {
        return of(propertyName, property, v -> "'" + v + "'");
    }

    /**
     * Creates a comparison for a property formatted with {@link String#valueOf}.
     *
     * @param <T> the property value type
     * @param propertyName the descriptive name of the property
     * @param property extracts the property from a manifest
     * @return a {@link RunManifestComparison} instance
     */
    static <T> RunManifestComparison of(String propertyName, Function<RunManifest, T> property) {
        return of(propertyName, property, String::valueOf);
    }

    /**
     * Creates a comparison with a custom value formatter and standard {@link Objects#equals} equality.
     *
     * @param <T> the property value type
     * @param propertyName the descriptive name of the property
     * @param property extracts the property from a manifest
     * @param formatter function converting a property value to its display representation
     * @return a {@link RunManifestComparison} instance
     */
    static <T> RunManifestComparison of(
            String propertyName, Function<RunManifest, T> property, Function<T, String> formatter) {
        return of(propertyName, property, formatter, Objects::equals);
    }

    /**
     * Creates a comparison with a custom value formatter and a custom equality predicate.
     *
     * @param <T> the property value type
     * @param propertyName the descriptive name of the property
     * @param property extracts the property from a manifest
     * @param formatter function converting a property value to its display representation
     * @param equality predicate testing whether baseline and current values are considered equal
     * @return a {@link RunManifestComparison} instance
     */
    static <T> RunManifestComparison of(
            String propertyName,
            Function<RunManifest, T> property,
            Function<T, String> formatter,
            BiPredicate<T, T> equality) {
        requireNonNull(propertyName, "propertyName cannot be null");
        requireNonNull(property, "property cannot be null");
        requireNonNull(formatter, "formatter cannot be null");
        requireNonNull(equality, "equality cannot be null");
        return (baselineManifest, currentManifest) -> {
            var baseline = property.apply(baselineManifest);
            var current = property.apply(currentManifest);
            if (equality.test(baseline, current)) {
                return List.of();
            }
            return List.of("%s mismatch: baseline used %s, but current used %s"
                    .formatted(propertyName, formatter.apply(baseline), formatter.apply(current)));
        };
    }
}
