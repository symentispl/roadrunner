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
package io.roadrunner.api.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MetricRegistryTest {

    @Test
    void registersMetricsWithSequentialIds() {
        var registry = new MetricRegistry();

        var key1 = registry.register("bytes_read", MetricUnit.BYTES);
        var key2 = registry.register("row_count", MetricUnit.COUNT);

        assertThat(key1.id()).isEqualTo(0);
        assertThat(key1.name()).isEqualTo("bytes_read");
        assertThat(key1.unit()).isEqualTo(MetricUnit.BYTES);
        assertThat(key2.id()).isEqualTo(1);
        assertThat(key2.name()).isEqualTo("row_count");
        assertThat(key2.unit()).isEqualTo(MetricUnit.COUNT);
    }

    @Test
    void returnsAllRegisteredKeysInOrder() {
        var registry = new MetricRegistry();

        var key1 = registry.register("bytes_read", MetricUnit.BYTES);
        var key2 = registry.register("row_count", MetricUnit.COUNT);

        assertThat(registry.registeredKeys()).containsExactly(key1, key2);
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void emptyRegistryHasSizeZero() {
        var registry = new MetricRegistry();

        assertThat(registry.size()).isEqualTo(0);
        assertThat(registry.registeredKeys()).isEmpty();
    }
}
