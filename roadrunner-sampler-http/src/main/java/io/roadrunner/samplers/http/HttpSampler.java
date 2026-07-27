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
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerSinkRegistrar;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.function.Function;

/**
 * Extension-point methods class for the HTTP sampler. Each method ({@link #GET(String)},
 * {@link #POST(String, String)}, {@link #PUT(String, String)}, {@link #DELETE(String)}) is bound
 * from a CLI operation expression such as {@code GET("http://localhost:8080/")} via
 * {@link io.roadrunner.samplers.spi.SamplerExtensionPoint}.
 */
public class HttpSampler implements SamplerSinkRegistrar {

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
        return request(url, request -> request.GET());
    }

    public Sampler POST(String url, String body) {
        return request(url, request -> request.POST(BodyPublishers.ofString(body)));
    }

    public Sampler PUT(String url, String body) {
        return request(url, request -> request.PUT(BodyPublishers.ofString(body)));
    }

    public Sampler DELETE(String url) {
        return request(url, request -> request.DELETE());
    }

    private Sampler request(String uriTemplate, Function<HttpRequest.Builder, HttpRequest.Builder> requestMapping) {
        return (parameters, builder) -> {
            var request = requestMapping
                    .apply(HttpRequest.newBuilder(URIBuilder.replace(uriTemplate, parameters.asMap())))
                    .build();
            var tStarted = System.nanoTime();
            try {
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

    private static String messageOf(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
