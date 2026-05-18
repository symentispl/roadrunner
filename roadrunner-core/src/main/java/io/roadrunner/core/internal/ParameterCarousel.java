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
import io.roadrunner.api.parameters.ParameterSource;
import io.roadrunner.api.parameters.SamplerParameters;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine-internal parameter dispatcher. Holds the preloaded parameter rows in a flat array and
 * cycles through them round-robin via a shared atomic counter — sampler threads grab the next row
 * as it rotates past. Calls to {@link #next()} are thread-safe, allocation-free, and lock-free
 * (not contention-free under high concurrency — see issue #138).
 * <p>
 * Not part of the public {@link ParameterFeed} SPI on purpose: implementors of
 * {@link ParameterSource} return a finite, single-threaded {@code ParameterFeed} from
 * {@link ParameterSource#load()}; this class is the engine's own data structure for re-publishing
 * those rows to many virtual threads.
 */
final class ParameterCarousel {

    private static final Logger LOG = LoggerFactory.getLogger(ParameterCarousel.class);

    /**
     * Drains the user-supplied {@link ParameterSource} once, copies its rows into a flat array,
     * and closes both the feed and the source. The resulting {@code ParameterCarousel} is the
     * only object the execution loop interacts with for the rest of the run.
     */
    static ParameterCarousel from(ParameterSource parameterSource) throws Exception {
        LOG.info("Pre-loading parameters from {} source", parameterSource);
        try (parameterSource;
                ParameterFeed feed = parameterSource.load()) {
            var rows = StreamSupport.stream(feed.spliterator(), false).toArray(SamplerParameters[]::new);
            return new ParameterCarousel(rows);
        }
    }

    private final SamplerParameters[] rows;
    private final AtomicLong counter = new AtomicLong(0);

    ParameterCarousel(SamplerParameters[] rows) {
        if (rows.length == 0) {
            throw new IllegalArgumentException("rows must be non-empty");
        }
        this.rows = rows;
    }

    /**
     * Returns the next parameter row in the round-robin cycle. Thread-safe.
     */
    SamplerParameters next() {
        long idx = counter.getAndIncrement();
        return rows[Math.floorMod(idx, rows.length)];
    }
}
