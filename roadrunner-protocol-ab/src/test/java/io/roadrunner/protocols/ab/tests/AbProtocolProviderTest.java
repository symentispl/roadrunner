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
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbProtocolProviderTest {

    private HttpServer server;
    private final int PORT = 8000;
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/test", new TestHandler());
        server.setExecutor(Executors.newFixedThreadPool(1));
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        lastMethod.set(null);
        lastContentType.set(null);
    }

    @Test
    void successfulRequest() {
        try (var provider = new AbProtocolProvider()) {
            provider.uri = URI.create("http://localhost:" + PORT + "/test");

            var protocol = provider.newProtocol();
            var event = protocol.execute();

            assertThat(event)
                    .asInstanceOf(type(ProtocolResponse.Response.class))
                    .satisfies(response -> {
                        assertThat(response.timestamp()).isGreaterThan(0);
                        assertThat(response.stopTime()).isGreaterThan(response.timestamp());
                    });
            assertThat(lastMethod.get()).isEqualTo("GET");
        }
    }

    @Test
    void errorRequest() {
        try (var provider = new AbProtocolProvider()) {
            provider.uri = URI.create("http://localhost:" + PORT + "/not-existing-endpoint");

            var protocol = provider.newProtocol();
            var event = protocol.execute();

            assertThat(event).asInstanceOf(type(ProtocolResponse.Error.class)).satisfies(response -> {
                assertThat(response.timestamp()).isGreaterThan(0);
                assertThat(response.stopTime()).isGreaterThan(response.timestamp());
            });
        }
    }

    @Test
    void postRequestWithFile() throws IOException {
        var tempFile = Files.createTempFile("post-body", ".txt");
        Files.writeString(tempFile, "hello=world");

        try (var provider = new AbProtocolProvider()) {
            provider.uri = URI.create("http://localhost:" + PORT + "/test");
            provider.postFile = tempFile;

            var event = provider.newProtocol().execute();

            assertThat(event).isInstanceOf(ProtocolResponse.Response.class);
            assertThat(lastMethod.get()).isEqualTo("POST");
            assertThat(lastContentType.get()).isEqualTo("text/plain");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void putRequestWithFile() throws IOException {
        var tempFile = Files.createTempFile("put-body", ".json");
        Files.writeString(tempFile, """
                {"key":"value"}""");

        try (var provider = new AbProtocolProvider()) {
            provider.uri = URI.create("http://localhost:" + PORT + "/test");
            provider.putFile = tempFile;
            provider.contentType = "application/json";

            var event = provider.newProtocol().execute();

            assertThat(event).isInstanceOf(ProtocolResponse.Response.class);
            assertThat(lastMethod.get()).isEqualTo("PUT");
            assertThat(lastContentType.get()).isEqualTo("application/json");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void customMethodDelete() {
        try (var provider = new AbProtocolProvider()) {
            provider.uri = URI.create("http://localhost:" + PORT + "/test");
            provider.method = "DELETE";

            var event = provider.newProtocol().execute();

            assertThat(event).isInstanceOf(ProtocolResponse.Response.class);
            assertThat(lastMethod.get()).isEqualTo("DELETE");
        }
    }

    @Test
    void customMethodHead() {
        try (var provider = new AbProtocolProvider()) {
            provider.uri = URI.create("http://localhost:" + PORT + "/test");
            provider.method = "HEAD";

            var event = provider.newProtocol().execute();

            assertThat(event).isInstanceOf(ProtocolResponse.Response.class);
            assertThat(lastMethod.get()).isEqualTo("HEAD");
        }
    }

    private class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            lastMethod.set(exchange.getRequestMethod());
            var ct = exchange.getRequestHeaders().getFirst("Content-Type");
            if (ct != null) {
                lastContentType.set(ct);
            }
            // Consume request body
            exchange.getRequestBody().readAllBytes();
            byte[] response = "Hello, Roadrunner!".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.getResponseBody().close();
        }
    }
}
