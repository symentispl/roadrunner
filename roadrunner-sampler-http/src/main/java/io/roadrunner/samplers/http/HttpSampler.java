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
package io.roadrunner.samplers.http;

import io.roadrunner.api.attachments.AttachmentKey;
import io.roadrunner.api.attachments.AttachmentRegistry;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerSinkRegistrar;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Extension-point methods class for the HTTP sampler. Each method ({@link #GET(String)},
 * {@link #POST(String, String)}, {@link #PUT(String, String)}, {@link #DELETE(String)}) is bound
 * from a CLI operation expression such as {@code GET("http://localhost:8080/")} via
 * {@link io.roadrunner.samplers.spi.SamplerExtensionPoint}.
 * <p>
 * Per-request {@code SamplerParameters} (e.g. from a CSV parameter source) can add headers and
 * supply the request body, on top of the {@code ${...}} uri template substitution handled by
 * {@link URIBuilder}:
 * <ul>
 *   <li>a parameter named {@code header:<name>} becomes an HTTP header</li>
 *   <li>a {@code POST}/{@code PUT} body argument of the form {@code @<name>} is resolved at
 *   request time from the parameter named {@code <name>} instead of being sent as literal text —
 *   as a file upload ({@link BodyPublishers#ofFile}) if the parameter's value is a {@link File}
 *   (see CSV {@code :file}-typed columns), otherwise as a string</li>
 * </ul>
 */
public class HttpSampler implements SamplerSinkRegistrar {

    private static final String HEADER_PARAMETER_PREFIX = "header:";
    private static final String BODY_PARAMETER_REFERENCE_PREFIX = "@";

    private final HttpClient httpClient;
    private AttachmentKey statusKey;

    public HttpSampler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void registerAttachments(AttachmentRegistry registry) {
        this.statusKey = registry.register("status");
    }

    public Sampler GET(String url) {
        return request(url, (request, parameters) -> request.GET());
    }

    public Sampler POST(String url, String body) {
        return request(url, (request, parameters) -> request.POST(bodyPublisherOf(body, parameters)));
    }

    public Sampler PUT(String url, String body) {
        return request(url, (request, parameters) -> request.PUT(bodyPublisherOf(body, parameters)));
    }

    public Sampler DELETE(String url) {
        return request(url, (request, parameters) -> request.DELETE());
    }

    private Sampler request(
            String uriTemplate,
            BiFunction<HttpRequest.Builder, SamplerParameters, HttpRequest.Builder> requestMapping) {
        return (parameters, builder) -> {
            var tStarted = System.nanoTime();
            try {
                // single pass over asMap(): TypedSamplerParameters converts on every call, and
                // header:/body values never appear in the uri template, so there's no reason to
                // convert+encode them twice (once here, once for URIBuilder.replace).
                Map<String, Object> urlParameters = new HashMap<>();
                Map<String, String> headers = new HashMap<>();
                for (var entry : parameters.asMap().entrySet()) {
                    var name = entry.getKey();
                    if (name.startsWith(HEADER_PARAMETER_PREFIX)) {
                        headers.put(name.substring(HEADER_PARAMETER_PREFIX.length()), String.valueOf(entry.getValue()));
                    } else {
                        urlParameters.put(name, entry.getValue());
                    }
                }
                var requestBuilder = HttpRequest.newBuilder(URIBuilder.replace(uriTemplate, urlParameters));
                headers.forEach(requestBuilder::header);
                var request = requestMapping.apply(requestBuilder, parameters).build();

                HttpResponse<byte[]> response = httpClient.send(request, BodyHandlers.ofByteArray());
                var tDone = System.nanoTime();
                var statusCode = response.statusCode();
                if (statusCode >= 400) {
                    return builder.error(tStarted, tDone, "HTTP status " + statusCode);
                }
                return builder.response(
                        tStarted, tDone, samplerSink -> samplerSink.attach(statusKey, Integer.toString(statusCode)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return builder.error(tStarted, System.nanoTime(), messageOf(e));
            } catch (Exception e) {
                return builder.error(tStarted, System.nanoTime(), messageOf(e));
            }
        };
    }

    private static BodyPublisher bodyPublisherOf(String literalBody, SamplerParameters parameters) {
        if (!literalBody.startsWith(BODY_PARAMETER_REFERENCE_PREFIX)) {
            return BodyPublishers.ofString(literalBody);
        }
        var name = literalBody.substring(BODY_PARAMETER_REFERENCE_PREFIX.length());
        var value = parameters.valueOf(name);
        if (value == null) {
            throw new IllegalArgumentException(
                    "No parameter named '%s' for body reference '%s'".formatted(name, literalBody));
        }
        if (value instanceof File file) {
            try {
                return BodyPublishers.ofFile(file.toPath());
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }
        }
        return BodyPublishers.ofString(String.valueOf(value));
    }

    private static String messageOf(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
