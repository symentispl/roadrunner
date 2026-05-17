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
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;
import java.sql.SQLException;
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
        return () -> {
            var t0 = System.nanoTime();
            try (var cnn = dataSource.getConnection();
                    var stmt = cnn.createStatement()) {
                var tAcquired = System.nanoTime();
                try {
                    long rowCount;
                    boolean hasResultSet = stmt.execute(query);
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
                    record(t0, tAcquired, tDone);
                    return SamplerResponse.response(t0, tDone, rowCount);
                } catch (SQLException e) {
                    var tDone = System.nanoTime();
                    record(t0, tAcquired, tDone);
                    return SamplerResponse.error(t0, tDone, e.getMessage());
                }
            } catch (SQLException e) {
                var tDone = System.nanoTime();
                // Connection acquisition failed: entire window is acquire time.
                record(t0, tDone, tDone);
                return SamplerResponse.error(t0, tDone, e.getMessage());
            }
        };
    }

    private void record(long t0, long tAcquired, long tDone) {
        sampleCount.increment();
        acquireNanos.add(tAcquired - t0);
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
}
