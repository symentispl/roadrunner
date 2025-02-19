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

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;

public class OptionsBinding<T> {

    private final Class<T> clazz;
    private final Constructor<T> constructor;
    private final Map<String, ParameterBinding> bindings;
    private final Options options;
    private String[] args;

    OptionsBinding(
            Class<T> clazz, Constructor<T> constructor, Map<String, ParameterBinding> bindings, Options options) {
        this.clazz = clazz;
        this.constructor = constructor;
        this.bindings = bindings;
        this.options = options;
    }

    public T newInstance(String[] args) throws Exception {
        var options = options();

        var parser = new DefaultParser();
        var cmd = parser.parse(options, args, true);

        var constructorArgs = new Object[constructor.getParameterCount()];
        var parameterNames = Arrays.stream(constructor.getParameters())
                .map(Parameter::getName)
                .toArray(String[]::new);

        for (int i = 0; i < parameterNames.length; i++) {
            var parameterBinding = bindings.get(parameterNames[i]);
            constructorArgs[i] = parameterBinding.bind(cmd);
        }

        this.args = cmd.getArgs();
        return constructor.newInstance(constructorArgs);
    }

    public String[] args() {
        return args;
    }

    Options options() {
        return options;
    }
}
