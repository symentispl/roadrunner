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
import io.roadrunner.samplers.spi.cli.OptionConverter;
import io.roadrunner.samplers.spi.cli.SamplerOption;
import io.roadrunner.samplers.spi.cli.SamplerOptionGroup;
import io.roadrunner.samplers.spi.cli.SamplerParameter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;

public class AbSamplerOptions implements SamplerOptions<AbSamplerProvider> {
    @SamplerParameter(paramLabel = "url", description = "HTTP server URL")
    public URI uri;

    @SamplerOption(names = "-m", description = "HTTP method (default: GET)", defaultValue = "GET")
    public String method = "GET";

    @SamplerOptionGroup
    public FileContent fileContent;

    public static class FileContent {
        @SamplerOption(names = "-p", description = "File containing data to POST")
        public Path postFile;

        @SamplerOption(names = "-u", description = "File containing data to PUT")
        public Path putFile;
    }

    @SamplerOption(names = "-T", description = "Content-type header for POST/PUT data", defaultValue = "text/plain")
    public String contentType = "text/plain";

    @SamplerOption(names = "-s", description = "Maximum seconds to wait for each response (default: 30)", defaultValue = "30")
    public int timeout = 30;

    @SamplerOption(names = "-i", description = "Use HEAD instead of GET", defaultValue = "false")
    public boolean useHEAD = false;

    @SamplerOption(names = "-H", description = "Add a custom HTTP header, e.g. 'Accept-Encoding: gzip' (can be repeated)")
    public String[] headers;

    @SamplerOption(
            names = "-X",
            description = "Send requests through this proxy server, in host:port format",
            converter = InetSocketAddressConverter.class)
    public InetSocketAddress proxyServer;

    @SamplerOption(
            names = "-B",
            description = "Local network address to send requests from (rarely needed)",
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

    @Override
    public String commandDescription() {
        return "Apache HTTP server benchmarking tool implementation";
    }

    public static class InetSocketAddressConverter implements OptionConverter<InetSocketAddress> {
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

    public static class InetAddressConverter implements OptionConverter<InetAddress> {
        @Override
        public InetAddress convert(String s) throws Exception {
            return InetAddress.getByName(s);
        }
    }
}
