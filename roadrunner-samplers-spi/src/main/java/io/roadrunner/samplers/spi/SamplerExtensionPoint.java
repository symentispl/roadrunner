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

import static java.util.stream.Collectors.joining;

import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.samplers.spi.internal.SamplerExpression;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
        var expression = SamplerExpression.parse(expressionText);

        var candidates = candidateMethods(target.getClass());

        var matching = candidates.stream()
                .filter(method -> method.getName().equals(expression.methodName()))
                .filter(method ->
                        method.getParameterCount() == expression.arguments().size())
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

        var method = matching.getFirst();

        MethodHandle handle;
        try {
            handle = MethodHandles.publicLookup().unreflect(method);
        } catch (IllegalAccessException e) {
            throw new PluginInitializationException(
                    "Cannot access extension point method '%s' on %s"
                            .formatted(method.getName(), target.getClass().getName()),
                    e);
        }

        var boundArguments = prependTarget(target, expression.arguments());
        var boundHandle = MethodHandles.insertArguments(handle, 0, boundArguments);

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
        var candidates = new ArrayList<Method>();
        for (var method : type.getMethods()) {
            if (method.getReturnType() != Sampler.class) {
                continue;
            }
            for (var parameterType : method.getParameterTypes()) {
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
        var withTarget = new Object[arguments.size() + 1];
        withTarget[0] = target;
        for (int i = 0; i < arguments.size(); i++) {
            withTarget[i + 1] = arguments.get(i);
        }
        return withTarget;
    }

    private static String describe(List<Method> candidates) {
        return candidates.stream()
                .map(m -> "%s(%d args)".formatted(m.getName(), m.getParameterCount()))
                .collect(joining(", "));
    }
}
