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

/**
 * Result of comparing baseline and current {@link RunManifest} instances to determine whether two
 * benchmark runs are comparable.
 *
 * <p>Two runs are considered comparable when they use the same sampler, sampler options, load model,
 * relevant load model parameters, and pause detectors. A mismatch does not block the comparison; it
 * is recorded here so reports can present it prominently.
 *
 * @param isComparable true if all checked configuration fields match, false if any mismatch exists
 * @param mismatches an immutable list of human-readable mismatch descriptions (empty if comparable)
 */
public record Comparability(boolean isComparable, List<String> mismatches) {

    private static final Comparability COMPATIBLE = new Comparability(true, List.of());

    private static final BiPredicate<RunManifest, RunManifest> SAME_CLOSED_MODEL =
            (b, c) -> Objects.equals(b.loadModel(), c.loadModel()) && "closed".equalsIgnoreCase(b.loadModel());
    private static final BiPredicate<RunManifest, RunManifest> SAME_OPEN_MODEL =
            (b, c) -> Objects.equals(b.loadModel(), c.loadModel()) && "open".equalsIgnoreCase(b.loadModel());

    private static final List<RunManifestComparison> COMPARISONS = List.of(
            RunManifestComparison.quoted("Sampler", RunManifest::sampler),
            RunManifestComparison.of("Sampler options", RunManifest::samplerOptions),
            RunManifestComparison.quoted("Load model", RunManifest::loadModel),
            RunManifestComparison.quoted("Load concurrency", RunManifest::loadConcurrency)
                    .onlyIf(SAME_CLOSED_MODEL),
            RunManifestComparison.quoted("Load requests", RunManifest::loadRequests).onlyIf(SAME_CLOSED_MODEL),
            RunManifestComparison.quoted("Load rate", RunManifest::loadRate).onlyIf(SAME_OPEN_MODEL),
            RunManifestComparison.quoted("Load duration", RunManifest::loadDuration)
                    .onlyIf(SAME_OPEN_MODEL),
            RunManifestComparison.of(
                    "Pause detectors",
                    RunManifest::pauseDetectors,
                    Comparability::formatPauseDetectors,
                    (b, c) -> Objects.equals(normalizePauseDetectors(b), normalizePauseDetectors(c))));

    public Comparability {
        requireNonNull(mismatches, "mismatches cannot be null");
        mismatches = List.copyOf(mismatches);
    }

    public static Comparability compatible() {
        return COMPATIBLE;
    }

    public static Comparability incompatible(List<String> mismatches) {
        return new Comparability(false, mismatches);
    }

    /**
     * Compares baseline and current run manifests.
     *
     * <p>If either manifest is missing or null, the comparison is marked non-comparable and records
     * an explanatory mismatch message without throwing.
     *
     * @param baseline the baseline run manifest (nullable)
     * @param current the current run manifest (nullable)
     * @return a {@link Comparability} result indicating compatibility and any recorded mismatches
     */
    public static Comparability compare(RunManifest baseline, RunManifest current) {
        if (baseline == null && current == null) {
            return incompatible(List.of("Baseline and current run manifests are missing"));
        }
        if (baseline == null) {
            return incompatible(List.of("Baseline run manifest is missing"));
        }
        if (current == null) {
            return incompatible(List.of("Current run manifest is missing"));
        }

        var mismatches = COMPARISONS.stream()
                .flatMap(comparison -> comparison.compare(baseline, current).stream())
                .toList();

        if (mismatches.isEmpty()) {
            return compatible();
        }
        return incompatible(mismatches);
    }

    private static String formatPauseDetectors(String value) {
        return "'" + normalizePauseDetectors(value) + "'";
    }

    private static String normalizePauseDetectors(String value) {
        return value == null || value.isEmpty() ? "none" : value;
    }
}
