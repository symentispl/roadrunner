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
package io.roadrunner.samplers.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.pool.HikariPool;
import io.roadrunner.samplers.spi.SamplerPlugin;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.util.ServiceLoader;

public class JDBCSamplerPlugin implements SamplerPlugin<JDBCSamplerProvider, JDBCSamplerOptions> {
    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public JDBCSamplerProvider newSamplerProvider(JDBCSamplerOptions options) {
        ensureDriverLoaded(options);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(options.url);
        config.setUsername(options.username);
        config.setPassword(options.password);
        config.setPoolName("roadrunner-sampler");
        var pool = new HikariPool(config);

        return new JDBCSamplerProvider(pool, options.query);
    }

    @Override
    public JDBCSamplerOptions options() {
        return new JDBCSamplerOptions(this);
    }

    private Driver ensureDriverLoaded(JDBCSamplerOptions options) {
        try {
            var jarUrl = options.driverPath.toUri().toURL();
            var driverClassLoader = new URLClassLoader(new URL[] {jarUrl}, ClassLoader.getSystemClassLoader());
            if (options.driverClass != null) {
                var clazz = driverClassLoader.loadClass(options.driverClass);
                return (Driver) clazz.getDeclaredConstructor().newInstance();
            } else {
                return ServiceLoader.load(Driver.class, driverClassLoader)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No JDBC driver found in: " + options.driverPath));
            }
        } catch (Exception e) {
            throw new ProtocolInitializationException("Failed to initialize JDBC driver", e);
        }
    }
}
