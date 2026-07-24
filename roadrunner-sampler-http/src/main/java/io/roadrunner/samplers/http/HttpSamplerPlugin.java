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

import io.roadrunner.samplers.spi.SamplerExtensionPointDescriptor;
import io.roadrunner.samplers.spi.SamplerPlugin;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

public class HttpSamplerPlugin implements SamplerPlugin<HttpSamplerProvider, HttpSamplerOptions> {

    @Override
    public String name() {
        return "http";
    }

    @Override
    public HttpSamplerProvider newSamplerProvider(HttpSamplerOptions options) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(options.connectTimeoutMillis))
                .build();
        return new HttpSamplerProvider(httpClient, options.expression);
    }

    @Override
    public HttpSamplerOptions options() {
        return new HttpSamplerOptions(this);
    }

    @Override
    public List<SamplerExtensionPointDescriptor> extensionPoints() {
        return List.of(
                new SamplerExtensionPointDescriptor("GET", List.of("url"), "Execute an HTTP GET request"),
                new SamplerExtensionPointDescriptor("POST", List.of("url", "body"), "Execute an HTTP POST request"),
                new SamplerExtensionPointDescriptor("PUT", List.of("url", "body"), "Execute an HTTP PUT request"),
                new SamplerExtensionPointDescriptor("DELETE", List.of("url"), "Execute an HTTP DELETE request"));
    }
}
