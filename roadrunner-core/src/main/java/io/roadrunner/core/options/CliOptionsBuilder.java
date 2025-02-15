/**
 *   Copyright 2024 Symentis.pl
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.roadrunner.core.options;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.Option;

public class CliOptionsBuilder {
    private static final String DEFAULT_DESCRIPTION = "";

    public <T> OptionsBinding<T> build(Class<T> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            Parameter[] parameters = constructor.getParameters();
            Map<String, Option> bindings = new HashMap<>();
            for (Parameter param : parameters) {
                CliOption annotation = param.getAnnotation(CliOption.class);
                if (annotation != null) {
                    Option.Builder optBuilder = Option.builder();
                    if (isNotEmpty(annotation.opt())) {
                        optBuilder.option(annotation.opt());
                    }
                    if (isNotEmpty(annotation.longOpt())) {
                        optBuilder.longOpt(annotation.longOpt());
                    }
                    optBuilder
                            .desc(annotation.description().isEmpty() ? DEFAULT_DESCRIPTION : annotation.description())
                            .required(annotation.required())
                            .type(param.getType());
                    if (annotation.hasArg()) {
                        optBuilder.hasArg();
                    }
                    // binds constructor parameter by name to option
                    bindings.put(param.getName(), optBuilder.build());
                }
            }
            return new OptionsBinding(clazz, constructor, bindings);
        }

        throw new IllegalArgumentException("Class %s doesn't have public constructor".formatted(clazz));
    }
}
