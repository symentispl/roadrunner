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
 * Finite, ordered sequence of {@link SamplerParameters} rows produced by a {@link ParameterSource}.
 * <p>
 * An implementation may stream lazily, allocate per row, and assume single-threaded iteration.
 * The engine drains the feed exactly once at startup, copies the rows into an internal store, and
 * closes the feed. From that point on, sampler threads see the engine's own thread-safe dispatcher;
 * the feed returned here never reaches the hot path. Concurrency, cycling, and hot-path
 * performance are entirely the engine's concerns.
 */
public interface ParameterFeed extends AutoCloseable, Iterable<SamplerParameters> {

    @Override
    default void close() throws Exception {}
}
