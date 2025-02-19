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
package io.roadrunner.protocols.ab;

import io.roadrunner.options.CliOptionsBuilder;
import io.roadrunner.protocols.spi.ProtocolProvider;
import io.roadrunner.protocols.spi.ProtocolRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AbProtocolProvider implements ProtocolProvider<AbProtocolOptions> {

    private final HttpClient httpClient;
    private final ExecutorService cachedThreadPool;

    public AbProtocolProvider() {
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        cachedThreadPool = Executors.newCachedThreadPool();
    }

    @Override
    public String name() {
        return "ab";
    }

    @Override
    public AbProtocolOptions requestOptions(String[] protocolArgs) {
        var optionsBuilder = new CliOptionsBuilder();
        var optionsBinding = optionsBuilder.build(AbProtocolOptions.class);
        try {
            var vmProtocolOptions = optionsBinding.newInstance(protocolArgs);
            return vmProtocolOptions;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ProtocolRequest request(AbProtocolOptions requestOptions) {
        var httpRequest = HttpRequest.newBuilder(URI.create(requestOptions.uri())).GET().build();
        var bodyHandler = HttpResponse.BodyHandlers.discarding();
        return () -> CompletableFuture.runAsync(() -> {
                    try {
                        var httpResponse = httpClient.send(httpRequest, bodyHandler);
                        if (httpResponse.statusCode() != 200) {
                            System.out.println("error");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }, cachedThreadPool)
                .join();
    }

    @Override
    public void close() {
        httpClient.close();
        cachedThreadPool.close();
    }
}
