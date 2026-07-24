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

import io.roadrunner.samplers.spi.SamplerExtension;
import org.neo4j.driver.Driver;

public class Neo4jSamplerProvider extends SamplerExtension {

    private final Neo4jSampler neo4jSampler;

    public Neo4jSamplerProvider(Driver driver, String expressionText) {
        this(new Neo4jSampler(driver), expressionText);
    }

    private Neo4jSamplerProvider(Neo4jSampler neo4jSampler, String expressionText) {
        super(neo4jSampler, expressionText);
        this.neo4jSampler = neo4jSampler;
    }

    @Override
    public void close() {
        neo4jSampler.close();
    }
}
