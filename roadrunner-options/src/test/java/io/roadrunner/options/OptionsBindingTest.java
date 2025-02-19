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
package io.roadrunner.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import org.junit.jupiter.api.Test;

class OptionsBindingTest {

    static class OptionsForClassWithStringParameter {
        final String param;

        public OptionsForClassWithStringParameter(
                @CliOption(longOpt = "test-param", description = "Test parameter", required = true, hasArg = true)
                        String param) {
            this.param = param;
        }
    }

    static class OptionsForClassWithIntParameter {
        final Integer param;

        public OptionsForClassWithIntParameter(
                @CliOption(longOpt = "test-param", description = "Test parameter", required = true, hasArg = true)
                        Integer param) {
            this.param = param;
        }
    }

    static class OptionsForClassWithStringArg {
        final String param;
        final String arg;

        public OptionsForClassWithStringArg(
                @CliOption(longOpt = "test-param", description = "Test parameter", required = true, hasArg = true)
                        String param,
                @CliArg String arg) {
            this.param = param;
            this.arg = arg;
        }
    }

    @Test
    void optionsForClassWithIntParameter() throws Exception {
        // given
        var cliOptionsBuilder = new CliOptionsBuilder();
        var binding = cliOptionsBuilder.build(OptionsForClassWithIntParameter.class);
        var args = new String[] {"--test-param", "1"};
        // when
        var instance = binding.newInstance(args);

        // then
        assertThat(instance)
                .asInstanceOf(type(OptionsForClassWithIntParameter.class))
                .satisfies(p -> {
                    assertThat(p.param).isEqualTo(1);
                });
    }

    @Test
    void optionsForClassWithStringParameter() throws Exception {
        // given
        var cliOptionsBuilder = new CliOptionsBuilder();
        var binding = cliOptionsBuilder.build(OptionsForClassWithStringParameter.class);
        var args = new String[] {"--test-param", "1"};
        // when
        var instance = binding.newInstance(args);

        // then
        assertThat(instance)
                .asInstanceOf(type(OptionsForClassWithStringParameter.class))
                .satisfies(p -> {
                    assertThat(p.param).isEqualTo("1");
                });
    }

    @Test
    void optionsForClassWithStringArg() throws Exception {
        // given
        var cliOptionsBuilder = new CliOptionsBuilder();
        var binding = cliOptionsBuilder.build(OptionsForClassWithStringArg.class);
        var args = new String[] {"--test-param", "1", "argument"};
        // when
        var instance = binding.newInstance(args);

        // then
        assertThat(instance)
                .asInstanceOf(type(OptionsForClassWithStringArg.class))
                .satisfies(p -> {
                    assertThat(p.param).isEqualTo("1");
                    assertThat(p.arg).isEqualTo("argument");
                });
    }
}
