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

import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.samplers.spi.SamplerExtensionPoint;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;

public class JDBCSamplerProvider implements SamplerProvider {

    private final JDBCSampler jdbcSampler;
    private final Supplier<Sampler> samplerSupplier;

    public JDBCSamplerProvider(DataSource dataSource, String expressionText) {
        this.jdbcSampler = new JDBCSampler(dataSource);
        this.samplerSupplier = SamplerExtensionPoint.bind(jdbcSampler, expressionText);
    }

    @Override
    public Sampler newSampler() {
        return samplerSupplier.get();
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
