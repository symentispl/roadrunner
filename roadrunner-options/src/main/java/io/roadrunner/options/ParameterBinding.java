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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;

@FunctionalInterface
interface ParameterBinding {

    class OptionParameterBinding implements ParameterBinding {

        private final Option option;

        OptionParameterBinding(Option option) {
            this.option = option;
        }

        @Override
        public Object bind(CommandLine commandLine) throws ParseException {
            return commandLine.getParsedOptionValue(option);
        }
    }

    class ArgParameterBinding implements ParameterBinding {

        @Override
        public Object bind(CommandLine commandLine) throws ParseException {
            return commandLine.getArgs()[0];
        }
    }

    static ParameterBinding forOption(Option option) {
        return new OptionParameterBinding(option);
    }

    static ParameterBinding forArg() {
        return new ArgParameterBinding();
    }

    Object bind(CommandLine commandLine) throws ParseException;
}
