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

import io.roadrunner.shaded.hdrhistogram.EncodableHistogram;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import io.roadrunner.shaded.hdrhistogram.HistogramLogReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The layout of a run's output directory. Every report is built from one of these, so the file
 * names live here rather than in each report generator.
 */
public final class RunDirectory {

    private static final String OUTPUT_CSV = "output.csv";
    private static final String LATENCY_SNAPSHOT = "latency.hgrm";

    private final Path dir;

    private RunDirectory(Path dir) {
        this.dir = requireNonNull(dir, "run directory cannot be null");
    }

    public static RunDirectory of(Path dir) {
        return new RunDirectory(dir);
    }

    public Path path() {
        return dir;
    }

    public Path outputCsv() {
        return dir.resolve(OUTPUT_CSV);
    }

    /**
     * What the run was: sampler, load model, environment. {@link RunManifest#UNKNOWN} if this run
     * directory has no manifest — an older run is not orphaned, its fields just render unknown.
     */
    public RunManifest manifest() throws IOException {
        return RunManifest.readFrom(dir);
    }

    /**
     * The pause-corrected latencies recorded during the run, if the run recorded any. Package
     * private on purpose: which file the latencies come from is between this class and
     * {@link ReportModel}, and no renderer should have to know.
     *
     * <p>The snapshot is a log of interval histograms; they are merged into one, so the result
     * carries the run's latency distribution but no time dimension.
     */
    Optional<Histogram> latencySnapshot() throws IOException {
        var snapshot = dir.resolve(LATENCY_SNAPSHOT);
        if (!Files.isRegularFile(snapshot)) {
            return Optional.empty();
        }
        var combined = new Histogram(1_000L, 3_600_000_000_000L, 3);
        try (var reader = new HistogramLogReader(snapshot.toFile())) {
            EncodableHistogram next;
            while ((next = reader.nextIntervalHistogram()) != null) {
                if (next instanceof Histogram h) {
                    combined.add(h);
                }
            }
        }
        return Optional.of(combined);
    }
}
