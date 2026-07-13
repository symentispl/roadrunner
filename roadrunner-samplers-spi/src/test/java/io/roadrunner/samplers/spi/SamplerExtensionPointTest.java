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
package io.roadrunner.samplers.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.api.samplers.Sampler;
import org.junit.jupiter.api.Test;

class SamplerExtensionPointTest {

    public static class QueryFixture {
        private String lastSql;

        public Sampler query(String sql) {
            this.lastSql = sql;
            // Capture sql so the lambda isn't a non-capturing singleton, forcing a fresh
            // instance per call.
            return parameters -> SamplerResponse.empty(sql.length(), 0);
        }

        public Sampler noArgs() {
            return parameters -> SamplerResponse.empty(0, 0);
        }

        public String lastSql() {
            return lastSql;
        }
    }

    public static class NonStringParameterFixture {
        public Sampler withInt(int notAString) {
            return parameters -> SamplerResponse.empty(0, 0);
        }
    }

    @Test
    void bindsSingleArgumentMethodAndPassesTheLiteral() {
        var fixture = new QueryFixture();

        var sampler = SamplerExtensionPoint.bind(fixture, "query(\"SELECT 1\")").get();
        var response = sampler.execute(SamplerParameters.NONE);

        assertThat(fixture.lastSql()).isEqualTo("SELECT 1");
        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
    }

    @Test
    void bindsZeroArgumentMethodAmongMultipleCandidates() {
        var fixture = new QueryFixture();

        var sampler = SamplerExtensionPoint.bind(fixture, "noArgs()").get();
        var response = sampler.execute(SamplerParameters.NONE);

        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
    }

    @Test
    void eachSupplierInvocationProducesAFreshSampler() {
        var samplerSupplier = SamplerExtensionPoint.bind(new QueryFixture(), "query(\"SELECT 1\")");

        assertThat(samplerSupplier.get()).isNotSameAs(samplerSupplier.get());
    }

    @Test
    void malformedExpressionThrows() {
        assertThatThrownBy(() -> SamplerExtensionPoint.bind(new QueryFixture(), "query(\"unterminated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unterminated string literal");
    }

    @Test
    void unknownMethodNameThrows() {
        assertThatThrownBy(() -> SamplerExtensionPoint.bind(new QueryFixture(), "update(\"SELECT 1\")"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("update");
    }

    @Test
    void arityMismatchThrows() {
        assertThatThrownBy(() -> SamplerExtensionPoint.bind(new QueryFixture(), "query(\"a\", \"b\")"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }

    @Test
    void nonStringParameterThrows() {
        assertThatThrownBy(() -> SamplerExtensionPoint.bind(new NonStringParameterFixture(), "withInt(\"1\")"))
                .isInstanceOf(PluginInitializationException.class)
                .hasMessageContaining("withInt");
    }
}
