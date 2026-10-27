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

import java.util.Map;
import org.junit.jupiter.api.Test;

class PropertyComparisonTest {

    @Test
    void producesEmptyWhenValuesAreEqual() {
        var comparison = PropertyComparison.quoted("Test");
        assertThat(comparison.compare("value", "value")).isEmpty();
    }

    @Test
    void producesDescriptiveMismatchWhenValuesDiffer() {
        var comparison = PropertyComparison.quoted("Sampler");
        assertThat(comparison.compare("http", "vm"))
                .contains("Sampler mismatch: baseline used 'http', but current used 'vm'");
    }

    @Test
    void formatsNonStringValuesUsingToString() {
        var comparison = PropertyComparison.<Map<String, String>>of("Sampler options");
        assertThat(comparison.compare(Map.of("k", "v1"), Map.of("k", "v2")))
                .contains("Sampler options mismatch: baseline used {k=v1}, but current used {k=v2}");
    }

    @Test
    void supportsCustomFormatterAndEqualityPredicate() {
        var comparison = PropertyComparison.<String>of(
                "Pause detectors",
                v -> "'" + (v == null || v.isEmpty() ? "none" : v) + "'",
                (b, c) -> (b == null || b.isEmpty()) && (c == null || c.isEmpty()));

        assertThat(comparison.compare("", null)).isEmpty();
        assertThat(comparison.compare("vt", "jvm"))
                .contains("Pause detectors mismatch: baseline used 'vt', but current used 'jvm'");
    }

    @Test
    void validatesNullArguments() {
        assertThatThrownBy(() -> PropertyComparison.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("propertyName cannot be null");
        assertThatThrownBy(() -> PropertyComparison.of("name", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("formatter cannot be null");
        assertThatThrownBy(() -> PropertyComparison.of("name", String::valueOf, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("equality cannot be null");
    }
}
