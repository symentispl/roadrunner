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

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Supplier;

public class AbSamplerProvider implements SamplerProvider {

    private final HttpClient httpClient;
    private final Supplier<HttpRequest> requestSupplier;

    public AbSamplerProvider(HttpClient httpClient, Supplier<HttpRequest> requestSupplier) {
        this.httpClient = httpClient;
        this.requestSupplier = requestSupplier;
    }

    @Override
    public Sampler newSampler() {
        var request = requestSupplier.get();
        return () -> {
            try {
                var startTime = System.nanoTime();
                var httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                var stopTime = System.nanoTime();
                if (httpResponse.statusCode() == 200) {
                    return SamplerResponse.response(startTime, stopTime, httpResponse);
                } else {
                    return SamplerResponse.error(startTime, stopTime, "");
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
