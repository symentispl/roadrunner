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

import io.roadrunner.samplers.spi.SamplerExtension;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public class JDBCSamplerProvider extends SamplerExtension {

    private final JDBCSampler jdbcSampler;

    public JDBCSamplerProvider(DataSource dataSource, String expressionText) {
        this(new JDBCSampler(dataSource), expressionText);
    }

    private JDBCSamplerProvider(JDBCSampler jdbcSampler, String expressionText) {
        super(jdbcSampler, expressionText);
        this.jdbcSampler = jdbcSampler;
    }

    public long sampleCount() {
        return jdbcSampler.sampleCount();
    }

    public long totalAcquireNanos() {
        return jdbcSampler.totalAcquireNanos();
    }

    public long totalQueryNanos() {
        return jdbcSampler.totalQueryNanos();
    }

    @Override
    public void close() {
        // Pool lifecycle is owned by JDBCSamplerPlugin.
    }

    public Connection getConnection() throws SQLException {
        return jdbcSampler.getConnection();
    }
}
