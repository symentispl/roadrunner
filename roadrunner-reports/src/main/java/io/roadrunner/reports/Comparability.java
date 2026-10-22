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

        // 1. Sampler
        if (!Objects.equals(baseline.sampler(), current.sampler())) {
            mismatches.add("Sampler mismatch: baseline used '%s', but current used '%s'"
                    .formatted(baseline.sampler(), current.sampler()));
        }

        // 2. Sampler options
        if (!Objects.equals(baseline.samplerOptions(), current.samplerOptions())) {
            mismatches.add("Sampler options mismatch: baseline used %s, but current used %s"
                    .formatted(baseline.samplerOptions(), current.samplerOptions()));
        }

        // 3. Load model & relevant parameters
        if (!Objects.equals(baseline.loadModel(), current.loadModel())) {
            mismatches.add("Load model mismatch: baseline used '%s', but current used '%s'"
                    .formatted(baseline.loadModel(), current.loadModel()));
        } else {
            if ("closed".equalsIgnoreCase(baseline.loadModel())) {
                if (!Objects.equals(baseline.loadConcurrency(), current.loadConcurrency())) {
                    mismatches.add("Load concurrency mismatch: baseline used '%s', but current used '%s'"
                            .formatted(baseline.loadConcurrency(), current.loadConcurrency()));
                }
                if (!Objects.equals(baseline.loadRequests(), current.loadRequests())) {
                    mismatches.add("Load requests mismatch: baseline used '%s', but current used '%s'"
                            .formatted(baseline.loadRequests(), current.loadRequests()));
                }
            } else if ("open".equalsIgnoreCase(baseline.loadModel())) {
                if (!Objects.equals(baseline.loadRate(), current.loadRate())) {
                    mismatches.add("Load rate mismatch: baseline used '%s', but current used '%s'"
                            .formatted(baseline.loadRate(), current.loadRate()));
                }
                if (!Objects.equals(baseline.loadDuration(), current.loadDuration())) {
                    mismatches.add("Load duration mismatch: baseline used '%s', but current used '%s'"
                            .formatted(baseline.loadDuration(), current.loadDuration()));
                }
            }
        }

        // 4. Pause detectors
        if (!Objects.equals(baseline.pauseDetectors(), current.pauseDetectors())) {
            var b = baseline.pauseDetectors() == null
                            || baseline.pauseDetectors().isEmpty()
                    ? "none"
                    : baseline.pauseDetectors();
            var c = current.pauseDetectors() == null || current.pauseDetectors().isEmpty()
                    ? "none"
                    : current.pauseDetectors();
            mismatches.add("Pause detectors mismatch: baseline used '%s', but current used '%s'".formatted(b, c));
        }

        if (mismatches.isEmpty()) {
            return compatible();
        }
        return incompatible(mismatches);
    }
}
