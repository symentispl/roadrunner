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

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.samplers.jdbc.JDBCSamplerOptions;
import io.roadrunner.samplers.jdbc.JDBCSamplerPlugin;
import io.roadrunner.samplers.jdbc.JDBCSamplerProvider;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

class JDBCSamplerProviderIT {

    private static final String DRIVER_PATH = "target/jdbc-drivers/hsqldb.jar";

    @Test
    void successfulQuery() {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = defaultSamplerOptions(plugin, "jdbc:hsqldb:mem:testdb", "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
            try (var provider = plugin.newSamplerProvider(options);
                 var sampler = provider.newSampler()) {
                var response = sampler.execute(SamplerParameters.NONE);
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
    void errorOnInvalidQuery() {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = defaultSamplerOptions(plugin, "jdbc:hsqldb:mem:testdb2", "SELECT * FROM NONEXISTENT_TABLE");
            try (var provider = plugin.newSamplerProvider(options);
                 var newSampler = provider.newSampler()) {
                var response = newSampler.execute(SamplerParameters.NONE);
                assertThat(response)
                        .asInstanceOf(type(SamplerResponse.Error.class))
                        .satisfies(r -> {
                            assertThat(r.timestamp()).isGreaterThan(0);
                            assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                            assertThat(r.message()).isNotBlank().contains("NONEXISTENT_TABLE");
                        });
            }
        }
    }

    @Test
    void errorOnConnectionFailure() {
        var failingDataSource = new ExceptionThrowingDataSource();
        try (var provider = new JDBCSamplerProvider(failingDataSource, "query(\"SELECT 1\")");
             var sampler = provider.newSampler()) {
            var response = sampler.execute(SamplerParameters.NONE);
            assertThat(response)
                    .asInstanceOf(type(SamplerResponse.Error.class))
                    .satisfies(r -> {
                        assertThat(r.timestamp()).isGreaterThan(0);
                        assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                        assertThat(r.message()).isEqualTo("simulated connection failure");
                    });
            assertThat(provider.sampleCount()).isEqualTo(1);
            assertThat(provider.totalAcquireNanos()).isPositive();
        }
    }

    @Test
    void poolSaturationVisibleInCounters() throws Exception {
        int callCount = 50;
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = defaultSamplerOptions(plugin, "jdbc:hsqldb:mem:saturation", "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
            options.poolSize = 1;
            try (var provider = plugin.newSamplerProvider(options);
                 var executor = Executors.newVirtualThreadPerTaskExecutor();
                 var sampler = provider.newSampler()) {
                var done = new CountDownLatch(callCount);
                for (int i = 0; i < callCount; i++) {
                    executor.submit(() -> {
                        try {
                            sampler.execute(SamplerParameters.NONE);
                        } finally {
                            done.countDown();
                        }
                    });
                }
                assertThat(done.await(30, TimeUnit.SECONDS))
                        .as("all samples should complete within 30 seconds")
                        .isTrue();

                long samples = provider.sampleCount();
                long acquire = provider.totalAcquireNanos();
                long query = provider.totalQueryNanos();
                double ratio = (double) acquire / (double) (acquire + query);

                assertThat(samples).isEqualTo(callCount);
                assertThat(ratio)
                        .as("acquire ratio should exceed 20%% under saturation, was %.3f", ratio)
                        .isGreaterThan(0.20);
            }
        }
    }

    @Test
    void fillInParameters() throws Exception {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = defaultSamplerOptions(plugin,
                    "jdbc:hsqldb:mem:parameters",
                    "INSERT INTO parameters (v_int,v_text) VALUES (?,?)");
            try (var provider = plugin.newSamplerProvider(options)) {
                try (Connection connection = provider.getConnection()) {
                    connection.createStatement().execute("CREATE TABLE parameters (v_int int,v_text varchar(255))");
                }
                var sampler = provider.newSampler();
                LinkedHashMap<String, Object> queryParameters = new LinkedHashMap<>();
                queryParameters.put("v_int", 1);
                queryParameters.put("v_text", "test");
                var response = sampler.execute(SamplerParameters.of(queryParameters));
                assertThat(response).asInstanceOf(type(SamplerResponse.Response.class)).satisfies(r -> {
                    assertThat(r.timestamp()).isLessThanOrEqualTo(System.nanoTime());
                });
            }
        }
    }

    private static class ExceptionThrowingDataSource implements DataSource {
        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("simulated connection failure");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("simulated connection failure");
        }

        @Override
        public PrintWriter getLogWriter() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLoginTimeout(int seconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLoginTimeout() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Logger getParentLogger() {
            throw new UnsupportedOperationException();
        }
    }

    private static JDBCSamplerOptions defaultSamplerOptions(JDBCSamplerPlugin plugin, String url, String query) {
        var options = plugin.options();
        options.url = url;
        options.username = "SA";
        options.password = "";
        options.expression = "query(\"%s\")".formatted(query);
        options.driverPath = Paths.get(DRIVER_PATH);
        return options;
    }
}
