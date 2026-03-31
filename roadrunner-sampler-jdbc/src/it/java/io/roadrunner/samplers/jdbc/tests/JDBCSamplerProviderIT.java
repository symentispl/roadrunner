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
import io.roadrunner.samplers.jdbc.JDBCSamplerPlugin;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class JDBCSamplerProviderIT {

    @Test
    void successfulQuery() throws Exception {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = plugin.options();
            options.url = "jdbc:hsqldb:mem:testdb";
            options.username = "SA";
            options.password = "";
            options.query = "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS";
            options.driverPath = Paths.get("target/jdbc-drivers/hsqldb.jar");
            try (var provider = plugin.newSamplerProvider(options);
                 var sampler = provider.newSampler()) {
                var response = sampler.execute();
                assertThat(response)
                        .asInstanceOf(type(SamplerResponse.Response.class))
                        .satisfies(r -> {
                            assertThat(r.timestamp()).isGreaterThan(0);
                            assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                        });
            }

        }
    }

    @Test
    void errorOnInvalidQuery() throws Exception {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = plugin.options();
            options.url = "jdbc:hsqldb:mem:testdb2";
            options.username = "SA";
            options.password = "";
            options.query = "SELECT * FROM NONEXISTENT_TABLE";
            options.driverPath = Paths.get("target/jdbc-drivers/hsqldb.jar");
            try (var provider = plugin.newSamplerProvider(options);
                 var newSampler = provider.newSampler()) {
                var response = newSampler.execute();
                assertThat(response)
                        .asInstanceOf(type(SamplerResponse.Error.class))
                        .satisfies(r -> {
                            assertThat(r.timestamp()).isGreaterThan(0);
                            assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                            assertThat(r.message()).isEqualTo("user lacks privilege or object not found: NONEXISTENT_TABLE");
                        });
            }
        }
    }
}
