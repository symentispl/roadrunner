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

import io.roadrunner.samplers.spi.SamplerExpressionException;
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

    @Test
    void errorHintOfAnEmptyListIsEmpty() {
        assertThat(SamplerExtensionPointsUsage.errorHint("http", List.of())).isEmpty();
    }

    @Test
    void errorHintListsAvailableOperationsAndAnExampleFromTheFirst() {
        var descriptors = List.of(
                new SamplerExtensionPointDescriptor("GET", List.of("url"), "Execute an HTTP GET request"),
                new SamplerExtensionPointDescriptor("POST", List.of("url", "body"), "Execute an HTTP POST request"));

        var hint = SamplerExtensionPointsUsage.errorHint("http", descriptors);

        assertThat(hint).contains("available: GET(<url>), POST(<url>, <body>)");
        assertThat(hint).contains("example:   roadrunner run -c 10 -n 100 http 'GET(\"url\")'");
    }

    @Test
    void errorHintExampleUsesOnlyTheFirstOperationEvenWithMultipleParameters() {
        var descriptors = List.of(new SamplerExtensionPointDescriptor("query", List.of("sql"), "Execute a SQL query"));

        var hint = SamplerExtensionPointsUsage.errorHint("jdbc", descriptors);

        assertThat(hint).contains("available: query(<sql>)");
        assertThat(hint).contains("example:   roadrunner run -c 10 -n 100 jdbc 'query(\"sql\")'");
    }

    @Test
    void enrichAppendsTheHintToTheOriginalMessage() {
        var original = new SamplerExpressionException("Expected '(' at position 3 in 'GET'");
        var extensionPoints = List.of(new SamplerExtensionPointDescriptor("GET", List.of("url"), "..."));

        var enriched = SamplerExtensionPointsUsage.enrich(original, "http", extensionPoints);

        assertThat(enriched.getMessage())
                .contains("Expected '(' at position 3 in 'GET'")
                .contains("available: GET(<url>)")
                .contains("example:   roadrunner run -c 10 -n 100 http 'GET(\"url\")'");
        // no cause: the message already embeds the original's text, and Main.report() joins a
        // cause chain's messages with ": ", so chaining here would print it twice.
        assertThat(enriched.getCause()).isNull();
    }

    @Test
    void enrichLeavesTheMessageUnchangedWhenThereAreNoExtensionPoints() {
        var original = new SamplerExpressionException("Expected '(' at position 3 in 'GET'");

        var enriched = SamplerExtensionPointsUsage.enrich(original, "zero", List.of());

        assertThat(enriched.getMessage()).isEqualTo("Expected '(' at position 3 in 'GET'");
    }

}
