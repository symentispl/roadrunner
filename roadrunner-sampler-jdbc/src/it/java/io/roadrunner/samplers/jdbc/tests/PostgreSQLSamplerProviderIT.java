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
package io.roadrunner.samplers.jdbc.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.samplers.jdbc.JDBCSamplerPlugin;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class PostgreSQLSamplerProviderIT {

    private static final String DRIVER_PATH = "target/jdbc-drivers/postgresql.jar";
    private static final String DB_NAME = "testdb";
    private static final String USERNAME = "postgres";
    private static final String PASSWORD = "pgpass";

    @Container
    private static final GenericContainer<?> postgres = new GenericContainer<>("postgres:16")
            .withExposedPorts(5432)
            .withEnv("POSTGRES_PASSWORD", PASSWORD)
            .withEnv("POSTGRES_DB", DB_NAME)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @Test
    void validQuery() {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = plugin.options();
            options.url = "jdbc:postgresql://%s:%d/%s".formatted(postgres.getHost(), postgres.getMappedPort(5432), DB_NAME);
            options.username = USERNAME;
            options.password = PASSWORD;
            options.query = "query(\"SELECT 1\")";
            options.driverPath = Paths.get(DRIVER_PATH);
            try (var provider = plugin.newSamplerProvider(options);
                    var sampler = provider.newSampler()) {
                var response = sampler.execute(SamplerParameters.NONE);
                assertThat(response)
                        .asInstanceOf(type(SamplerResponse.Response.class))
                        .satisfies(r -> {
                            assertThat(r.timestamp()).isGreaterThan(0);
                            assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                        });
            }
        }
    }
}
