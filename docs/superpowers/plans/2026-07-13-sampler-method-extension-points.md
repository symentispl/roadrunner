# Sampler Method Extension Points Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a sampler class expose multiple named, type-checked operations (instead of one fixed `execute()`), selected and literal-bound from a small CLI expression (`query("SELECT ...")`), while every existing sampler and downstream engine code keeps working unchanged.

**Architecture:** Two new types in `roadrunner-samplers-spi`: `SamplerExpression` (hand-rolled parser: `name("lit", ...)` → method name + literal args), which lives in an **unexported** internal package since it's a parsing detail no other module should ever reference directly; and `SamplerExtensionPoint` (the module's only exported entry point for this mechanism — validates a target object's `Sampler`-returning methods, parses the expression internally, binds literal args via `MethodHandles.publicLookup()`, returns a `Supplier<Sampler>`). JDBC and Neo4j samplers migrate onto this: their per-call logic moves into a new `JDBCSampler`/`Neo4jSampler` "methods class" exposing `query(String sql)`; their `SamplerProvider`s become thin wrappers delegating to the bound `Supplier<Sampler>`. AB and VM are untouched, proving the old direct-`Sampler`-implementation style still works.

**Tech Stack:** Java 25, Maven (multi-module, JPMS), JUnit 5 + AssertJ, JMH (`roadrunner-microbenchmarks`), `java.lang.invoke.MethodHandle`s (JDK stdlib, no new dependency).

**Spec:** `docs/superpowers/specs/2026-07-13-sampler-method-extension-points-design.md`

## Global Constraints

- No new Maven module. Both new types live in `roadrunner-samplers-spi`.
- No new external dependency (no parser generator, no extra reflection library).
- Literal CLI arguments support only `String` in this change — no numeric/`URL`/file-content literals.
- Binding uses `MethodHandles.publicLookup()`, never `MethodHandles.lookup()` — `roadrunner-samplers-spi` must not gain a `requires` edge onto sampler modules.
- `SamplerExtensionPoint` validation only inspects methods whose return type is exactly `Sampler`; methods with any other return type are ignored, not rejected.
- `SamplerExpression` lives in `io.roadrunner.samplers.spi.internal`, which is **not** listed in `roadrunner-samplers-spi`'s `module-info.java` `exports` — no other module may import it. `SamplerExtensionPoint` is the only public entry point (`bind(Object, String)`); it never exposes the parsed IR type in its signature.
- AB and VM samplers are not touched by this plan.
- Every new/modified `.java` file gets the existing Apache-2.0 header (copy verbatim from any existing file in the same module) and must pass `./mvnw spotless:apply -pl <module>` (Palantir Java format) before committing.
- Use `./mvnw` (wrapper) for all build/test commands below, run from the repo root.

---

### Task 1: `SamplerExpression` parser (internal)

**Files:**
- Modify: `roadrunner-samplers-spi/pom.xml`
- Create: `roadrunner-samplers-spi/src/main/java/io/roadrunner/samplers/spi/internal/SamplerExpression.java`
- Test: `roadrunner-samplers-spi/src/test/java/io/roadrunner/samplers/spi/internal/SamplerExpressionTest.java`

**Interfaces:**
- Produces: `record SamplerExpression(String methodName, List<String> arguments)` with `static SamplerExpression parse(String input)`, throwing `IllegalArgumentException` on malformed input. Package `io.roadrunner.samplers.spi.internal` (not exported by `roadrunner-samplers-spi`'s `module-info.java` — no edit needed there, a package absent from `exports` is unexported by default). Consumed only by `SamplerExtensionPoint` (Task 2), which lives in the sibling `io.roadrunner.samplers.spi` package in the *same* module — intra-module access doesn't need `exports`.

- [ ] **Step 1: Add test dependencies to `roadrunner-samplers-spi/pom.xml`**

No test source set exists yet in this module. Add the same test dependencies every sibling module uses (versions come from the parent's `dependencyManagement` — do not add explicit `<version>`):

```xml
<dependencies>
    <dependency>
        <groupId>io.roadrunner</groupId>
        <artifactId>roadrunner-api</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 2: Write the failing test**

Create `roadrunner-samplers-spi/src/test/java/io/roadrunner/samplers/spi/internal/SamplerExpressionTest.java`:

```java
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
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./mvnw -pl roadrunner-samplers-spi -am test`
Expected: COMPILE ERROR — `SamplerExpression` does not exist.

- [ ] **Step 4: Write the implementation**

Create `roadrunner-samplers-spi/src/main/java/io/roadrunner/samplers/spi/internal/SamplerExpression.java`:

```java
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
                throw new IllegalArgumentException(
                        "Expected ',' or ')' at position %d in '%s'".formatted(pos, input));
            }
            if (chars[pos] == ')') {
                pos++;
                break;
            }
            if (chars[pos] != ',') {
                throw new IllegalArgumentException(
                        "Expected ',' or ')' at position %d in '%s'".formatted(pos, input));
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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw -pl roadrunner-samplers-spi -am test`
Expected: PASS (all `SamplerExpressionTest` cases green).

- [ ] **Step 6: Format and commit**

```bash
./mvnw spotless:apply -pl roadrunner-samplers-spi
git add roadrunner-samplers-spi/pom.xml \
        roadrunner-samplers-spi/src/main/java/io/roadrunner/samplers/spi/internal/SamplerExpression.java \
        roadrunner-samplers-spi/src/test/java/io/roadrunner/samplers/spi/internal/SamplerExpressionTest.java
git commit -m "Add internal SamplerExpression parser for sampler operation CLI syntax"
```

---

### Task 2: `SamplerExtensionPoint` binder

**Files:**
- Create: `roadrunner-samplers-spi/src/main/java/io/roadrunner/samplers/spi/SamplerExtensionPoint.java`
- Test: `roadrunner-samplers-spi/src/test/java/io/roadrunner/samplers/spi/SamplerExtensionPointTest.java`

**Interfaces:**
- Consumes: `io.roadrunner.samplers.spi.internal.SamplerExpression` (Task 1, same module — `SamplerExtensionPoint` is the only caller anywhere). `io.roadrunner.api.samplers.Sampler` (existing, single abstract method `execute(SamplerParameters)`). `io.roadrunner.samplers.spi.PluginInitializationException` (existing, `roadrunner-samplers-spi`, constructor `(String message, Throwable cause)`).
- Produces: `final class SamplerExtensionPoint` with `static Supplier<Sampler> bind(Object target, String expressionText)` — the module's only exported entry point for this mechanism; the parsed IR type never appears in this signature. Consumed by `JDBCSamplerProvider`/`Neo4jSamplerProvider` in Tasks 3–4.

- [ ] **Step 1: Write the failing test**

Create `roadrunner-samplers-spi/src/test/java/io/roadrunner/samplers/spi/SamplerExtensionPointTest.java`. Note this test never imports `SamplerExpression` — it exercises `bind` exactly the way `JDBCSamplerProvider`/`Neo4jSamplerProvider` will, with a raw string:

```java
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
package io.roadrunner.samplers.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.api.samplers.Sampler;
import org.junit.jupiter.api.Test;

class SamplerExtensionPointTest {

    public static class QueryFixture {
        private String lastSql;

        public Sampler query(String sql) {
            this.lastSql = sql;
            return parameters -> SamplerResponse.empty(0, 0);
        }

        public Sampler noArgs() {
            return parameters -> SamplerResponse.empty(0, 0);
        }

        public String lastSql() {
            return lastSql;
        }
    }

    public static class NonStringParameterFixture {
        public Sampler withInt(int notAString) {
            return parameters -> SamplerResponse.empty(0, 0);
        }
    }

    @Test
    void bindsSingleArgumentMethodAndPassesTheLiteral() {
        var fixture = new QueryFixture();

        var sampler = SamplerExtensionPoint.bind(fixture, "query(\"SELECT 1\")").get();
        var response = sampler.execute(SamplerParameters.NONE);

        assertThat(fixture.lastSql()).isEqualTo("SELECT 1");
        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
    }

    @Test
    void bindsZeroArgumentMethodAmongMultipleCandidates() {
        var fixture = new QueryFixture();

        var sampler = SamplerExtensionPoint.bind(fixture, "noArgs()").get();
        var response = sampler.execute(SamplerParameters.NONE);

        assertThat(response).isInstanceOf(SamplerResponse.Response.class);
    }

    @Test
    void eachSupplierInvocationProducesAFreshSampler() {
        var samplerSupplier = SamplerExtensionPoint.bind(new QueryFixture(), "query(\"SELECT 1\")");

        assertThat(samplerSupplier.get()).isNotSameAs(samplerSupplier.get());
    }

    @Test
    void malformedExpressionThrows() {
        assertThatThrownBy(() -> SamplerExtensionPoint.bind(new QueryFixture(), "query(\"unterminated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unterminated string literal");
    }

    @Test
    void unknownMethodNameThrows() {
        assertThatThrownBy(() -> SamplerExtensionPoint.bind(new QueryFixture(), "update(\"SELECT 1\")"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("update");
    }

    @Test
    void arityMismatchThrows() {
        assertThatThrownBy(() -> SamplerExtensionPoint.bind(new QueryFixture(), "query(\"a\", \"b\")"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }

    @Test
    void nonStringParameterThrows() {
        assertThatThrownBy(() -> SamplerExtensionPoint.bind(new NonStringParameterFixture(), "withInt(\"1\")"))
                .isInstanceOf(PluginInitializationException.class)
                .hasMessageContaining("withInt");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -pl roadrunner-samplers-spi -am test`
Expected: COMPILE ERROR — `SamplerExtensionPoint` does not exist.

- [ ] **Step 3: Write the implementation**

Create `roadrunner-samplers-spi/src/main/java/io/roadrunner/samplers/spi/SamplerExtensionPoint.java`:

```java
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
package io.roadrunner.samplers.spi;

import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.samplers.spi.internal.SamplerExpression;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Binds a sampler operation expression (e.g. {@code query("SELECT 1")}) to a matching
 * {@code Sampler}-returning method on a target object, producing a factory for the resulting
 * {@link Sampler}. This is the module's only exported entry point for the mechanism — callers
 * pass the raw expression text and never see the parsed intermediate representation.
 *
 * <p>Uses {@link MethodHandles#publicLookup()} rather than {@link MethodHandles#lookup()}: the
 * target class typically lives in a different Maven/JPMS module than this class, and this module
 * must not gain a {@code requires} edge back onto every sampler module that uses it.
 */
public final class SamplerExtensionPoint {

    private SamplerExtensionPoint() {}

    public static Supplier<Sampler> bind(Object target, String expressionText) {
        SamplerExpression expression = SamplerExpression.parse(expressionText);

        List<Method> candidates = candidateMethods(target.getClass());

        List<Method> matching = candidates.stream()
                .filter(method -> method.getName().equals(expression.methodName()))
                .filter(method -> method.getParameterCount() == expression.arguments().size())
                .toList();

        if (matching.isEmpty()) {
            throw new IllegalArgumentException(
                    "No Sampler-returning method named '%s' with %d argument(s) on %s. Available: %s"
                            .formatted(
                                    expression.methodName(),
                                    expression.arguments().size(),
                                    target.getClass().getName(),
                                    describe(candidates)));
        }

        Method method = matching.get(0);

        MethodHandle handle;
        try {
            handle = MethodHandles.publicLookup().unreflect(method);
        } catch (IllegalAccessException e) {
            throw new PluginInitializationException(
                    "Cannot access extension point method '%s' on %s"
                            .formatted(method.getName(), target.getClass().getName()),
                    e);
        }

        Object[] boundArguments = prependTarget(target, expression.arguments());
        MethodHandle boundHandle = MethodHandles.insertArguments(handle, 0, boundArguments);

        return () -> {
            try {
                return (Sampler) boundHandle.invoke();
            } catch (Throwable t) {
                throw new IllegalStateException(
                        "Failed to invoke extension point method '%s' on %s"
                                .formatted(method.getName(), target.getClass().getName()),
                        t);
            }
        };
    }

    private static List<Method> candidateMethods(Class<?> type) {
        List<Method> candidates = new ArrayList<>();
        for (Method method : type.getMethods()) {
            if (method.getReturnType() != Sampler.class) {
                continue;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (parameterType != String.class) {
                    throw new PluginInitializationException(
                            "Extension point method '%s' on %s has non-String parameter of type %s"
                                    .formatted(method.getName(), type.getName(), parameterType.getName()),
                            null);
                }
            }
            candidates.add(method);
        }
        return candidates;
    }

    private static Object[] prependTarget(Object target, List<String> arguments) {
        Object[] withTarget = new Object[arguments.size() + 1];
        withTarget[0] = target;
        for (int i = 0; i < arguments.size(); i++) {
            withTarget[i + 1] = arguments.get(i);
        }
        return withTarget;
    }

    private static String describe(List<Method> candidates) {
        return candidates.stream()
                .map(m -> "%s(%d args)".formatted(m.getName(), m.getParameterCount()))
                .collect(Collectors.joining(", "));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -pl roadrunner-samplers-spi -am test`
Expected: PASS (all `SamplerExtensionPointTest` cases green).

- [ ] **Step 5: Format and commit**

```bash
./mvnw spotless:apply -pl roadrunner-samplers-spi
git add roadrunner-samplers-spi/src/main/java/io/roadrunner/samplers/spi/SamplerExtensionPoint.java \
        roadrunner-samplers-spi/src/test/java/io/roadrunner/samplers/spi/SamplerExtensionPointTest.java
git commit -m "Add SamplerExtensionPoint: MethodHandle-bind an expression to a Sampler factory"
```

---

### Task 3: Migrate the JDBC sampler

**Files:**
- Create: `roadrunner-sampler-jdbc/src/main/java/io/roadrunner/samplers/jdbc/JDBCSampler.java`
- Modify: `roadrunner-sampler-jdbc/src/main/java/io/roadrunner/samplers/jdbc/JDBCSamplerProvider.java`
- Modify: `roadrunner-sampler-jdbc/src/it/java/io/roadrunner/samplers/jdbc/tests/JDBCSamplerProviderIT.java`

**Interfaces:**
- Consumes: `SamplerExtensionPoint.bind(Object, String)` (Task 2).
- Produces: `JDBCSampler` with `Sampler query(String sql)`, `long sampleCount()`, `long totalAcquireNanos()`, `long totalQueryNanos()`, `Connection getConnection()`. `JDBCSamplerProvider` keeps its existing public surface — `newSampler()`, `sampleCount()`, `totalAcquireNanos()`, `totalQueryNanos()`, `close()`, `getConnection()`, and its existing `(DataSource, String)` constructor, whose `String` now always means a full expression (`query("...")`) rather than bare SQL.

**No changes to `JDBCSamplerPlugin.java`.** Its `newSamplerProvider` method already calls `new JDBCSamplerProvider(dataSource, options.query)` — that line is untouched; only what the `String` means changes (bare SQL → expression), which is why the IT test needs updating (next steps) but the plugin code doesn't.

No new module-info changes: `roadrunner-sampler-jdbc`'s `module-info.java` already has `requires io.roadrunner.samplers.spi;` and `exports io.roadrunner.samplers.jdbc;` (unconditional), which is everything `MethodHandles.publicLookup()` needs. It has no dependency on the new internal package, and doesn't need one — it only ever calls `SamplerExtensionPoint.bind(Object, String)`.

- [ ] **Step 1: Extract `JDBCSampler` — move the existing lambda body out of `JDBCSamplerProvider`**

Create `roadrunner-sampler-jdbc/src/main/java/io/roadrunner/samplers/jdbc/JDBCSampler.java` (this is today's `JDBCSamplerProvider` body, moved verbatim into a method-shaped form):

```java
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
package io.roadrunner.samplers.jdbc;

import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.api.samplers.Sampler;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.SQLType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import javax.sql.DataSource;

/**
 * Extension-point methods class for the JDBC sampler: {@link #query(String)} is bound from a CLI
 * {@code query("SELECT ...")} expression via {@link io.roadrunner.samplers.spi.SamplerExtensionPoint}.
 */
public class JDBCSampler {

    private final DataSource dataSource;
    private final LongAdder sampleCount = new LongAdder();
    private final LongAdder acquireNanos = new LongAdder();
    private final LongAdder queryNanos = new LongAdder();

    public JDBCSampler(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Sampler query(String sql) {
        return (SamplerParameters parameters) -> {
            var tStarted = System.nanoTime();
            try (var cnn = dataSource.getConnection();
                    var stmt = cnn.prepareStatement(sql)) {
                var tAcquired = System.nanoTime();
                try {
                    long rowCount;
                    parameters.forEach((index, type, value) -> stmt.setObject(index + 1, value, sqlTypeOf(type)));
                    boolean hasResultSet = stmt.execute();
                    if (hasResultSet) {
                        try (var rs = stmt.getResultSet()) {
                            rowCount = 0;
                            while (rs.next()) {
                                rowCount++;
                            }
                        }
                    } else {
                        rowCount = stmt.getUpdateCount();
                    }
                    var tDone = System.nanoTime();
                    recordTimestamps(tStarted, tAcquired, tDone);
                    return SamplerResponse.response(tStarted, tDone, rowCount);
                } catch (Exception e) {
                    var tDone = System.nanoTime();
                    recordTimestamps(tStarted, tAcquired, tDone);
                    return SamplerResponse.error(tStarted, tDone, e.getMessage());
                }
            } catch (SQLException e) {
                var tDone = System.nanoTime();
                // Connection acquisition failed: entire window is acquire time.
                recordTimestamps(tStarted, tDone, tDone);
                return SamplerResponse.error(tStarted, tDone, e.getMessage());
            }
        };
    }

    /**
     * Java to SQL type mapping per the JDBC 4.3 spec (table B-4 / appendix B). Only the exact
     * runtime classes a {@link io.roadrunner.api.parameters.ParameterSource} can produce are
     * listed — subtypes (e.g. {@code java.util.Date}, {@code Number}) and primitive class
     * literals (unreachable since values arrive via {@link Object#getClass()}) are out of scope.
     */
    private static final Map<Class<?>, SQLType> SQL_TYPE_BY_JAVA_TYPE = Map.ofEntries(
            entry(String.class, JDBCType.VARCHAR),
            entry(Character.class, JDBCType.CHAR),
            entry(Boolean.class, JDBCType.BOOLEAN),
            entry(Byte.class, JDBCType.TINYINT),
            entry(Short.class, JDBCType.SMALLINT),
            entry(Integer.class, JDBCType.INTEGER),
            entry(Long.class, JDBCType.BIGINT),
            entry(Float.class, JDBCType.REAL),
            entry(Double.class, JDBCType.DOUBLE),
            entry(BigDecimal.class, JDBCType.DECIMAL),
            entry(BigInteger.class, JDBCType.NUMERIC),
            entry(byte[].class, JDBCType.VARBINARY),
            entry(java.sql.Date.class, JDBCType.DATE),
            entry(java.sql.Time.class, JDBCType.TIME),
            entry(java.sql.Timestamp.class, JDBCType.TIMESTAMP),
            entry(LocalDate.class, JDBCType.DATE),
            entry(LocalTime.class, JDBCType.TIME),
            entry(LocalDateTime.class, JDBCType.TIMESTAMP),
            entry(OffsetTime.class, JDBCType.TIME_WITH_TIMEZONE),
            entry(OffsetDateTime.class, JDBCType.TIMESTAMP_WITH_TIMEZONE));

    private static SQLType sqlTypeOf(Class<?> type) {
        return requireNonNull(
                SQL_TYPE_BY_JAVA_TYPE.get(type),
                () -> "unsupported Java type for JDBC parameter binding: " + type.getName());
    }

    private void recordTimestamps(long tStarted, long tAcquired, long tDone) {
        sampleCount.increment();
        acquireNanos.add(tAcquired - tStarted);
        queryNanos.add(tDone - tAcquired);
    }

    public long sampleCount() {
        return sampleCount.sum();
    }

    public long totalAcquireNanos() {
        return acquireNanos.sum();
    }

    public long totalQueryNanos() {
        return queryNanos.sum();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
```

- [ ] **Step 2: Rewrite `JDBCSamplerProvider` as a thin wrapper**

Replace the full contents of `roadrunner-sampler-jdbc/src/main/java/io/roadrunner/samplers/jdbc/JDBCSamplerProvider.java`:

```java
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
package io.roadrunner.samplers.jdbc;

import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.samplers.spi.SamplerExtensionPoint;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;

public class JDBCSamplerProvider implements SamplerProvider {

    private final JDBCSampler jdbcSampler;
    private final Supplier<Sampler> samplerSupplier;

    public JDBCSamplerProvider(DataSource dataSource, String expressionText) {
        this.jdbcSampler = new JDBCSampler(dataSource);
        this.samplerSupplier = SamplerExtensionPoint.bind(jdbcSampler, expressionText);
    }

    @Override
    public Sampler newSampler() {
        return samplerSupplier.get();
    }

    public long sampleCount() {
        return jdbcSampler.sampleCount();
    }

    public long totalAcquireNanos() {
        return jdbcSampler.totalAcquireNanos();
    }

    public long totalQueryNanos() {
        return jdbcSampler.totalQueryNanos();
    }

    @Override
    public void close() {
        // Pool lifecycle is owned by JDBCSamplerPlugin.
    }

    public Connection getConnection() throws SQLException {
        return jdbcSampler.getConnection();
    }
}
```

The constructor's `String` parameter now always means a full expression (e.g. `query("SELECT 1")`), never bare SQL — every caller, including the direct-construction IT test (next step), passes an expression.

- [ ] **Step 3: Update the JDBC integration test to use the new expression syntax**

In `roadrunner-sampler-jdbc/src/it/java/io/roadrunner/samplers/jdbc/tests/JDBCSamplerProviderIT.java`:

First, change the `defaultSamplerOptions` helper so every test that goes through `JDBCSamplerOptions`/`JDBCSamplerPlugin` keeps passing bare SQL to the helper, which now wraps it:

```java
private static JDBCSamplerOptions defaultSamplerOptions(JDBCSamplerPlugin plugin, String url, String query) {
    var options = plugin.options();
    options.url = url;
    options.username = "SA";
    options.password = "";
    options.query = "query(\"%s\")".formatted(query);
    options.driverPath = Paths.get(DRIVER_PATH);
    return options;
}
```

Second, `errorOnConnectionFailure` constructs a `JDBCSamplerProvider` directly, bypassing options/plugin entirely — since the constructor's `String` now always means an expression, update the literal it passes:

```java
@Test
void errorOnConnectionFailure() {
    var failingDataSource = new ExceptionThrowingDataSource();
    try (var provider = new JDBCSamplerProvider(failingDataSource, "query(\"SELECT 1\")");
         var sampler = provider.newSampler()) {
        var response = sampler.execute(SamplerParameters.NONE);
        assertThat(response)
                .asInstanceOf(type(SamplerResponse.Error.class))
                .satisfies(r -> {
                    assertThat(r.timestamp()).isGreaterThan(0);
                    assertThat(r.stopTime()).isGreaterThan(r.timestamp());
                    assertThat(r.message()).isEqualTo("simulated connection failure");
                });
        assertThat(provider.sampleCount()).isEqualTo(1);
        assertThat(provider.totalAcquireNanos()).isPositive();
    }
}
```

(Only the string literal `"SELECT 1"` → `"query(\"SELECT 1\")"` changes; the rest of the test is identical. `provider.sampleCount()`/`totalAcquireNanos()` still work because `JDBCSample.query("SELECT 1")`'s returned `Sampler` still records timings on the same `JDBCSampler` instance whether it's constructed by hand or through the extension point.)

- [ ] **Step 4: Run unit and integration tests to verify they pass**

Run: `./mvnw -pl roadrunner-sampler-jdbc -am verify`
Expected: PASS — all `JDBCSamplerProviderIT` cases green (uses the hsqldb driver copied into `target/jdbc-drivers` by the `pre-integration-test` phase already configured in the module's `pom.xml`).

- [ ] **Step 5: Format and commit**

```bash
./mvnw spotless:apply -pl roadrunner-sampler-jdbc
git add roadrunner-sampler-jdbc/src/main/java/io/roadrunner/samplers/jdbc/JDBCSampler.java \
        roadrunner-sampler-jdbc/src/main/java/io/roadrunner/samplers/jdbc/JDBCSamplerProvider.java \
        roadrunner-sampler-jdbc/src/it/java/io/roadrunner/samplers/jdbc/tests/JDBCSamplerProviderIT.java
git commit -m "Migrate JDBC sampler onto the SamplerExtensionPoint mechanism"
```

---

### Task 4: Migrate the Neo4j sampler

**Files:**
- Create: `roadrunner-sampler-neo4j/src/main/java/io/roadrunner/samplers/neo4j/Neo4jSampler.java`
- Modify: `roadrunner-sampler-neo4j/src/main/java/io/roadrunner/samplers/neo4j/Neo4jSamplerProvider.java`
- Modify: `roadrunner-sampler-neo4j/src/it/java/io/roadrunner/samplers/neo4j/Neo4jSamplerPluginIT.java`

**Interfaces:**
- Consumes: `SamplerExtensionPoint.bind(Object, String)` (Task 2).
- Produces: `Neo4jSampler` with `Sampler query(String cypher)` and `void close()`. `Neo4jSamplerProvider` keeps `newSampler()`/`close()` and its existing `(Driver, String)` constructor, whose `String` now always means a full expression rather than bare Cypher.

**No changes to `Neo4jSamplerPlugin.java`.** Its `newSamplerProvider` method already calls `new Neo4jSamplerProvider(driver, options.query)` — untouched; only what the string means changes.

No `module-info.java` changes needed (same reasoning as Task 3).

- [ ] **Step 1: Extract `Neo4jSampler`**

Create `roadrunner-sampler-neo4j/src/main/java/io/roadrunner/samplers/neo4j/Neo4jSampler.java`:

```java
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
package io.roadrunner.samplers.neo4j;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.samplers.Sampler;
import java.util.Map;
import org.neo4j.driver.Driver;

/**
 * Extension-point methods class for the Neo4j sampler: {@link #query(String)} is bound from a
 * CLI {@code query("RETURN 1")} expression via {@link io.roadrunner.samplers.spi.SamplerExtensionPoint}.
 */
public class Neo4jSampler {

    private final Driver driver;

    public Neo4jSampler(Driver driver) {
        this.driver = driver;
    }

    public Sampler query(String cypher) {
        return parameters -> {
            var startTime = System.nanoTime();
            try (var session = driver.session()) {
                // Neo4j's Session.run accepts Map<String, Object>; SamplerParameters.asMap returns
                // Map<String, ?>. Erasure makes the cast safe — Session.run is a read-only consumer
                // of the map. See #137 for typed CSV values (Integer/Long/etc. instead of String).
                @SuppressWarnings("unchecked")
                var params = (Map<String, Object>) parameters.asMap();
                var result = session.run(cypher, params);
                return SamplerResponse.response(startTime, System.nanoTime(), result.consume());
            } catch (Exception e) {
                return SamplerResponse.error(startTime, System.nanoTime(), e.getMessage());
            }
        };
    }

    public void close() {
        driver.close();
    }
}
```

- [ ] **Step 2: Rewrite `Neo4jSamplerProvider` as a thin wrapper**

Replace the full contents of `roadrunner-sampler-neo4j/src/main/java/io/roadrunner/samplers/neo4j/Neo4jSamplerProvider.java`:

```java
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
package io.roadrunner.samplers.neo4j;

import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.samplers.spi.SamplerExtensionPoint;
import java.util.function.Supplier;
import org.neo4j.driver.Driver;

public class Neo4jSamplerProvider implements SamplerProvider {

    private final Neo4jSampler neo4jSampler;
    private final Supplier<Sampler> samplerSupplier;

    public Neo4jSamplerProvider(Driver driver, String expressionText) {
        this.neo4jSampler = new Neo4jSampler(driver);
        this.samplerSupplier = SamplerExtensionPoint.bind(neo4jSampler, expressionText);
    }

    @Override
    public Sampler newSampler() {
        return samplerSupplier.get();
    }

    @Override
    public void close() {
        neo4jSampler.close();
    }
}
```

- [ ] **Step 3: Update the Neo4j integration test to use the new expression syntax**

In `roadrunner-sampler-neo4j/src/it/java/io/roadrunner/samplers/neo4j/Neo4jSamplerPluginIT.java`, update all three tests. `invalidQuery` previously left `options.query` unset (`null`) to exercise Neo4j's own "query text should not be null" validation; with the new syntax `options.query` is now always a parseable expression (a null/missing value would fail in `SamplerExpression.parse` before ever reaching Neo4j, with a different, clearer message), so this test now exercises an *empty* cypher literal instead, and asserts only that an error surfaces (not the exact driver wording, which may differ):

```java
@Test
void invalidQuery() throws Exception {
    try (var plugin = new Neo4jSamplerPlugin()) {
        var options = plugin.options();
        options.uri = new URI("neo4j://%s:%d".formatted(neo4j.getHost(), neo4j.getMappedPort(7687)));
        options.username = "neo4j";
        options.password = "";
        options.query = "query(\"\")";
        try (var samplerProvider = options.samplerProvider()) {
            var sampler = samplerProvider.newSampler();
            var response = sampler.execute(SamplerParameters.NONE);
            assertThat(response).asInstanceOf(type(SamplerResponse.Error.class)).satisfies(e -> {
                assertThat(e.timestamp()).isLessThanOrEqualTo(e.stopTime());
                assertThat(e.stopTime()).isLessThanOrEqualTo(System.nanoTime());
                assertThat(e.message()).isNotBlank();
            });
        }
    }
}

@Test
void validQuery() throws Exception {
    try (var plugin = new Neo4jSamplerPlugin()) {
        var options = plugin.options();
        options.uri = new URI("neo4j://%s:%d".formatted(neo4j.getHost(), neo4j.getMappedPort(7687)));
        options.username = "neo4j";
        options.password = "";
        options.query = "query(\"RETURN 1\")";
        try (var samplerProvider = options.samplerProvider()) {
            var sampler = samplerProvider.newSampler();
            var response = sampler.execute(SamplerParameters.NONE);
            assertThat(response).asInstanceOf(type(SamplerResponse.Response.class)).satisfies(r -> assertThat(r.timestamp()).isLessThanOrEqualTo(System.nanoTime()));
        }
    }
}

@Test
void validParameterizedQuery() throws Exception {
    try (var plugin = new Neo4jSamplerPlugin()) {
        var options = plugin.options();
        options.uri = new URI("neo4j://%s:%d".formatted(neo4j.getHost(), neo4j.getMappedPort(7687)));
        options.username = "neo4j";
        options.password = "";
        options.query = "query(\"RETURN $param\")";
        try (var samplerProvider = options.samplerProvider()) {
            var sampler = samplerProvider.newSampler();
            var response = sampler.execute(SamplerParameters.of("param", "1"));
            assertThat(response).asInstanceOf(type(SamplerResponse.Response.class))
                    .satisfies(r -> {
                        assertThat(r.timestamp()).isLessThanOrEqualTo(r.stopTime());
                        assertThat(r.stopTime()).isLessThanOrEqualTo(System.nanoTime());
                    });
        }
    }
}
```

- [ ] **Step 4: Run integration tests to verify they pass**

Run: `./mvnw -pl roadrunner-sampler-neo4j -am verify`
Expected: PASS — all `Neo4jSamplerPluginIT` cases green (requires Docker for the Neo4j Testcontainers container).

- [ ] **Step 5: Format and commit**

```bash
./mvnw spotless:apply -pl roadrunner-sampler-neo4j
git add roadrunner-sampler-neo4j/src/main/java/io/roadrunner/samplers/neo4j/Neo4jSampler.java \
        roadrunner-sampler-neo4j/src/main/java/io/roadrunner/samplers/neo4j/Neo4jSamplerProvider.java \
        roadrunner-sampler-neo4j/src/it/java/io/roadrunner/samplers/neo4j/Neo4jSamplerPluginIT.java
git commit -m "Migrate Neo4j sampler onto the SamplerExtensionPoint mechanism"
```

---

### Task 5: Dispatch microbenchmark (direct vs `MethodHandle` vs reflection)

**Files:**
- Create: `roadrunner-microbenchmarks/src/main/java/io/roadrunner/SamplerFactoryDispatchBenchmarks.java`

**Interfaces:**
- Consumes: `io.roadrunner.api.samplers.Sampler`, `io.roadrunner.api.events.SamplerResponse` (both already on the module's compile classpath transitively via the existing `roadrunner-core` dependency — no `pom.xml` change needed, same as how `RoadrunnerBenchmarks.java` already imports them without an explicit `roadrunner-api` dependency entry).
- Produces: a standalone JMH benchmark class, not consumed by any other task.

This task has no unit-test red/green cycle (JMH benchmarks aren't assertions) — "passing" means the benchmark jar builds and a short smoke run executes cleanly.

- [ ] **Step 1: Write the benchmark**

Create `roadrunner-microbenchmarks/src/main/java/io/roadrunner/SamplerFactoryDispatchBenchmarks.java`:

```java
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
package io.roadrunner;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.samplers.Sampler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Compares three ways of turning a resolved target + literal argument into a {@link Sampler},
 * at the frequency {@code SamplerProvider.newSampler()} is actually called (once per virtual
 * thread) — see the "Microbenchmarks" section of
 * docs/superpowers/specs/2026-07-13-sampler-method-extension-points-design.md. This does not
 * measure the per-request {@code execute()} hot path, which is unaffected by this design either
 * way.
 */
public class SamplerFactoryDispatchBenchmarks {

    public static class DispatchFixture {
        public Sampler query(String sql) {
            return parameters -> SamplerResponse.empty(0, 0);
        }
    }

    @State(Scope.Benchmark)
    public static class DispatchState {
        DispatchFixture fixture;
        String sql;
        MethodHandle boundHandle;
        Method reflectMethod;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            fixture = new DispatchFixture();
            sql = "SELECT 1";
            reflectMethod = DispatchFixture.class.getMethod("query", String.class);
            MethodHandle handle = MethodHandles.publicLookup().unreflect(reflectMethod);
            boundHandle = MethodHandles.insertArguments(handle, 0, fixture, sql);
        }
    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    public Sampler directDispatch(DispatchState state) {
        return state.fixture.query(state.sql);
    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    public Sampler methodHandleDispatch(DispatchState state) throws Throwable {
        return (Sampler) state.boundHandle.invoke();
    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    public Sampler reflectionDispatch(DispatchState state) throws Exception {
        return (Sampler) state.reflectMethod.invoke(state.fixture, state.sql);
    }
}
```

- [ ] **Step 2: Build the benchmarks jar**

Run: `./mvnw -pl roadrunner-microbenchmarks -am package -DskipTests`
Expected: BUILD SUCCESS, producing `roadrunner-microbenchmarks/target/benchmarks.jar`.

- [ ] **Step 3: Smoke-run the new benchmarks with reduced iterations**

Run: `java -jar roadrunner-microbenchmarks/target/benchmarks.jar SamplerFactoryDispatch -f 1 -wi 1 -i 1`
Expected: all three benchmarks (`directDispatch`, `methodHandleDispatch`, `reflectionDispatch`) execute and print a score — no exceptions. This is a smoke test, not a performance gate; a full comparison run is a separate manual step via `task benchmark:run` per the spec.

- [ ] **Step 4: Format and commit**

```bash
./mvnw spotless:apply -pl roadrunner-microbenchmarks
git add roadrunner-microbenchmarks/src/main/java/io/roadrunner/SamplerFactoryDispatchBenchmarks.java
git commit -m "Add JMH benchmark comparing direct, MethodHandle, and reflection sampler dispatch"
```
