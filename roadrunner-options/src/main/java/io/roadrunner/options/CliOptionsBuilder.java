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

import static io.roadrunner.options.ParameterBinding.forArg;
import static io.roadrunner.options.ParameterBinding.forOption;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.Converter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class CliOptionsBuilder {
    private static final String DEFAULT_DESCRIPTION = "";

    public <T> OptionsBinding<T> build(Class<T> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            Parameter[] parameters = constructor.getParameters();
            Map<String, ParameterBinding> bindings = new HashMap<>();
            Options options = new Options();
            for (Parameter param : parameters) {
                CliOption cliOption = param.getAnnotation(CliOption.class);
                if (cliOption != null) {
                    Option.Builder optBuilder = Option.builder();
                    if (isNotEmpty(cliOption.opt())) {
                        optBuilder.option(cliOption.opt());
                    }
                    if (isNotEmpty(cliOption.longOpt())) {
                        optBuilder.longOpt(cliOption.longOpt());
                    }
                    optBuilder
                            .desc(cliOption.description().isEmpty() ? DEFAULT_DESCRIPTION : cliOption.description())
                            .required(cliOption.required())
                            .type(param.getType());
                    if (param.getType() == Duration.class) {
                        optBuilder.converter(
                                (Converter<Duration, Throwable>) string -> Duration.ofMillis(Long.parseLong(string)));
                    }
                    if (cliOption.hasArg()) {
                        optBuilder.hasArg();
                    }
                    // binds constructor parameter by name to option
                    var option = optBuilder.build();
                    bindings.put(param.getName(), forOption(option));
                    options.addOption(option);
                    continue;
                }

                CliArg cliArg = param.getAnnotation(CliArg.class);
                if (cliArg != null) {
                    bindings.put(param.getName(), forArg());
                }
            }
            return new OptionsBinding(clazz, constructor, bindings, options);
        }

        throw new IllegalArgumentException("Class %s doesn't have public constructor".formatted(clazz));
    }
}
