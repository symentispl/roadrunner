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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    private static final PropertyComparison<String> SAMPLER = PropertyComparison.quoted("Sampler");
    private static final PropertyComparison<java.util.Map<String, String>> SAMPLER_OPTIONS =
            PropertyComparison.of("Sampler options");
    private static final PropertyComparison<String> LOAD_MODEL = PropertyComparison.quoted("Load model");
    private static final PropertyComparison<String> LOAD_CONCURRENCY = PropertyComparison.quoted("Load concurrency");
    private static final PropertyComparison<String> LOAD_REQUESTS = PropertyComparison.quoted("Load requests");
    private static final PropertyComparison<String> LOAD_RATE = PropertyComparison.quoted("Load rate");
    private static final PropertyComparison<String> LOAD_DURATION = PropertyComparison.quoted("Load duration");
    private static final PropertyComparison<String> PAUSE_DETECTORS = PropertyComparison.of(
            "Pause detectors",
            Comparability::formatPauseDetectors,
            (b, c) -> Objects.equals(normalizePauseDetectors(b), normalizePauseDetectors(c)));

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

        var mismatches = new ArrayList<String>();

        SAMPLER.compare(baseline.sampler(), current.sampler()).ifPresent(mismatches::add);
        SAMPLER_OPTIONS
                .compare(baseline.samplerOptions(), current.samplerOptions())
                .ifPresent(mismatches::add);

        var loadModelMismatch = LOAD_MODEL.compare(baseline.loadModel(), current.loadModel());
        if (loadModelMismatch.isPresent()) {
            mismatches.add(loadModelMismatch.get());
        } else if ("closed".equalsIgnoreCase(baseline.loadModel())) {
            LOAD_CONCURRENCY
                    .compare(baseline.loadConcurrency(), current.loadConcurrency())
                    .ifPresent(mismatches::add);
            LOAD_REQUESTS
                    .compare(baseline.loadRequests(), current.loadRequests())
                    .ifPresent(mismatches::add);
        } else if ("open".equalsIgnoreCase(baseline.loadModel())) {
            LOAD_RATE.compare(baseline.loadRate(), current.loadRate()).ifPresent(mismatches::add);
            LOAD_DURATION
                    .compare(baseline.loadDuration(), current.loadDuration())
                    .ifPresent(mismatches::add);
        }

        PAUSE_DETECTORS
                .compare(baseline.pauseDetectors(), current.pauseDetectors())
                .ifPresent(mismatches::add);

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
