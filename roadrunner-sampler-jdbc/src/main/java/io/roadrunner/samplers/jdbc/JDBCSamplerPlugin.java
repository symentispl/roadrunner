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
import com.zaxxer.hikari.HikariDataSource;
import io.roadrunner.samplers.spi.PluginInitializationException;
import io.roadrunner.samplers.spi.SamplerPlugin;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDBCSamplerPlugin implements SamplerPlugin<JDBCSamplerProvider, JDBCSamplerOptions> {

    private static final Logger LOG = LoggerFactory.getLogger(JDBCSamplerPlugin.class);
    private static final double ACQUIRE_RATIO_WARN_THRESHOLD = 0.20;

    private URLClassLoader driverClassLoader;
    private DriverWrapper registeredDriver;
    private HikariDataSource dataSource;
    private JDBCSamplerProvider provider;

    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public JDBCSamplerProvider newSamplerProvider(JDBCSamplerOptions options) {
        registeredDriver = registerDriver(options);

        var config = new HikariConfig();
        config.setJdbcUrl(options.url);
        config.setUsername(options.username);
        config.setPassword(options.password);
        config.setMaximumPoolSize(options.poolSize);
        config.setPoolName("roadrunner-sampler");
        dataSource = new HikariDataSource(config);

        provider = new JDBCSamplerProvider(dataSource, options.expression);
        return provider;
    }

    @Override
    public JDBCSamplerOptions options() {
        return new JDBCSamplerOptions(this);
    }

    private DriverWrapper registerDriver(JDBCSamplerOptions options) {
        try {
            var jarUrl = options.driverPath.toUri().toURL();
            driverClassLoader = new URLClassLoader(new URL[] {jarUrl}, ClassLoader.getSystemClassLoader());
            Driver delegate;
            if (options.driverClass != null) {
                var clazz = driverClassLoader.loadClass(options.driverClass);
                delegate = (Driver) clazz.getDeclaredConstructor().newInstance();
            } else {
                delegate = ServiceLoader.load(Driver.class, driverClassLoader)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No JDBC driver found in: " + options.driverPath));
            }
            var shim = new DriverWrapper(delegate);
            DriverManager.registerDriver(shim);
            return shim;
        } catch (Exception e) {
            throw new PluginInitializationException("Failed to initialize JDBC driver", e);
        }
    }

    @Override
    public void close() {
        try {
            if (provider != null) {
                logSummary(provider);
            }
        } finally {
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
            if (registeredDriver != null) {
                try {
                    DriverManager.deregisterDriver(registeredDriver);
                } catch (Exception e) {
                    LOG.warn("Failed to deregister JDBC driver", e);
                }
                registeredDriver = null;
            }
            if (driverClassLoader != null) {
                try {
                    driverClassLoader.close();
                } catch (Exception e) {
                    LOG.warn("Failed to close driver class loader", e);
                }
                driverClassLoader = null;
            }
        }
    }

    private static void logSummary(JDBCSamplerProvider provider) {
        long samples = provider.sampleCount();
        if (samples == 0) {
            return;
        }
        long acquire = provider.totalAcquireNanos();
        long query = provider.totalQueryNanos();
        long total = acquire + query;
        double ratio = total == 0 ? 0.0 : (double) acquire / (double) total;
        long avgAcquire = acquire / samples;
        long avgQuery = query / samples;
        String summary = String.format(
                Locale.ROOT,
                "JDBC sampler summary: samples=%d, avg-acquire-ns=%d, avg-query-ns=%d, acquire-ratio=%.1f%%",
                samples,
                avgAcquire,
                avgQuery,
                ratio * 100.0);
        if (ratio > ACQUIRE_RATIO_WARN_THRESHOLD) {
            LOG.warn("{} - pool likely undersized; increase --pool-size or reduce --users", summary);
        } else {
            LOG.info(summary);
        }
    }
}
