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
package io.roadrunner.site.tests;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** One invocation per example dir found under the examples dir named by the test class' {@link Snippets}. */
class SnippetsInvocationContextProvider implements TestTemplateInvocationContextProvider {

    private static final Path EXAMPLES = Path.of("../site/modules/ROOT/examples");

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return true;
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        var snippets = context.getRequiredTestClass().getAnnotation(Snippets.class);
        if (snippets == null) {
            throw new IllegalStateException("%s must be annotated with @Snippets".formatted(context.getRequiredTestClass()));
        }
        try (var dirs = Files.list(EXAMPLES.resolve(snippets.value()))) {
            return dirs.filter(Files::isDirectory).sorted().map(SnippetsInvocationContextProvider::invocation).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static TestTemplateInvocationContext invocation(Path exampleDir) {
        return new TestTemplateInvocationContext() {
            @Override
            public String getDisplayName(int invocationIndex) {
                return exampleDir.getFileName().toString();
            }

            @Override
            public List<org.junit.jupiter.api.extension.Extension> getAdditionalExtensions() {
                return List.of(new ParameterResolver() {
                    @Override
                    public boolean supportsParameter(ParameterContext p, ExtensionContext e) {
                        return p.getParameter().getType() == Path.class && !p.isAnnotated(TempDir.class);
                    }

                    @Override
                    public Object resolveParameter(ParameterContext p, ExtensionContext e) {
                        return exampleDir;
                    }
                });
            }
        };
    }
}
