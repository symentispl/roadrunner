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

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Generic comparison abstraction for manifest properties.
 *
 * <p>Inspects a baseline and current value of a property. If the values match, no mismatch is
 * produced; if they differ, a human-readable mismatch description is generated.
 *
 * @param <T> the type of property values being compared
 */
@FunctionalInterface
interface PropertyComparison<T> {

    /**
     * Compares the baseline value against the current value.
     *
     * @param baseline the baseline value
     * @param current the current value
     * @return an {@link Optional} containing a mismatch description, or {@link Optional#empty()} if matching
     */
    Optional<String> compare(T baseline, T current);

    /**
     * Creates a comparison for a string property formatted with single quotes: {@code 'value'}.
     *
     * @param propertyName the descriptive name of the property
     * @return a {@link PropertyComparison} instance
     */
    static PropertyComparison<String> quoted(String propertyName) {
        return of(propertyName, v -> "'" + v + "'");
    }

    /**
     * Creates a comparison for a property formatted with {@link String#valueOf}.
     *
     * @param <T> the property value type
     * @param propertyName the descriptive name of the property
     * @return a {@link PropertyComparison} instance
     */
    static <T> PropertyComparison<T> of(String propertyName) {
        return of(propertyName, String::valueOf);
    }

    /**
     * Creates a comparison with a custom value formatter and standard {@link Objects#equals} equality.
     *
     * @param <T> the property value type
     * @param propertyName the descriptive name of the property
     * @param formatter function converting a property value to its display representation
     * @return a {@link PropertyComparison} instance
     */
    static <T> PropertyComparison<T> of(String propertyName, Function<T, String> formatter) {
        return of(propertyName, formatter, Objects::equals);
    }

    /**
     * Creates a comparison with a custom value formatter and a custom equality predicate.
     *
     * @param <T> the property value type
     * @param propertyName the descriptive name of the property
     * @param formatter function converting a property value to its display representation
     * @param equality predicate testing whether baseline and current values are considered equal
     * @return a {@link PropertyComparison} instance
     */
    static <T> PropertyComparison<T> of(
            String propertyName, Function<T, String> formatter, BiPredicate<T, T> equality) {
        requireNonNull(propertyName, "propertyName cannot be null");
        requireNonNull(formatter, "formatter cannot be null");
        requireNonNull(equality, "equality cannot be null");
        return (baseline, current) -> {
            if (equality.test(baseline, current)) {
                return Optional.empty();
            }
            return Optional.of("%s mismatch: baseline used %s, but current used %s"
                    .formatted(propertyName, formatter.apply(baseline), formatter.apply(current)));
        };
    }
}
