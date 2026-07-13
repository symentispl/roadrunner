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
package io.roadrunner.samplers.spi.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a sampler operation expression: a method name followed by zero or more quoted string
 * literals, e.g. {@code query("SELECT * FROM table")} or {@code post("url", "body")}.
 *
 * <p>Grammar: {@code expression := name '(' ( stringLiteral (',' stringLiteral)* )? ')'}
 *
 * <p>This is a parsing detail of {@link io.roadrunner.samplers.spi.SamplerExtensionPoint} — the
 * containing package is not exported by this module, so no other module can reference this type.
 */
public record SamplerExpression(String methodName, List<String> arguments) {

    public SamplerExpression {
        arguments = List.copyOf(arguments);
    }

    public static SamplerExpression parse(String input) {
        char[] chars = input.toCharArray();
        int pos = 0;

        int nameStart = pos;
        while (pos < chars.length && Character.isLetterOrDigit(chars[pos])) {
            pos++;
        }
        if (pos == nameStart) {
            throw new IllegalArgumentException("Expected method name at position %d in '%s'".formatted(pos, input));
        }
        String methodName = input.substring(nameStart, pos);

        if (pos >= chars.length || chars[pos] != '(') {
            throw new IllegalArgumentException("Expected '(' at position %d in '%s'".formatted(pos, input));
        }
        pos++;
        pos = skipWhitespace(pos, chars);

        List<String> arguments = new ArrayList<>();
        if (pos < chars.length && chars[pos] == ')') {
            pos++;
            requireExhausted(pos, chars, input);
            return new SamplerExpression(methodName, arguments);
        }

        while (true) {
            if (pos >= chars.length || chars[pos] != '"') {
                throw new IllegalArgumentException("Expected '\"' at position %d in '%s'".formatted(pos, input));
            }
            pos++;

            StringBuilder literal = new StringBuilder();
            while (true) {
                if (pos >= chars.length) {
                    throw new IllegalArgumentException("Unterminated string literal in '%s'".formatted(input));
                }
                char ch = chars[pos];
                if (ch == '\\') {
                    pos++;
                    if (pos >= chars.length) {
                        throw new IllegalArgumentException("Unterminated escape sequence in '%s'".formatted(input));
                    }
                    char escaped = chars[pos];
                    if (escaped != '"' && escaped != '\\') {
                        throw new IllegalArgumentException(
                                "Invalid escape sequence '\\%s' in '%s'".formatted(escaped, input));
                    }
                    literal.append(escaped);
                    pos++;
                } else if (ch == '"') {
                    pos++;
                    break;
                } else {
                    literal.append(ch);
                    pos++;
                }
            }
            arguments.add(literal.toString());
            pos = skipWhitespace(pos, chars);

            if (pos >= chars.length) {
                throw new IllegalArgumentException("Expected ',' or ')' at position %d in '%s'".formatted(pos, input));
            }
            if (chars[pos] == ')') {
                pos++;
                break;
            }
            if (chars[pos] != ',') {
                throw new IllegalArgumentException("Expected ',' or ')' at position %d in '%s'".formatted(pos, input));
            }
            pos++;
            pos = skipWhitespace(pos, chars);
        }

        requireExhausted(pos, chars, input);
        return new SamplerExpression(methodName, arguments);
    }

    private static int skipWhitespace(int pos, char[] chars) {
        while (pos < chars.length && Character.isWhitespace(chars[pos])) {
            pos++;
        }
        return pos;
    }

    private static void requireExhausted(int pos, char[] chars, String input) {
        if (pos != chars.length) {
            throw new IllegalArgumentException(
                    "Unexpected trailing characters at position %d in '%s'".formatted(pos, input));
        }
    }
}
