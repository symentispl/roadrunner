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
package io.roadrunner.protocols.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.pool.HikariPool;
import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.protocol.Protocol;
import io.roadrunner.protocols.spi.ProtocolInitializationException;
import io.roadrunner.protocols.spi.ProtocolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ServiceLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(description = "JDBC protocol, executes a SQL query against a database", mixinStandardHelpOptions = true)
public class JdbcProtocolProvider implements ProtocolProvider {

    @Option(names = "--url", description = "JDBC URL (e.g. jdbc:postgresql://localhost/mydb)", required = true)
    public String url;

    @Option(names = "--username", description = "Database username", required = true)
    public String username;

    @Option(names = "--password", description = "Database password", required = true)
    public String password;

    @Option(names = "--query", description = "SQL query to execute per request", required = true)
    public String query;

    @Option(names = "--driver", description = "Path to JDBC driver JAR file", required = true)
    public Path driverPath;

    @Option(names = "--driver-class", description = "Fully-qualified JDBC driver class name (auto-detected if omitted)")
    public String driverClass;

    private Driver driver;
    private HikariPool pool;

    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public Protocol newProtocol() {
        ensureDriverLoaded();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("roadrunner-protocol");
        pool = new HikariPool(config);
        return () -> {
            try (var cnn = pool.getConnection();
                    var stmt = cnn.createStatement()) {
                var startTime = System.nanoTime();
                try {
                    boolean execute = stmt.execute(query);
                    var stopTime = System.nanoTime();
                    return ProtocolResponse.response(startTime, stopTime, execute);
                } catch (SQLException e) {
                    var stopTime = System.nanoTime();
                    return ProtocolResponse.error(startTime, stopTime, e.getMessage());
                }

            } catch (SQLException e) {
                return ProtocolResponse.error(System.nanoTime(), System.nanoTime(), e.getMessage());
            }
        };
    }

    private void ensureDriverLoaded() {
        if (driver != null) {
            return;
        }
        try {
            var jarUrl = driverPath.toUri().toURL();
            var driverClassLoader = new URLClassLoader(new URL[] {jarUrl}, ClassLoader.getSystemClassLoader());
            if (driverClass != null) {
                var clazz = driverClassLoader.loadClass(driverClass);
                driver = (Driver) clazz.getDeclaredConstructor().newInstance();
            } else {
                driver = ServiceLoader.load(Driver.class, driverClassLoader)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No JDBC driver found in: " + driverPath));
            }
        } catch (Exception e) {
            throw new ProtocolInitializationException("Failed to initialize JDBC driver", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (pool != null) {
            pool.shutdown();
        }
    }
}
