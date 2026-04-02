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

import picocli.CommandLine;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

record PrefixedMap(String prefix, Map<String, String> parameters) {
    static PrefixedMap parse(String input) throws IOException {
        State state;
        StringBuilder buffer;
        String type;
        String currentKey;
        Map<String, String> parameters;
        try (var pushbackReader = new PushbackReader(new StringReader(input))) {
            int ch;
            state = State.prefix;
            buffer = new StringBuilder();
            type = null;
            currentKey = null;
            parameters = new HashMap<>();
            while ((ch = pushbackReader.read()) != -1) {
                switch (state) {
                    case prefix: {
                        if (Character.isAlphabetic(ch)) {
                            buffer.append((char) ch);
                        } else if (ch == ':') {
                            type = buffer.toString();
                            buffer.setLength(0);
                            state = State.key;
                        } else {
                            throw new IllegalStateException(
                                    "Invalid character in prefix state: '%s'".formatted((char) ch));
                        }
                        break;
                    }
                    case key: {
                        if (Character.isLetterOrDigit(ch)) {
                            buffer.append((char) ch);
                        } else if (ch == '=') {
                            currentKey = buffer.toString();
                            buffer.setLength(0);
                            state = State.value;
                        } else {
                            throw new IllegalStateException(
                                    "Invalid character in key state: '%s'".formatted((char) ch));
                        }
                        break;
                    }
                    case value: {
                        if (ch == '\\') {
                            int escaped = pushbackReader.read();
                            if (escaped == '"' || escaped == '\\' || escaped == ',' || escaped == '=') {
                                buffer.append((char) escaped);
                            } else if (escaped != -1) {
                                throw new IllegalStateException(
                                        "Invalid escape sequence: '%s'".formatted((char) escaped));
                            }
                        } else if (ch == ',') {
                            parameters.put(currentKey, buffer.toString());
                            buffer.setLength(0);
                            currentKey = null;
                            state = State.key;
                        } else {
                            buffer.append((char) ch);
                        }
                        break;
                    }
                }
            }
        }
        if (state == State.value && currentKey != null) {
            parameters.put(currentKey, buffer.toString());
        }
        return new PrefixedMap(type, parameters);
    }

    private enum State {
        key,
        value,
        prefix
    }

    public static class Converter implements CommandLine.ITypeConverter<PrefixedMap> {
        @Override
        public PrefixedMap convert(String s) throws Exception {
            return PrefixedMap.parse(s);
        }
    }
}
