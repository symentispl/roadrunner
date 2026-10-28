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
package io.roadrunner.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.samplers.spi.SamplerOptions;
import io.roadrunner.samplers.spi.cli.OptionConverter;
import io.roadrunner.samplers.spi.cli.SamplerOption;
import io.roadrunner.samplers.spi.cli.SamplerOptionGroup;
import io.roadrunner.samplers.spi.cli.SamplerParameter;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class SamplerOptionsSpecsTest {

    public static class HostPort implements OptionConverter<InetSocketAddress> {
        @Override
        public InetSocketAddress convert(String value) {
            var parts = value.split(":");
            return new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
        }
    }

    public static class Options implements SamplerOptions<SamplerProvider> {
        @SamplerParameter(paramLabel = "expression", description = "what to run")
        public String expression;

        @SamplerOption(names = {"-t", "--timeout"}, description = "timeout (default: ${DEFAULT-VALUE})")
        public long timeout = 5000;

        @SamplerOption(names = "-m", defaultValue = "GET")
        public String method = "GET";

        @SamplerOption(names = "--flag")
        public boolean flag;

        @SamplerOption(names = "--url", required = true)
        public String url;

        @SamplerOption(names = "-H")
        public String[] headers;

        @SamplerOption(names = "-X", converter = HostPort.class)
        public InetSocketAddress proxy;

        @SamplerOptionGroup
        public Content content;

        public static class Content {
            @SamplerOption(names = "-p")
            public Path post;

            @SamplerOption(names = "-u")
            public Path put;
        }

        @Override
        public SamplerProvider samplerProvider() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String commandDescription() {
            return "test sampler";
        }
    }

    private static CommandLine parse(Options options, String... args) {
        var commandLine = new CommandLine(SamplerOptionsSpecs.create(options));
        commandLine.parseArgs(args);
        return commandLine;
    }

    @Test
    void bindsOptionsPositionalsAndConvertsValues() {
        var options = new Options();

        parse(options, "--url", "u", "-t", "10", "--flag", "-H", "a", "-H", "b", "-X", "h:81", "select 1");

        assertThat(options.expression).isEqualTo("select 1");
        assertThat(options.timeout).isEqualTo(10);
        assertThat(options.flag).isTrue();
        assertThat(options.headers).containsExactly("a", "b");
        assertThat(options.proxy).isEqualTo(new InetSocketAddress("h", 81));
        assertThat(options.method).isEqualTo("GET");
        assertThat(options.content).isNull();
    }

    @Test
    void keepsFieldInitialValuesAndShowsThemInUsage() {
        var options = new Options();

        var commandLine = parse(options, "--url", "u", "x");

        assertThat(options.timeout).isEqualTo(5000);
        var usage = commandLine.getUsageMessage();
        assertThat(usage).contains("timeout (default: 5000)").contains("test sampler");
    }

    @Test
    void requiredOptionIsEnforced() {
        assertThatThrownBy(() -> parse(new Options(), "x")).isInstanceOf(CommandLine.MissingParameterException.class);
    }

    @Test
    void groupObjectIsCreatedOnlyWhenAnOptionIsGivenAndIsExclusive() {
        var options = new Options();
        parse(options, "--url", "u", "-p", "f", "x");
        assertThat(options.content.post).isEqualTo(Path.of("f"));
        assertThat(options.content.put).isNull();

        assertThatThrownBy(() -> parse(new Options(), "--url", "u", "-p", "f", "-u", "g", "x"))
                .isInstanceOf(CommandLine.MutuallyExclusiveArgsException.class);
    }

    @Test
    void exposesCurrentValuesThroughTheSpec() {
        var options = new Options();
        var commandLine = parse(options, "--url", "u", "-p", "f", "x");

        var values = commandLine.getCommandSpec().options().stream()
                .filter(o -> o.longestName().equals("--timeout") || o.longestName().equals("-p"))
                .map(o -> o.longestName() + "=" + o.getValue())
                .toList();

        assertThat(values).containsExactlyInAnyOrder("--timeout=5000", "-p=f");
    }
}
