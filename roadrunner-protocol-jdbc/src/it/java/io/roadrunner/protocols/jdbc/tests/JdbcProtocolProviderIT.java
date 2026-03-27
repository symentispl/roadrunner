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
package io.roadrunner.protocols.jdbc.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.protocols.jdbc.JdbcProtocolProvider;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class JdbcProtocolProviderIT {

    @Test
    void successfulQuery() throws Exception{
        try (var provider = new JdbcProtocolProvider()) {
            provider.url = "jdbc:hsqldb:mem:testdb";
            provider.username = "SA";
            provider.password = "";
            provider.query = "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS";
            provider.driverPath = Paths.get("target/jdbc-drivers/hsqldb.jar");

            var protocol = provider.newProtocol();
            var response = protocol.execute();

            assertThat(response)
                    .asInstanceOf(type(ProtocolResponse.Response.class))
                    .satisfies(r -> {
                        assertThat(r.timestamp()).isGreaterThan(0);
                        assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                    });
        }
    }

    @Test
    void errorOnInvalidQuery() throws Exception{
        try (var provider = new JdbcProtocolProvider()) {
            provider.url = "jdbc:hsqldb:mem:testdb2";
            provider.username = "SA";
            provider.password = "";
            provider.query = "SELECT * FROM NONEXISTENT_TABLE";
            provider.driverPath = Paths.get("target/jdbc-drivers/hsqldb.jar");

            var protocol = provider.newProtocol();
            var response = protocol.execute();

            assertThat(response)
                    .asInstanceOf(type(ProtocolResponse.Error.class))
                    .satisfies(r -> {
                        assertThat(r.timestamp()).isGreaterThan(0);
                        assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                        assertThat(r.message()).isEqualTo("user lacks privilege or object not found: NONEXISTENT_TABLE");
                    });
        }
    }
}
