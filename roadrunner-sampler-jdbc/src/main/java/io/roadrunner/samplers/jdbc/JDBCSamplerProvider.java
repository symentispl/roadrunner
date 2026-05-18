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

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.SQLType;
import java.util.concurrent.atomic.LongAdder;
import javax.sql.DataSource;

public class JDBCSamplerProvider implements SamplerProvider {

    private final DataSource dataSource;
    private final String query;
    private final LongAdder sampleCount = new LongAdder();
    private final LongAdder acquireNanos = new LongAdder();
    private final LongAdder queryNanos = new LongAdder();

    public JDBCSamplerProvider(DataSource dataSource, String query) {
        this.dataSource = dataSource;
        this.query = query;
    }

    @Override
    public Sampler newSampler() {
        return (SamplerParameters parameters) -> {
            var tStarted = System.nanoTime();
            try (var cnn = dataSource.getConnection();
                 var stmt = cnn.prepareStatement(query)) {
                var tAcquired = System.nanoTime();
                try {
                    long rowCount;
                    parameters.forEach((index, type, value) -> stmt.setObject(index + 1, value, sqlTypeOf(type)));
                    boolean hasResultSet = stmt.execute();
                    if (hasResultSet) {
                        try (var rs = stmt.getResultSet()) {
                            rowCount = 0;
                            while (rs.next()) {
                                rowCount++;
                            }
                        }
                    } else {
                        rowCount = stmt.getUpdateCount();
                    }
                    var tDone = System.nanoTime();
                    recordTimestamps(tStarted, tAcquired, tDone);
                    return SamplerResponse.response(tStarted, tDone, rowCount);
                } catch (Exception e) {
                    var tDone = System.nanoTime();
                    recordTimestamps(tStarted, tAcquired, tDone);
                    return SamplerResponse.error(tStarted, tDone, e.getMessage());
                }
            } catch (SQLException e) {
                var tDone = System.nanoTime();
                // Connection acquisition failed: entire window is acquire time.
                recordTimestamps(tStarted, tDone, tDone);
                return SamplerResponse.error(tStarted, tDone, e.getMessage());
            }
        };
    }

    private static SQLType sqlTypeOf(Class<?> type) {
        if (type == String.class) {
            return JDBCType.VARCHAR;
        }
        if (type == Integer.class || type == int.class) {
            return JDBCType.INTEGER;
        }
        throw new IllegalArgumentException("unsupported Java type mapping to SQL type");
    }

    private void recordTimestamps(long tStarted, long tAcquired, long tDone) {
        sampleCount.increment();
        acquireNanos.add(tAcquired - tStarted);
        queryNanos.add(tDone - tAcquired);
    }

    public long sampleCount() {
        return sampleCount.sum();
    }

    public long totalAcquireNanos() {
        return acquireNanos.sum();
    }

    public long totalQueryNanos() {
        return queryNanos.sum();
    }

    @Override
    public void close() {
        // Pool lifecycle is owned by JDBCSamplerPlugin.
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
