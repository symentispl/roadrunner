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

import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.protocol.Protocol;
import io.roadrunner.api.protocol.ProtocolSupplier;
import io.roadrunner.protocols.spi.ProtocolPlugin;

import java.net.URI;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import picocli.CommandLine;

@CommandLine.Command(description = "Neo4j protocol provider")
public class Neo4JProtocolPlugin implements ProtocolPlugin {

    @CommandLine.Option(names = "--uri", description = "Neo4j URI")
    private URI uri;

    @CommandLine.Option(names = "--username", description = "Neo4j username")
    private String username = "neo4j";

    @CommandLine.Option(names = "--password", description = "Neo4j password")
    private String password = "neo4j";

    @CommandLine.Parameters(paramLabel = "query", description = "Cypher query to execute")
    private String query;

    @Override
    public String name() {
        return "neo4j";
    }

    @Override
    public ProtocolSupplier newProtocolSupplier() {
        return new Neo4jProtocolSupplier();
    }

    @Override
    public void close() {}

    private class Neo4jProtocolSupplier implements ProtocolSupplier {
        private final Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));

        @Override
        public Protocol get() {
            return () -> {
                var startTime = System.nanoTime();
                var session = driver.session();
                var response = session.executeRead(tx -> tx.run(query).consume());
                var stopTime = System.nanoTime();
                return ProtocolResponse.response(startTime, stopTime, response);
            };
        }

        @Override
        public void close() throws Exception {
            driver.close();
        }
    }
}
