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

import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.protocol.Protocol;
import io.roadrunner.protocols.spi.ProtocolProvider;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(description = "Apache HTTP server benchmarking tool implementation")
public class AbProtocolProvider implements ProtocolProvider {

    @Parameters(paramLabel = "url", description = "HTTP server URL")
    public URI uri;

    @Option(names = "-m", description = "HTTP method (default: GET)", defaultValue = "GET")
    public String method = "GET";

    @Option(names = "-p", description = "File containing data to POST")
    public Path postFile;

    @Option(names = "-u", description = "File containing data to PUT")
    public Path putFile;

    @Option(names = "-T", description = "Content-type header for POST/PUT data", defaultValue = "text/plain")
    public String contentType = "text/plain";

    @Option(
            names = "-s",
            description = "Seconds to max. wait for each response. Default is 30 seconds",
            defaultValue = "30")
    public int timeout = 30;

    @Option(names = "-i", description = "Use HEAD instead of GET", defaultValue = "false")
    public boolean useHEAD = false;

    @Option(
            names = "-H",
            description =
                    "Add Arbitrary header line, eg. 'Accept-Encoding: gzip'. Inserted after all normal header lines. (repeatable)")
    public String[] headers;

    @Option(
            names = "-X",
            description = "Proxy server and port number to use",
            converter = InetSocketAddressConverter.class)
    private InetSocketAddress proxyServer;

    @Option(
            names = "-B",
            description = "Address to bind to when making outgoing connections",
            converter = InetAddressConverter.class)
    private InetAddress localAddress;

    public AbProtocolProvider() {}

    @Override
    public String name() {
        return "ab";
    }

    @Override
    public Protocol newProtocol() {
        var httpClientBuilder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS);

        if (localAddress != null) {
            httpClientBuilder.localAddress(localAddress);
        }

        if (proxyServer != null) {
            httpClientBuilder.proxy(ProxySelector.of(proxyServer));
        }

        var httpClient = httpClientBuilder.build();

        var effectiveMethod = method;
        if (postFile != null) {
            effectiveMethod = "POST";
        } else if (putFile != null) {
            effectiveMethod = "PUT";
        } else if (method.equals("GET") && useHEAD) {
            effectiveMethod = "HEAD";
        }

        HttpRequest request =
                switch (effectiveMethod) {
                    case "GET" -> newBuilder().GET().build();
                    case "POST" ->
                        newBuilder()
                                .POST(ofFilePublisher(postFile))
                                .header("Content-Type", contentType)
                                .build();
                    case "PUT" ->
                        newBuilder()
                                .PUT(ofFilePublisher(putFile))
                                .header("Content-Type", contentType)
                                .build();
                    case "HEAD" -> newBuilder().HEAD().build();
                    case "DELETE" -> newBuilder().DELETE().build();
                    default ->
                        throw new IllegalArgumentException("Unsupported HTTP method: %s".formatted(effectiveMethod));
                };

        return () -> {
            try {
                var startTime = System.nanoTime();
                var httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
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

    private HttpRequest.BodyPublisher ofFilePublisher(Path file) {
        try {
            return HttpRequest.BodyPublishers.ofFile(file);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    private HttpRequest.Builder newBuilder() {
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout));
        Arrays.stream(Objects.requireNonNullElseGet(headers, () -> new String[0]))
                .map(s -> {
                    var z = s.split("=");
                    if (z.length == 2) {
                        return Map.entry(z[0], z[1]);
                    } else {
                        throw new IllegalArgumentException(
                                "Invalid header: %s, expected format: key=value".formatted(s));
                    }
                })
                .forEach((entry) -> builder.header(entry.getKey(), entry.getValue()));
        return builder;
    }

    @Override
    public void close() {}

    public static class InetSocketAddressConverter implements CommandLine.ITypeConverter<InetSocketAddress> {
        @Override
        public InetSocketAddress convert(String s) throws Exception {
            var strings = s.split(":");
            if (strings.length == 1) {
                return new InetSocketAddress(strings[0], 80);
            } else if (strings.length == 2) {
                return new InetSocketAddress(strings[0], Integer.parseInt(strings[1]));
            }
            throw new IllegalArgumentException("Invalid proxy format: %s, expected format: host:port".formatted(s));
        }
    }

    public static class InetAddressConverter implements CommandLine.ITypeConverter<InetAddress> {
        @Override
        public InetAddress convert(String s) throws Exception {
            return InetAddress.getByName(s);
        }
    }
}
