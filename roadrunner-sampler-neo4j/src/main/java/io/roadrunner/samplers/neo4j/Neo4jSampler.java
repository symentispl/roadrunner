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
package io.roadrunner.samplers.neo4j;

import io.roadrunner.api.attachments.AttachmentKey;
import io.roadrunner.api.attachments.AttachmentRegistry;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerSinkRegistrar;
import java.util.Map;
import org.neo4j.driver.Driver;

/**
 * Extension-point methods class for the Neo4j sampler: {@link #query(String)} is bound from a
 * CLI {@code query("RETURN 1")} expression via {@link io.roadrunner.samplers.spi.SamplerExtensionPoint}.
 */
public class Neo4jSampler implements SamplerSinkRegistrar {

    private final Driver driver;
    private AttachmentKey resultsKey;

    public Neo4jSampler(Driver driver) {
        this.driver = driver;
    }

    @Override
    public void registerAttachments(AttachmentRegistry registry) {
        this.resultsKey = registry.register("results");
    }

    public Sampler query(String cypher) {
        return (parameters, builder) -> {
            var startTime = System.nanoTime();
            try (var session = driver.session()) {
                // Neo4j's Session.run accepts Map<String, Object>; SamplerParameters.asMap returns
                // Map<String, ?>. Erasure makes the cast safe — Session.run is a read-only consumer
                // of the map. See #137 for typed CSV values (Integer/Long/etc. instead of String).
                @SuppressWarnings("unchecked")
                var params = (Map<String, Object>) parameters.asMap();
                var result = session.run(cypher, params);
                var summary = result.consume();
                return builder.response(
                        startTime,
                        System.nanoTime(),
                        sink -> sink.attach(resultsKey, summary.counters().toString()));
            } catch (Exception e) {
                return builder.error(startTime, System.nanoTime(), e.getMessage());
            }
        };
    }

    public void close() {
        driver.close();
    }
}
