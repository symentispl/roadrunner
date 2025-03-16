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

import io.roadrunner.api.protocol.Protocol;
import io.roadrunner.api.protocol.ProtocolResponse;
import io.roadrunner.protocols.spi.ProtocolProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(description = "Apache HTTP server benchmarking tool implementation")
public class AbProtocolProvider implements ProtocolProvider {

    @Parameters(paramLabel = "url", description = "HTTP server URL")
    URI uri;

    public AbProtocolProvider() {}

    @Override
    public String name() {
        return "ab";
    }

    @Override
    public Protocol newProtocol() {
        var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        return () -> {
            try {
                var httpRequest = HttpRequest.newBuilder(uri).GET().build();
                var bodyHandler = HttpResponse.BodyHandlers.ofByteArray();
                var startTime = System.nanoTime();
                var httpResponse = httpClient.send(httpRequest, bodyHandler);
                var stopTime = System.nanoTime();
                if (httpResponse.statusCode() == 200) {
                    return ProtocolResponse.response(startTime, stopTime, httpResponse);
                } else {
                    return ProtocolResponse.error(startTime, stopTime, "");
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    public void close() {}
}
