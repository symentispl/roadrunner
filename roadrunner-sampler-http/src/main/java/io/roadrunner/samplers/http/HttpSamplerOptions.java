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

import io.roadrunner.samplers.spi.SamplerOptions;
import io.roadrunner.samplers.spi.cli.SamplerOption;
import io.roadrunner.samplers.spi.cli.SamplerParameter;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;

public class HttpSamplerOptions implements SamplerOptions<HttpSamplerProvider> {

    @SamplerParameter(
            paramLabel = "expression",
            description = "The operation to run on each request, e.g. GET(\"http://localhost:8080/\")")
    public String expression;

    @SamplerOption(names = "--connect-timeout", description = "Connection timeout in milliseconds (default: ${DEFAULT-VALUE})")
    public long connectTimeoutMillis = 10_000;

    @SamplerOption(
            names = "--request-timeout",
            description = "Timeout in milliseconds for a full request/response exchange, "
                    + "independent of --connect-timeout (default: ${DEFAULT-VALUE})")
    public long requestTimeoutMillis = 10_000;

    @SamplerOption(
            names = "--follow-redirects",
            description =
                    "Specifies whether requests will automatically follow redirects issued by the server. (default: ${DEFAULT-VALUE})")
    public HttpClient.Redirect followRedirects = HttpClient.Redirect.NEVER;

    @SamplerOption(
            names = "--proxy-address",
            description = "Specifies whether requests will user given proxy address",
            converter = InetSocketAddressConverter.class)
    public InetSocketAddress proxyAddress;

    private final HttpSamplerPlugin samplerPlugin;

    public HttpSamplerOptions(HttpSamplerPlugin samplerPlugin) {
        this.samplerPlugin = samplerPlugin;
    }

    @Override
    public HttpSamplerProvider samplerProvider() {
        return samplerPlugin.newSamplerProvider(this);
    }

    @Override
    public String commandDescription() {
        return "HTTP protocol, executes a HTTP request";
    }
}
