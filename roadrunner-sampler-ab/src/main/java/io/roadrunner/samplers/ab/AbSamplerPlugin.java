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
package io.roadrunner.samplers.ab;

import io.roadrunner.samplers.spi.SamplerPlugin;
import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class AbSamplerPlugin implements SamplerPlugin<AbSamplerProvider, AbSamplerOptions> {
    @Override
    public String name() {
        return "ab";
    }

    @Override
    public AbSamplerProvider newSamplerProvider(AbSamplerOptions options) {
        var httpClientBuilder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS);

        if (options.localAddress != null) {
            httpClientBuilder.localAddress(options.localAddress);
        }

        if (options.proxyServer != null) {
            httpClientBuilder.proxy(ProxySelector.of(options.proxyServer));
        }

        var httpClient = httpClientBuilder.build();

        var effectiveMethod = options.method;
        if (options.fileContent != null && options.fileContent.postFile != null) {
            effectiveMethod = "POST";
        } else if (options.fileContent != null && options.fileContent.putFile != null) {
            effectiveMethod = "PUT";
        } else if (options.method.equals("GET") && options.useHEAD) {
            effectiveMethod = "HEAD";
        }

        Supplier<HttpRequest> requestSupplier =
                switch (effectiveMethod) {
                    case "GET" -> () -> newBuilder(options).GET().build();
                    case "POST" ->
                        () -> newBuilder(options)
                                .POST(ofFilePublisher(options.fileContent.postFile))
                                .header("Content-Type", options.contentType)
                                .build();
                    case "PUT" ->
                        () -> newBuilder(options)
                                .PUT(ofFilePublisher(options.fileContent.putFile))
                                .header("Content-Type", options.contentType)
                                .build();
                    case "HEAD" -> () -> newBuilder(options).HEAD().build();
                    case "DELETE" -> () -> newBuilder(options).DELETE().build();
                    default ->
                        throw new IllegalArgumentException("Unsupported HTTP method: %s".formatted(effectiveMethod));
                };

        return new AbSamplerProvider(httpClient, requestSupplier);
    }

    @Override
    public AbSamplerOptions options() {
        return new AbSamplerOptions(this);
    }

    private HttpRequest.BodyPublisher ofFilePublisher(Path file) {
        try {
            return HttpRequest.BodyPublishers.ofFile(file);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    private HttpRequest.Builder newBuilder(AbSamplerOptions options) {
        var builder = HttpRequest.newBuilder(options.uri).timeout(Duration.ofSeconds(options.timeout));
        Arrays.stream(Objects.requireNonNullElseGet(options.headers, () -> new String[0]))
                .map(s -> {
                    var z = s.split(":");
                    if (z.length == 2) {
                        return Map.entry(z[0].trim(), z[1].trim());
                    } else {
                        throw new IllegalArgumentException(
                                "Invalid header: %s, expected format: 'key: value'".formatted(s));
                    }
                })
                .forEach((entry) -> builder.header(entry.getKey(), entry.getValue()));
        return builder;
    }
}
