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
 * Thread-safe, non-blocking dispatcher of {@link SamplerParameters} to sampler threads.
 * <p>
 * The data must be fully loaded before the benchmark loop begins (via
 * {@link io.roadrunner.api.parameters.ParameterSource#load()}). {@link java.util.Iterator#next()} is
 * called on the hot path and must never block.
 */
public interface ParameterFeed extends AutoCloseable, Iterable<SamplerParameters> {

    default void close() throws Exception {}

    static ParameterFeed cyclicOfEmptyParameters() {
        return () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public SamplerParameters next() {
                return SamplerParameters.EMPTY;
            }
        };
    }
}
