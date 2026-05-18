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

import java.util.Iterator;

/**
 * Ordered sequence of {@link SamplerParameters} rows produced by a {@link ParameterSource}.
 *
 * <h2>For {@code ParameterSource} implementors</h2>
 *
 * Return a finite feed from {@link ParameterSource#load()}. You may stream lazily, allocate
 * per row, and assume single-threaded iteration. The engine drains your feed once at startup,
 * copies the rows into an internal array, and re-publishes them through its own thread-safe,
 * cycling, non-allocating feed. Concurrency, cycling (when the run requests more samples than
 * your feed produced), and hot-path performance are the engine's responsibility, not yours.
 *
 * <h2>For the execution engine</h2>
 *
 * The feed the {@code ExecutionStrategy} actually sees is the engine's preloaded wrapper, not
 * the one returned by a user-supplied source. An {@code ExecutionStrategy} obtains the iterator
 * <strong>once</strong>, before forking sampler users, and shares that single iterator across
 * all of them. Calls to {@link java.util.Iterator#next()} on the shared iterator are:
 * <ul>
 *   <li><b>thread-safe</b> — concurrent calls produce well-defined results;</li>
 *   <li><b>allocation-free</b> on the hot path — no boxing, no new objects per call;</li>
 *   <li><b>lock-free and non-blocking</b> — implemented with an atomic counter, not a mutex.
 *       Note this is <em>not</em> contention-free: under high concurrency the counter is a
 *       cache-line hot spot. See issue #138 for measurement and mitigation work.</li>
 * </ul>
 */
public interface ParameterFeed extends AutoCloseable, Iterable<SamplerParameters> {

    default void close() throws Exception {}

    /**
     * Test-only helper: an infinite feed that always yields {@link SamplerParameters#NONE}.
     * Useful for unit tests that exercise an {@code ExecutionStrategy} directly, bypassing the
     * engine's preloading wrapper. Production code always goes through the preloaded feed and
     * does not need this.
     */
    static ParameterFeed cyclicOfEmptyParameters() {
        return () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public SamplerParameters next() {
                return SamplerParameters.NONE;
            }
        };
    }
}
