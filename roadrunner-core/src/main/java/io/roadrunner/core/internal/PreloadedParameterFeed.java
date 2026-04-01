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
package io.roadrunner.core.internal;

import io.roadrunner.api.parameters.ParameterFeed;
import io.roadrunner.api.parameters.SamplerParameters;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link ParameterFeed} backed by a pre-loaded array of {@link SamplerParameters}.
 * <p>
 * Rows are delivered round-robin using an {@link AtomicLong} counter.
 * Hot-path cost: one {@code getAndIncrement()} CAS + two array dereferences.
 * No object allocation per {@link Iterator#next()} call.
 */
final class PreloadedParameterFeed implements ParameterFeed {

    private final SamplerParameters[] rows;

    PreloadedParameterFeed(SamplerParameters[] rows) {
        if (rows.length == 0) {
            throw new IllegalArgumentException("rows must be non-empty");
        }
        this.rows = rows;
    }

    @Override
    public Iterator<SamplerParameters> iterator() {
        return new Iterator<>() {
            private final AtomicLong counter = new AtomicLong(0);

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public SamplerParameters next() {
                long idx = counter.getAndIncrement();
                return rows[(int) (idx % rows.length)];
            }
        };
    }
}
