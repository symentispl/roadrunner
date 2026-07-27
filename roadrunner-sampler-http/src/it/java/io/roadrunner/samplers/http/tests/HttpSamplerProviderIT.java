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
package io.roadrunner.samplers.http.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.samplers.http.HttpSamplerPlugin;
import io.roadrunner.samplers.spi.SamplerContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpSamplerProviderIT {

    private HttpServer server;
    private String baseUrl;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    @BeforeAll
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/echo", exchange -> {
            var body = readBody(exchange);
            requests.add(new RecordedRequest(exchange.getRequestMethod(), body));
            var payload = ("method=" + exchange.getRequestMethod() + ";body=" + body).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            try (var os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });

        server.createContext("/not-found", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        server.createContext("/boom", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        server.start();
        baseUrl = "http://127.0.0.1:%d".formatted(server.getAddress().getPort());
    }

    @AfterAll
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void getRequestReturnsResponse() {
        var response = execute("""
                GET("%s/${path}")""".formatted(baseUrl));
        assertThat(response)
                .asInstanceOf(type(SamplerResponse.Response.class))
                .satisfies(r -> {
                    assertThat(r.timestamp()).isGreaterThan(0);
                    assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                });
        assertThat(requests).contains(new RecordedRequest("GET", ""));
    }

    @Test
    void postRequestSendsBody() {
        var response = execute("""
                POST("%s/${path}", "payload-123")""".formatted(baseUrl));
        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
        assertThat(requests).contains(new RecordedRequest("POST", "payload-123"));
    }

    @Test
    void putRequestSendsBody() {
        var response = execute("""
                PUT("%s/${path}", "updated")""".formatted(baseUrl));
        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
        assertThat(requests).contains(new RecordedRequest("PUT", "updated"));
    }

    @Test
    void deleteRequestReturnsResponse() {
        var response = execute("""
                DELETE("%s/${path}")""".formatted(baseUrl));
        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
        assertThat(requests).contains(new RecordedRequest("DELETE", ""));
    }

    @Test
    void successRecordsStatusAttachment() {
        try (var plugin = new HttpSamplerPlugin()) {
            var options = plugin.options();
            options.expression = "GET(\"%s/echo\")".formatted(baseUrl);
            try (var provider = plugin.newSamplerProvider(options)) {
                var ctx = SamplerContext.of(provider);
                var statusKey = ctx.attachmentRegistry().registeredKeys().stream()
                        .filter(k -> "status".equals(k.name()))
                        .findFirst()
                        .orElseThrow();
                try (var sampler = provider.newSampler()) {
                    var response = sampler.execute(SamplerParameters.NONE, ctx.newResponseBuilder());
                    assertThat(response)
                            .asInstanceOf(type(SamplerResponse.Response.class))
                            .satisfies(r -> assertThat(r.attachmentValueAt(statusKey)).isEqualTo("200"));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void notFoundStatusReportedAsError() {
        var response = execute("""
                GET("%s/not-found")""".formatted(baseUrl));
        assertThat(response)
                .asInstanceOf(type(SamplerResponse.Error.class))
                .satisfies(r -> {
                    assertThat(r.timestamp()).isGreaterThan(0);
                    assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                    assertThat(r.message()).contains("404");
                });
    }

    @Test
    void serverErrorStatusReportedAsError() {
        var response = execute("""
                DELETE("%s/boom")""".formatted(baseUrl));
        assertThat(response)
                .asInstanceOf(type(SamplerResponse.Error.class))
                .satisfies(r -> assertThat(r.message()).contains("500"));
    }

    @Test
    void connectionFailureReportedAsError() {
        // Port 1 is reserved/unbound: connecting fails fast.
        var response = execute("""
                GET("http://127.0.0.1:1/${path}")""");
        assertThat(response)
                .asInstanceOf(type(SamplerResponse.Error.class))
                .satisfies(r -> {
                    assertThat(r.timestamp()).isGreaterThan(0);
                    assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                    assertThat(r.message()).isNotBlank();
                });
    }

    private SamplerResponse<?> execute(String expression) {
        try (var plugin = new HttpSamplerPlugin()) {
            var options = plugin.options();
            options.expression = expression;
            try (var provider = plugin.newSamplerProvider(options)) {
                var ctx = SamplerContext.of(provider);
                try (var sampler = provider.newSampler()) {
                    return sampler.execute(SamplerParameters.of("path", "echo"), ctx.newResponseBuilder());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record RecordedRequest(String method, String body) {
    }
}
