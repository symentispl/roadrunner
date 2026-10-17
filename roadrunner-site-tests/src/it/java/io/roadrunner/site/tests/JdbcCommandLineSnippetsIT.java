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
package io.roadrunner.site.tests;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.util.Map;

@Testcontainers
@Snippets("jdbc")
public class JdbcCommandLineSnippetsIT extends AbstractCommandLineSnippets {

    private static final String DB_NAME = "testdb";
    private static final String USERNAME = "app";
    private static final String PASSWORD = "secret";

    @Container
    private static final GenericContainer<?> postgres = new GenericContainer<>("postgres:16")
            .withExposedPorts(5432)
            .withEnv("POSTGRES_USER", USERNAME)
            .withEnv("POSTGRES_PASSWORD", PASSWORD)
            .withEnv("POSTGRES_DB", DB_NAME)
            .withCopyToContainer(Transferable.of("""
                    CREATE TABLE users (id int PRIMARY KEY, name text);
                    INSERT INTO users VALUES (1, 'alice'), (2, 'bob'), (3, 'carol');
                    """), "/docker-entrypoint-initdb.d/init.sql")
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @Override
    protected Map<String, String> placeholders() {
        try {
            var driver = Path.of(org.postgresql.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Map.of(
                    "jdbc_url", "jdbc:postgresql://%s:%d/%s".formatted(postgres.getHost(), postgres.getMappedPort(5432), DB_NAME),
                    "username", USERNAME,
                    "password", PASSWORD,
                    "driver", driver.toString());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
