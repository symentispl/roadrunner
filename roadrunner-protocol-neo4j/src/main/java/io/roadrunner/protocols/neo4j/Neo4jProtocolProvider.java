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
import io.roadrunner.protocols.spi.ProtocolProvider;
import java.net.URI;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(description = "Neo4j protocol")
public class Neo4jProtocolProvider implements ProtocolProvider {

    @Parameters(paramLabel = "query", description = "Neo4j query")
    private String query;

    @Option(names = "--uri", description = "Neo4j database uri")
    private URI uri;

    @Option(names = "--username", description = "Neo4j database username")
    private String username;

    @Option(names = "--password", description = "Neo4j database password")
    private String password;

    private Driver driver;

    @Override
    public String name() {
        return "neo4j";
    }

    @Override
    public Protocol newProtocol() {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
        return () -> {
            var startTime = System.nanoTime();
            try (var session = driver.session()) {
                session.run(query);
                return ProtocolResponse.response(startTime, System.nanoTime(), "OK");
            } catch (Exception e) {
                return ProtocolResponse.error(startTime, System.nanoTime(), e.getMessage());
            }
        };
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }
}
