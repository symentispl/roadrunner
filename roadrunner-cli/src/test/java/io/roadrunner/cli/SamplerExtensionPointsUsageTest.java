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
package io.roadrunner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.samplers.spi.SamplerExtensionPointDescriptor;
import java.util.List;
import org.junit.jupiter.api.Test;

class SamplerExtensionPointsUsageTest {

    @Test
    void emptyListProducesEmptyString() {
        assertThat(SamplerExtensionPointsUsage.format(List.of())).isEmpty();
    }

    @Test
    void singleExtensionPointIsFormatted() {
        var descriptor = new SamplerExtensionPointDescriptor("query", List.of("sql"), "Execute a SQL query");

        String usage = SamplerExtensionPointsUsage.format(List.of(descriptor));

        assertThat(usage).contains("Expression syntax:");
        assertThat(usage).contains("query(<sql>)");
        assertThat(usage).contains("Execute a SQL query");
    }

    @Test
    void multipleExtensionPointsAreAligned() {
        var descriptors = List.of(
                new SamplerExtensionPointDescriptor("query", List.of("sql"), "Execute a SQL query"),
                new SamplerExtensionPointDescriptor("noArgs", List.of(), "No-argument operation"));

        String usage = SamplerExtensionPointsUsage.format(descriptors);

        assertThat(usage).contains("query(<sql>)");
        assertThat(usage).contains("noArgs()");
        assertThat(usage).contains("Execute a SQL query");
        assertThat(usage).contains("No-argument operation");
    }

    @Test
    void zeroArgExtensionPointIsFormatted() {
        var descriptor = new SamplerExtensionPointDescriptor("ping", List.of(), "Ping the server");

        String usage = SamplerExtensionPointsUsage.format(List.of(descriptor));

        assertThat(usage).contains("ping()");
        assertThat(usage).contains("Ping the server");
    }
}
