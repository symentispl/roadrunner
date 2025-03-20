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
package io.roadrunner.protocols.ab.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.protocols.ab.AbProtocolProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbProtocolProviderTest {

    private HttpServer server;
    private final int PORT = 8000;

    @BeforeEach
    void setUp() throws IOException {
        // Start the HTTP server
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/test", new TestHandler());
        server.setExecutor(Executors.newFixedThreadPool(1));
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void successfulRequest() {
        try (var provider = new AbProtocolProvider()) {
            provider.uri = URI.create("http://localhost:" + PORT + "/test");

            var protocol = provider.newProtocol();

            // Execute the protocol
            var event = protocol.execute();

            // Verify it's a ProtocolResponse
            assertThat(event)
                    .asInstanceOf(type(ProtocolResponse.Response.class))
                    .satisfies(response -> {
                        assertThat(response.timestamp()).isGreaterThan(0);
                        assertThat(response.stopTime()).isGreaterThan(response.timestamp());
                    });
        }
    }

    @Test
    void errorRequest() {
        try (var provider = new AbProtocolProvider()) {
            provider.uri = URI.create("http://localhost:" + PORT + "/not-existing-endpoint");

            // Create a protocol instance
            var protocol = provider.newProtocol();

            // Execute the protocol
            var event = protocol.execute();

            // Verify it's a ProtocolResponse
            assertThat(event).asInstanceOf(type(ProtocolResponse.Error.class)).satisfies(response -> {
                assertThat(response.timestamp()).isGreaterThan(0);
                assertThat(response.stopTime()).isGreaterThan(response.timestamp());
            });
        }
    }

    private static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] response = "Hello, Roadrunner!".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        }
    }
}
