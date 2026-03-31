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
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.StartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

@Testcontainers
public class Neo4jSamplerPluginIT {

    @Container
    private static final GenericContainer<?> neo4j = new GenericContainer<>("neo4j:community")
            .withExposedPorts(7687, 7474)
            .withEnv("NEO4J_dbms_security_auth__enabled", "false")
            .waitingFor(Wait.forListeningPort());

    @Test
    void invalidQuery() throws Exception {
        try (var plugin = new Neo4jSamplerPlugin()) {
            var options = plugin.options();
            options.uri = new URI("neo4j://%s:%d".formatted(neo4j.getHost(), neo4j.getMappedPort(7687)));
            options.username = "neo4j";
            options.password = "";
            try (var samplerProvider = options.samplerProvider()) {
                var sampler = samplerProvider.newSampler();
                var response = sampler.execute();
                assertThat(response).asInstanceOf(type(SamplerResponse.Error.class)).satisfies(e -> assertThat(e.timestamp()).isLessThanOrEqualTo(System.currentTimeMillis()));
            }
        }
    }

    @Test
    void validQuery() throws Exception {
        try (var plugin = new Neo4jSamplerPlugin()) {
            var options = plugin.options();
            options.uri = new URI("neo4j://%s:%d".formatted(neo4j.getHost(), neo4j.getMappedPort(7687)));
            options.username = "neo4j";
            options.password = "";
            options.query = "RETURN 1";
            try (var samplerProvider = options.samplerProvider()) {
                var sampler = samplerProvider.newSampler();
                var response = sampler.execute();
                assertThat(response).asInstanceOf(type(SamplerResponse.Response.class)).satisfies(r -> assertThat(r.timestamp()).isLessThanOrEqualTo(System.currentTimeMillis()));
            }
        }
    }
}
