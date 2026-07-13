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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SamplerExpressionTest {

    static Stream<Arguments> validExpressions() {
        return Stream.of(
                Arguments.of("query(\"SELECT 1\")", "query", List.of("SELECT 1")),
                Arguments.of("noArgs()", "noArgs", List.of()),
                Arguments.of("post(\"url\", \"body\")", "post", List.of("url", "body")),
                Arguments.of("query(\"say \\\"hi\\\"\")", "query", List.of("say \"hi\"")));
    }

    @ParameterizedTest
    @MethodSource("validExpressions")
    void parsesValidExpressions(String input, String expectedMethodName, List<String> expectedArguments) {
        var expression = SamplerExpression.parse(input);

        assertThat(expression.methodName()).isEqualTo(expectedMethodName);
        assertThat(expression.arguments()).isEqualTo(expectedArguments);
    }

    @Test
    void missingMethodNameThrows() {
        assertThatThrownBy(() -> SamplerExpression.parse("(\"x\")"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected method name");
    }

    @Test
    void missingOpenParenThrows() {
        assertThatThrownBy(() -> SamplerExpression.parse("query \"x\")"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected '('");
    }

    @Test
    void unterminatedStringThrows() {
        assertThatThrownBy(() -> SamplerExpression.parse("query(\"unterminated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unterminated string literal");
    }

    @Test
    void missingClosingParenThrows() {
        assertThatThrownBy(() -> SamplerExpression.parse("query(\"a\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected ',' or ')'");
    }

    @Test
    void trailingCharactersThrow() {
        assertThatThrownBy(() -> SamplerExpression.parse("query(\"a\")trailing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected trailing characters");
    }
}
