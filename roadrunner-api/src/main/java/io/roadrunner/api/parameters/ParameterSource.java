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

/**
 * Source of parameter data for sampler invocations.
 * <p>
 * Responsible only for loading raw data — buffering, prefetching, and
 * thread-safe delivery are handled separately by the execution engine.
 * <p>
 * {@link #load()} is called once before the benchmark loop begins; all I/O
 * must happen there. The returned {@code Iterable} may be lazy (streaming)
 * or eager (in-memory list).
 */
public interface ParameterSource extends AutoCloseable {

    /**
     * Loads and returns parameter rows. Maybe lazy (streaming) or eager.
     * Called once before the benchmark execution loop.
     */
    ParameterFeed load() throws Exception;

    default void close() throws Exception {
    }

    /**
     * Returns a parameters source that produces only empty parameters.
     */
    static ParameterSource empty() {
        return ParameterFeed::empty;
    }
}
