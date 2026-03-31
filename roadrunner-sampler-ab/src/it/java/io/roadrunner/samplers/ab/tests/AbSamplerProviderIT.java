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
package io.roadrunner.samplers.ab.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.samplers.ab.AbSamplerOptions;
import io.roadrunner.samplers.ab.AbSamplerPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbSamplerProviderIT {

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
        try (var plugin = new AbSamplerPlugin()) {
            var options = plugin.options();
            options.uri = URI.create("http://localhost:" + PORT + "/test");
            options.headers = new String[]{"Accept-Encoding: gzip"};
            try (var provider = plugin.newSamplerProvider(options); var sampler = provider.newSampler()) {
                var event = sampler.execute();
                assertThat(event).asInstanceOf(type(SamplerResponse.Response.class)).satisfies(response -> {
                    assertThat(response.timestamp()).isGreaterThan(0);
                    assertThat(response.stopTime()).isGreaterThan(response.timestamp());
                });
                assertThat(lastMethod.get()).isEqualTo("GET");
            }
        }
    }

    @Test
    void errorRequest() {
        try (var plugin = new AbSamplerPlugin()) {
            var options = plugin.options();
            options.uri = URI.create("http://localhost:" + PORT + "/not-existing-endpoint");
            try (var provider = plugin.newSamplerProvider(options); var sampler = provider.newSampler()) {
                var event = sampler.execute();
                assertThat(event).asInstanceOf(type(SamplerResponse.Error.class)).satisfies(response -> {
                    assertThat(response.timestamp()).isGreaterThan(0);
                    assertThat(response.stopTime()).isGreaterThan(response.timestamp());
                });
            }
        }
    }

    @Test
    void postRequestWithFile() throws IOException {
        var tempFile = Files.createTempFile("post-body", ".txt");
        Files.writeString(tempFile, "hello=world");

        try (var plugin = new AbSamplerPlugin()) {
            var options = plugin.options();
            options.uri = URI.create("http://localhost:" + PORT + "/test");
            options.fileContent = new AbSamplerOptions.FileContent();
            options.fileContent.postFile = tempFile;
            try (var provider = plugin.newSamplerProvider(options); var sampler = provider.newSampler()) {
                var event = sampler.execute();

                assertThat(event).isInstanceOf(SamplerResponse.Response.class);
                assertThat(lastMethod.get()).isEqualTo("POST");
                assertThat(lastContentType.get()).isEqualTo("text/plain");
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Test
    void putRequestWithFile() throws IOException {
        var tempFile = Files.createTempFile("put-body", ".json");
        Files.writeString(tempFile, """
                {"key":"value"}""");
        try (var plugin = new AbSamplerPlugin()) {
            var options = plugin.options();
            options.uri = URI.create("http://localhost:" + PORT + "/test");
            options.fileContent = new AbSamplerOptions.FileContent();
            options.fileContent.putFile = tempFile;
            options.contentType = "application/json";
            try (var provider = plugin.newSamplerProvider(options);
                 var sampler = provider.newSampler()) {
                var event = sampler.execute();
                assertThat(event).isInstanceOf(SamplerResponse.Response.class);
                assertThat(lastMethod.get()).isEqualTo("PUT");
                assertThat(lastContentType.get()).isEqualTo("application/json");
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Test
    void customMethodDelete() {
        try (var plugin = new AbSamplerPlugin()) {
            var options = plugin.options();
            options.uri = URI.create("http://localhost:" + PORT + "/test");
            options.method = "DELETE";
            try (var provider = plugin.newSamplerProvider(options);
                 var sampler = provider.newSampler()) {
                var event = sampler.execute();
                assertThat(event).isInstanceOf(SamplerResponse.Response.class);
                assertThat(lastMethod.get()).isEqualTo("DELETE");
            }
        }
    }

    @Test
    void customMethodHead() {
        try (var plugin = new AbSamplerPlugin()) {
            var options = plugin.options();

            options.uri = URI.create("http://localhost:" + PORT + "/test");
            options.method = "HEAD";
            try (var provider = plugin.newSamplerProvider(options);
                 var sampler = provider.newSampler()) {
                var event = sampler.execute();
                assertThat(event).isInstanceOf(SamplerResponse.Response.class);
                assertThat(lastMethod.get()).isEqualTo("HEAD");
            }
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
