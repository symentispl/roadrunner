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

import org.junit.jupiter.api.Test;

class CliOptionsBuilderTest {

    class OptionsForClassWithOneConstructorParameter {
        public OptionsForClassWithOneConstructorParameter(
                @CliOption(longOpt = "test-param", description = "Test parameter", required = true, hasArg = true)
                        String param) {}
    }

    @Test
    void shouldFromOptionsForClassWithOneConstructorParameter() {
        // given
        var builder = new CliOptionsBuilder();

        // when
        var optionsBinding = builder.from(OptionsForClassWithOneConstructorParameter.class);
        var options = optionsBinding.options();
        // then
        assertThat(options.getOptions()).hasSize(1);
        assertThat(options.getOption("test-param")).isNotNull().satisfies(opt -> {
            assertThat(opt.getLongOpt()).isEqualTo("test-param");
            assertThat(opt.getDescription()).isEqualTo("Test parameter");
            assertThat(opt.isRequired()).isTrue();
            assertThat(opt.hasArg()).isTrue();
        });
    }
}
