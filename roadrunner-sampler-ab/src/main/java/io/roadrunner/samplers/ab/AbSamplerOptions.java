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

import io.roadrunner.samplers.spi.SamplerOptions;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(description = "Apache HTTP server benchmarking tool implementation")
public class AbSamplerOptions implements SamplerOptions<AbSamplerProvider> {
    @Parameters(paramLabel = "url", description = "HTTP server URL")
    public URI uri;

    @Option(names = "-m", description = "HTTP method (default: GET)", defaultValue = "GET")
    public String method = "GET";

    @ArgGroup
    public FileContent fileContent;

    public static class FileContent {
        @Option(names = "-p", description = "File containing data to POST")
        public Path postFile;

        @Option(names = "-u", description = "File containing data to PUT")
        public Path putFile;
    }

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
    public InetSocketAddress proxyServer;

    @Option(
            names = "-B",
            description = "Address to bind to when making outgoing connections",
            converter = InetAddressConverter.class)
    public InetAddress localAddress;

    private final AbSamplerPlugin abSamplerPlugin;

    public AbSamplerOptions(AbSamplerPlugin abSamplerPlugin) {
        this.abSamplerPlugin = abSamplerPlugin;
    }

    @Override
    public AbSamplerProvider samplerProvider() {
        return abSamplerPlugin.newSamplerProvider(this);
    }

    public static class InetSocketAddressConverter implements CommandLine.ITypeConverter<InetSocketAddress> {
        @Override
        public InetSocketAddress convert(String s) {
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
