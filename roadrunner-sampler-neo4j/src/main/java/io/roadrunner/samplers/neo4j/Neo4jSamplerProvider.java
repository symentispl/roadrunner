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

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;
import java.util.Map;
import org.neo4j.driver.Driver;

public class Neo4jSamplerProvider implements SamplerProvider {

    private final Driver driver;
    private final String query;

    public Neo4jSamplerProvider(Driver driver, String query) {
        this.driver = driver;
        this.query = query;
    }

    @Override
    public Sampler newSampler() {
        return parameters -> {
            var startTime = System.nanoTime();
            try (var session = driver.session()) {
                // Neo4j's Session.run accepts Map<String, Object>; SamplerParameters.asMap returns
                // Map<String, ?>. Erasure makes the cast safe — Session.run is a read-only consumer
                // of the map. See #137 for typed CSV values (Integer/Long/etc. instead of String).
                @SuppressWarnings("unchecked")
                var params = (Map<String, Object>) parameters.asMap();
                var result = session.run(query, params);
                return SamplerResponse.response(startTime, System.nanoTime(), result.consume());
            } catch (Exception e) {
                return SamplerResponse.error(startTime, System.nanoTime(), e.getMessage());
            }
        };
    }

    @Override
    public void close() {
        driver.close();
    }
}
