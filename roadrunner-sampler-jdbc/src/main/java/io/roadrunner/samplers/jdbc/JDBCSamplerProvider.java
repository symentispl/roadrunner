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

import com.zaxxer.hikari.pool.HikariPool;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;
import java.sql.SQLException;

public class JDBCSamplerProvider implements SamplerProvider {

    private final HikariPool pool;
    private final String query;

    public JDBCSamplerProvider(HikariPool pool, String query) {
        this.pool = pool;
        this.query = query;
    }

    @Override
    public Sampler newSampler() {
        return () -> {
            try (var cnn = pool.getConnection();
                    var stmt = cnn.createStatement()) {
                var startTime = System.nanoTime();
                try {
                    boolean execute = stmt.execute(query);
                    var stopTime = System.nanoTime();
                    return SamplerResponse.response(startTime, stopTime, execute);
                } catch (SQLException e) {
                    var stopTime = System.nanoTime();
                    return SamplerResponse.error(startTime, stopTime, e.getMessage());
                }

            } catch (SQLException e) {
                return SamplerResponse.error(System.nanoTime(), System.nanoTime(), e.getMessage());
            }
        };
    }

    @Override
    public void close() throws Exception {
        if (pool != null) {
            pool.shutdown();
        }
    }
}
