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
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import picocli.CommandLine;

@CommandLine.Command(description = "Neo4j protocol provider")
public class Neo4jProtocolProvider implements ProtocolProvider {

    private Driver driver;

    @Override
    public String name() {
        return "neo4j";
    }

    @Override
    public Protocol newProtocol() {
        driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "password"));
        return new Protocol() {
            @Override
            public ProtocolResponse execute() {
                return driver.session().executeRead(tx -> tx.run("RETURN 1").single().get(0).asInt());
            }
        };
    }

    @Override
    public void close() {
        driver.close();
    }
}
