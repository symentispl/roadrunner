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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.metrics.MetricKey;
import io.roadrunner.api.metrics.MetricUnit;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.parameters.csv.CsvParameterSource;
import io.roadrunner.samplers.jdbc.JDBCSamplerOptions;
import io.roadrunner.samplers.jdbc.JDBCSamplerPlugin;
import io.roadrunner.samplers.jdbc.JDBCSamplerProvider;
import io.roadrunner.samplers.spi.SamplerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

class JDBCSamplerProviderIT {

    private static final String DRIVER_PATH = "target/jdbc-drivers/hsqldb.jar";

    @Test
    void successfulQuery() {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = defaultSamplerOptions(plugin, "jdbc:hsqldb:mem:testdb",
                    "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
            var ctx = SamplerContext.create(plugin, options);
            var rowCountKey = metricKey(ctx, "row_count");
            var acquireKey = metricKey(ctx, "jdbc.acquire_ns");
            var queryKey = metricKey(ctx, "jdbc.query_ns");
            assertThat(acquireKey.unit()).isEqualTo(MetricUnit.NANOSECONDS);
            assertThat(queryKey.unit()).isEqualTo(MetricUnit.NANOSECONDS);
            try (var sampler = ctx.newSampler()) {
                var response = sampler.execute(SamplerParameters.NONE, ctx.newResponseBuilder());
                assertThat(response)
                        .asInstanceOf(type(SamplerResponse.Response.class))
                        .satisfies(r -> {
                            assertThat(r.timestamp()).isGreaterThan(0);
                            assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                            assertThat(r.metricValueAt(rowCountKey)).isGreaterThanOrEqualTo(1.0);
                            var acquire = r.metricValueAt(acquireKey);
                            var query = r.metricValueAt(queryKey);
                            assertThat(acquire).isNotNegative();
                            assertThat(query).isNotNegative();
                            assertThat(acquire + query).isEqualTo((double) (r.stopTime() - r.timestamp()));
                        });
            }
        }
    }

    @Test
    void errorOnInvalidQuery() {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = defaultSamplerOptions(plugin, "jdbc:hsqldb:mem:testdb2",
                    "SELECT * FROM NONEXISTENT_TABLE");
            var ctx = SamplerContext.create(plugin, options);
            try (var sampler = ctx.newSampler()) {
                var response = sampler.execute(SamplerParameters.NONE, ctx.newResponseBuilder());
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
        var provider = new JDBCSamplerProvider(new ExceptionThrowingDataSource(), "query(\"SELECT 1\")");
        var ctx = SamplerContext.of(provider);
        try (var sampler = ctx.newSampler()) {
            var response = sampler.execute(SamplerParameters.NONE, ctx.newResponseBuilder());
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
    void poolSaturationVisibleInPerSampleTimingsAndCounters() throws Exception {
        int callCount = 50;
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = defaultSamplerOptions(plugin, "jdbc:hsqldb:mem:saturation",
                    "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
            options.poolSize = 1;
            // Use of() to retain a typed provider reference for stats inspection
            var provider = plugin.newSamplerProvider(options);
            var ctx = SamplerContext.of(provider);
            var acquireKey = metricKey(ctx, "jdbc.acquire_ns");
            var queryKey = metricKey(ctx, "jdbc.query_ns");
            var responses = new ArrayList<SamplerResponse<?>>();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                 var sampler = ctx.newSampler()) {
                var started = new CountDownLatch(callCount);
                var futures = new ArrayList<Future<SamplerResponse<?>>>();
                try (var heldConnection = provider.getConnection()) {
                    for (int i = 0; i < callCount; i++) {
                        futures.add(executor.submit(() -> {
                            started.countDown();
                            return sampler.execute(SamplerParameters.NONE, ctx.newResponseBuilder());
                        }));
                    }
                    assertThat(started.await(30, TimeUnit.SECONDS))
                            .as("all sample tasks should start within 30 seconds")
                            .isTrue();
                    assertThatThrownBy(() -> futures.getFirst().get(100, TimeUnit.MILLISECONDS))
                            .as("a sample must wait while the only connection is held")
                            .isInstanceOf(TimeoutException.class);
                }
                for (var future : futures) {
                    responses.add(future.get(30, TimeUnit.SECONDS));
                }
            }

            assertThat(responses).hasSize(callCount).allSatisfy(response -> {
                assertThat(response).isInstanceOf(SamplerResponse.Response.class);
                var acquire = response.metricValueAt(acquireKey);
                var query = response.metricValueAt(queryKey);
                assertThat(acquire).isNotNegative();
                assertThat(query).isNotNegative();
                assertThat(acquire + query).isEqualTo((double) (response.stopTime() - response.timestamp()));
            });
            assertThat(responses).anySatisfy(response ->
                    assertThat(response.metricValueAt(acquireKey)).isGreaterThan(response.metricValueAt(queryKey)));

            long samples = provider.sampleCount();
            long acquire = provider.totalAcquireNanos();
            long query = provider.totalQueryNanos();
            double ratio = (double) acquire / (double) (acquire + query);

            assertThat(samples).isEqualTo(callCount);
            assertThat(responses.stream().mapToDouble(r -> r.metricValueAt(acquireKey)).sum())
                    .isEqualTo((double) acquire);
            assertThat(responses.stream().mapToDouble(r -> r.metricValueAt(queryKey)).sum())
                    .isEqualTo((double) query);
            assertThat(ratio)
                    .as("acquire ratio should exceed 20%% under saturation, was %.3f", ratio)
                    .isGreaterThan(0.20);
        }
    }

    @Test
    void fillInParameters() throws Exception {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = defaultSamplerOptions(plugin, "jdbc:hsqldb:mem:parameters",
                    "INSERT INTO parameters (v_int,v_text) VALUES (?,?)");
            // Use of() to retain a typed provider reference for getConnection()
            var provider = plugin.newSamplerProvider(options);
            var ctx = SamplerContext.of(provider);
            try (Connection connection = provider.getConnection()) {
                connection.createStatement().execute("CREATE TABLE parameters (v_int int,v_text varchar(255))");
            }
            LinkedHashMap<String, Object> queryParameters = new LinkedHashMap<>();
            queryParameters.put("v_int", 1);
            queryParameters.put("v_text", "test");
            try (var sampler = ctx.newSampler()) {
                var response = sampler.execute(SamplerParameters.of(queryParameters), ctx.newResponseBuilder());
                assertThat(response).asInstanceOf(type(SamplerResponse.Response.class)).satisfies(r ->
                        assertThat(r.timestamp()).isLessThanOrEqualTo(System.nanoTime()));
            }
        }
    }

    // Issue #137 acceptance criterion: a CSV "id:int" column must drive a WHERE id = ? query
    // against a real INT column, proving the value is bound as an INTEGER rather than a VARCHAR
    // the driver has to coerce.
    @Test
    void typedCsvColumnBindsToIntColumnWithoutDriverCoercion(@TempDir Path tempDir) throws Exception {
        try (var plugin = new JDBCSamplerPlugin()) {
            var options = defaultSamplerOptions(
                    plugin, "jdbc:hsqldb:mem:typedcsv", "SELECT 1 FROM orders WHERE id = ?");
            var provider = plugin.newSamplerProvider(options);
            var ctx = SamplerContext.of(provider);
            try (Connection connection = provider.getConnection()) {
                connection.createStatement().execute("CREATE TABLE orders (id INT, name VARCHAR(255))");
                connection.createStatement().execute("INSERT INTO orders VALUES (42, 'widget')");
            }

            var csvFile = tempDir.resolve("orders.csv");
            Files.writeString(csvFile, "id:int\n42\n");
            SamplerParameters parameters;
            try (var feed = new CsvParameterSource(csvFile, ',').load()) {
                parameters = feed.iterator().next();
            }
            assertThat(parameters.valueOf("id")).isInstanceOf(Integer.class);

            var rowCountKey = ctx.metricRegistry().registeredKeys().stream()
                    .filter(k -> "row_count".equals(k.name()))
                    .findFirst()
                    .orElseThrow();
            try (var sampler = ctx.newSampler()) {
                var response = sampler.execute(parameters, ctx.newResponseBuilder());
                assertThat(response)
                        .asInstanceOf(type(SamplerResponse.Response.class))
                        .satisfies(r -> assertThat(r.metricValueAt(rowCountKey)).isEqualTo(1.0));
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

    private static MetricKey metricKey(SamplerContext context, String name) {
        return context.metricRegistry().registeredKeys().stream()
                .filter(key -> name.equals(key.name()))
                .findFirst()
                .orElseThrow();
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
