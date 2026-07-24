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

import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.api.samplers.Sampler;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.SQLType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import javax.sql.DataSource;

/**
 * Extension-point methods class for the JDBC sampler: {@link #query(String)} is bound from a CLI
 * {@code query("SELECT ...")} expression via {@link io.roadrunner.samplers.spi.SamplerExtensionPoint}.
 */
public class JDBCSampler {

    private final DataSource dataSource;
    private final LongAdder sampleCount = new LongAdder();
    private final LongAdder acquireNanos = new LongAdder();
    private final LongAdder queryNanos = new LongAdder();

    public JDBCSampler(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Sampler query(String sql) {
        return (SamplerParameters parameters) -> {
            var tStarted = System.nanoTime();
            try (var cnn = dataSource.getConnection();
                    var stmt = cnn.prepareStatement(sql)) {
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

    /**
     * Java to SQL type mapping per the JDBC 4.3 spec (table B-4 / appendix B). Only the exact
     * runtime classes a {@link io.roadrunner.api.parameters.ParameterSource} can produce are
     * listed — subtypes (e.g. {@code java.util.Date}, {@code Number}) and primitive class
     * literals (unreachable since values arrive via {@link Object#getClass()}) are out of scope.
     */
    private static final Map<Class<?>, SQLType> SQL_TYPE_BY_JAVA_TYPE = Map.ofEntries(
            entry(String.class, JDBCType.VARCHAR),
            entry(Character.class, JDBCType.CHAR),
            entry(Boolean.class, JDBCType.BOOLEAN),
            entry(Byte.class, JDBCType.TINYINT),
            entry(Short.class, JDBCType.SMALLINT),
            entry(Integer.class, JDBCType.INTEGER),
            entry(Long.class, JDBCType.BIGINT),
            entry(Float.class, JDBCType.REAL),
            entry(Double.class, JDBCType.DOUBLE),
            entry(BigDecimal.class, JDBCType.DECIMAL),
            entry(BigInteger.class, JDBCType.NUMERIC),
            entry(byte[].class, JDBCType.VARBINARY),
            entry(java.sql.Date.class, JDBCType.DATE),
            entry(java.sql.Time.class, JDBCType.TIME),
            entry(java.sql.Timestamp.class, JDBCType.TIMESTAMP),
            entry(LocalDate.class, JDBCType.DATE),
            entry(LocalTime.class, JDBCType.TIME),
            entry(LocalDateTime.class, JDBCType.TIMESTAMP),
            entry(OffsetTime.class, JDBCType.TIME_WITH_TIMEZONE),
            entry(OffsetDateTime.class, JDBCType.TIMESTAMP_WITH_TIMEZONE));

    private static SQLType sqlTypeOf(Class<?> type) {
        return requireNonNull(
                SQL_TYPE_BY_JAVA_TYPE.get(type),
                () -> "unsupported Java type for JDBC parameter binding: " + type.getName());
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

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
