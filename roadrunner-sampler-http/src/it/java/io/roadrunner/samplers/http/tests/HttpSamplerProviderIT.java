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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpSamplerProviderIT {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private String baseUrl;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    @BeforeAll
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/echo", exchange -> {
            var body = readBody(exchange);
            var headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, values.get(0)));
            requests.add(new RecordedRequest(exchange.getRequestMethod(), body, headers));
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
        assertThat(requests).anyMatch(r -> r.method().equals("GET") && r.body().isEmpty());
    }

    @Test
    void postRequestSendsBody() {
        var response = execute("""
                POST("%s/${path}", "payload-123")""".formatted(baseUrl));
        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
        assertThat(requests).anyMatch(r -> r.method().equals("POST") && r.body().equals("payload-123"));
    }

    @Test
    void putRequestSendsBody() {
        var response = execute("""
                PUT("%s/${path}", "updated")""".formatted(baseUrl));
        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
        assertThat(requests).anyMatch(r -> r.method().equals("PUT") && r.body().equals("updated"));
    }

    @Test
    void deleteRequestReturnsResponse() {
        var response = execute("""
                DELETE("%s/${path}")""".formatted(baseUrl));
        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
        assertThat(requests).anyMatch(r -> r.method().equals("DELETE") && r.body().isEmpty());
    }

    @Test
    void headerParameterIsSentAsHttpHeader() {
        SequencedMap<String, Object> row = new LinkedHashMap<>();
        row.put("path", "echo");
        row.put("header:X-Api-Key", "secret-123");

        var response = execute(
                """
                GET("%s/${path}")""".formatted(baseUrl),
                SamplerParameters.of(row));

        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
        assertThat(requests)
                .anyMatch(r -> r.method().equals("GET") && "secret-123".equals(r.headers().get("X-Api-Key")));
    }

    @Test
    void bodyReferenceResolvesStringParameterValue() {
        SequencedMap<String, Object> row = new LinkedHashMap<>();
        row.put("path", "echo");
        row.put("payload", "from-parameter");

        var response = execute(
                """
                POST("%s/${path}", "@payload")""".formatted(baseUrl),
                SamplerParameters.of(row));

        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
        assertThat(requests).anyMatch(r -> r.method().equals("POST") && r.body().equals("from-parameter"));
    }

    @Test
    void bodyReferenceUploadsFileParameterValue() throws IOException {
        var file = tempDir.resolve("upload.txt");
        Files.writeString(file, "file-contents");

        SequencedMap<String, Object> row = new LinkedHashMap<>();
        row.put("path", "echo");
        row.put("payload", file.toFile());

        var response = execute(
                """
                PUT("%s/${path}", "@payload")""".formatted(baseUrl),
                SamplerParameters.of(row));

        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
        assertThat(requests).anyMatch(r -> r.method().equals("PUT") && r.body().equals("file-contents"));
    }

    @Test
    void bodyReferenceToMissingParameterReportedAsError() {
        var response = execute("""
                POST("%s/${path}", "@missing")""".formatted(baseUrl));

        assertThat(response)
                .asInstanceOf(type(SamplerResponse.Error.class))
                .satisfies(r -> assertThat(r.message()).contains("missing"));
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
        return execute(expression, SamplerParameters.of("path", "echo"));
    }

    private SamplerResponse<?> execute(String expression, SamplerParameters parameters) {
        try (var plugin = new HttpSamplerPlugin()) {
            var options = plugin.options();
            options.expression = expression;
            try (var provider = plugin.newSamplerProvider(options)) {
                var ctx = SamplerContext.of(provider);
                try (var sampler = provider.newSampler()) {
                    return sampler.execute(parameters, ctx.newResponseBuilder());
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

    private record RecordedRequest(String method, String body, Map<String, String> headers) {}
}
