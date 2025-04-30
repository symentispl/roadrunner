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
package io.roadrunner.protocols.neo4j;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class Neo4jProtocolIT {

    @Container
    private final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:5.15.0"));

    @Test
    void executeQueryAndReturnResults() {
        // given
        try (var provider = new Neo4JProtocolPlugin()) {

            // Set container URI, username and password from testcontainer
            CommandLine.populateCommand(provider,
                    "RETURN 1",
                    "--uri=" + neo4jContainer.getBoltUrl(),
                    "--username=" + "neo4j",
                    "--password=" + neo4jContainer.getAdminPassword());

            try (var protocolSupplier = provider.newProtocolSupplier();
                 var protocol = protocolSupplier.get()) {
                // when
                var result = protocol.execute();
                // then - just verify no exceptions and result is available
                assertThat(result).isNotNull();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
