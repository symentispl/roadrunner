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

import java.util.List;
import org.junit.jupiter.api.Test;

class SamplerExtensionPointDescriptorTest {

    @Test
    void usageExpressionWithSingleParameter() {
        var descriptor = new SamplerExtensionPointDescriptor("query", List.of("sql"), "Execute a SQL query");

        assertThat(descriptor.usageExpression()).isEqualTo("query(<sql>)");
    }

    @Test
    void usageExpressionWithNoParameters() {
        var descriptor = new SamplerExtensionPointDescriptor("noArgs", List.of(), "No-argument operation");

        assertThat(descriptor.usageExpression()).isEqualTo("noArgs()");
    }

    @Test
    void usageExpressionWithMultipleParameters() {
        var descriptor =
                new SamplerExtensionPointDescriptor("post", List.of("url", "body"), "Send an HTTP POST request");

        assertThat(descriptor.usageExpression()).isEqualTo("post(<url>, <body>)");
    }

    @Test
    void parameterNamesListIsImmutable() {
        var descriptor = new SamplerExtensionPointDescriptor("query", List.of("sql"), "Execute a SQL query");

        assertThat(descriptor.parameterNames()).containsExactly("sql");
    }
}
