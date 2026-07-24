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

import io.roadrunner.api.events.SamplerResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpSampler {

    private final HttpClient httpClient;

    public HttpSampler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    SamplerResponse<?> GET(URI uri) {
        var request = HttpRequest.newBuilder(uri).GET().build();
        try {
            long nanoTime = System.nanoTime();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return SamplerResponse.Response.response(nanoTime, System.nanoTime(), response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.interrupted();
            throw new RuntimeException(e);
        }
    }
}
