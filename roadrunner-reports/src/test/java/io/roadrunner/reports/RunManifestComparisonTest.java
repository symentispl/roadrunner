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

class RunManifestComparisonTest {

    @Test
    void producesNoMismatchesWhenValuesAreEqual() {
        var comparison = RunManifestComparison.quoted("Test", RunManifest::sampler);
        assertThat(comparison.compare(manifest("value", Map.of()), manifest("value", Map.of())))
                .isEmpty();
    }

    @Test
    void producesDescriptiveMismatchWhenValuesDiffer() {
        var comparison = RunManifestComparison.quoted("Sampler", RunManifest::sampler);
        assertThat(comparison.compare(manifest("http", Map.of()), manifest("vm", Map.of())))
                .containsExactly("Sampler mismatch: baseline used 'http', but current used 'vm'");
    }

    @Test
    void formatsNonStringValuesUsingToString() {
        var comparison = RunManifestComparison.of("Sampler options", RunManifest::samplerOptions);
        assertThat(comparison.compare(manifest("s", Map.of("k", "v1")), manifest("s", Map.of("k", "v2"))))
                .containsExactly("Sampler options mismatch: baseline used {k=v1}, but current used {k=v2}");
    }

    @Test
    void supportsCustomFormatterAndEqualityPredicate() {
        var comparison = RunManifestComparison.<String>of(
                "Pause detectors",
                RunManifest::pauseDetectors,
                v -> "'" + (v == null || v.isEmpty() ? "none" : v) + "'",
                (b, c) -> (b == null || b.isEmpty()) && (c == null || c.isEmpty()));

        assertThat(comparison.compare(withPauseDetectors(""), withPauseDetectors(null)))
                .isEmpty();
        assertThat(comparison.compare(withPauseDetectors("vt"), withPauseDetectors("jvm")))
                .containsExactly("Pause detectors mismatch: baseline used 'vt', but current used 'jvm'");
    }

    @Test
    void onlyIfSkipsComparisonWhenConditionIsFalse() {
        var comparison = RunManifestComparison.quoted("Sampler", RunManifest::sampler)
                .onlyIf((b, c) -> false);
        assertThat(comparison.compare(manifest("http", Map.of()), manifest("vm", Map.of())))
                .isEmpty();
    }

    @Test
    void validatesNullArguments() {
        assertThatThrownBy(() -> RunManifestComparison.of(null, RunManifest::sampler))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("propertyName cannot be null");
        assertThatThrownBy(() -> RunManifestComparison.of("name", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("property cannot be null");
        assertThatThrownBy(() -> RunManifestComparison.of("name", RunManifest::sampler, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("formatter cannot be null");
        assertThatThrownBy(() -> RunManifestComparison.of("name", RunManifest::sampler, String::valueOf, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("equality cannot be null");
    }

    private static RunManifest withPauseDetectors(String pauseDetectors) {
        return manifest("s", Map.of(), pauseDetectors);
    }

    private static RunManifest manifest(String sampler, Map<String, String> samplerOptions) {
        return manifest(sampler, samplerOptions, "");
    }

    private static RunManifest manifest(String sampler, Map<String, String> samplerOptions, String pauseDetectors) {
        return new RunManifest(
                "v", "t", sampler, samplerOptions, "closed", "1", "1", "1", "1", pauseDetectors, "p", "j", "a", "o",
                "ov", "arch", "1", "1");
    }
}
