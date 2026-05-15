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
package io.roadrunner.api.latency;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Records per-request latency samples and, optionally, pause-corrected samples.
 * Implementations may attach pause detectors that fill in synthetic samples when
 * the load generator itself stalls.
 *
 * <p>{@link #record} is hot-path; implementations must be wait-free and thread-safe.
 * {@link #writeSnapshot} is called once at the end of a run.
 */
public interface LatencyRecorder extends AutoCloseable {

    /**
     * Record a successful request's corrected latency, in nanoseconds.
     */
    void record(long correctedLatencyNanos);

    /**
     * Persist the recorder's histogram into {@code outputDir/latency.hgrm}.
     * Called once at end of run. Implementations should tolerate being called before
     * any {@link #record} calls (empty histogram is fine).
     */
    void writeSnapshot(Path outputDir) throws IOException;

    /**
     * Release detector threads and other resources. Idempotent.
     */
    @Override
    void close();

    /**
     * A recorder whose {@link #record} is a no-op and that writes nothing.
     * Used when no pause detectors are configured.
     */
    static LatencyRecorder noop() {
        return NoopLatencyRecorder.INSTANCE;
    }

    final class NoopLatencyRecorder implements LatencyRecorder {
        static final NoopLatencyRecorder INSTANCE = new NoopLatencyRecorder();

        private NoopLatencyRecorder() {}

        @Override
        public void record(long correctedLatencyNanos) {}

        @Override
        public void writeSnapshot(Path outputDir) {}

        @Override
        public void close() {}
    }
}
