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
package io.roadrunner.api.parameters;

import java.util.Collections;

/**
 * Source of parameter data for sampler invocations.
 *
 * <h2>Implementor contract</h2>
 *
 * An implementation loads raw parameter rows (typically from external storage — a CSV file, a
 * database, an HTTP endpoint, etc.) and exposes them as a {@link ParameterFeed} via
 * {@link #load()}. Implementations need <strong>not</strong> worry about:
 * <ul>
 *   <li><b>Thread-safety.</b> The engine drains the returned feed from a single thread before
 *       any sampler thread starts. Subsequent thread-safe distribution to sampler threads is
 *       handled by the engine's preloaded feed.</li>
 *   <li><b>Cycling.</b> When the run requests more samples than the feed produced, the engine's
 *       internal feed cycles through the loaded rows round-robin. Implementations return a
 *       finite feed.</li>
 *   <li><b>Hot-path costs.</b> Per-row allocation and lazy streaming inside {@code load()} are
 *       fine — the rows are copied into a flat array once and the original feed is closed
 *       immediately afterwards.</li>
 * </ul>
 *
 * {@link #load()} is the only point at which I/O may happen. The hot path never re-enters this
 * method.
 */
public interface ParameterSource extends AutoCloseable {

    /**
     * Loads and returns parameter rows. Called once before the benchmark execution loop. The
     * returned feed is drained immediately by the engine and then closed. I/O is permitted
     * here; per-row allocation and lazy streaming are both fine.
     */
    ParameterFeed load() throws Exception;

    default void close() throws Exception {}

    /**
     * Returns a parameters source that produces only empty parameters.
     */
    static ParameterSource onlyEmptyParameters() {
        return () -> () -> Collections.singletonList(SamplerParameters.NONE).iterator();
    }
}
