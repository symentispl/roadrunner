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

import static java.util.stream.Collectors.joining;

import io.roadrunner.samplers.spi.SamplerExpressionException;
import io.roadrunner.samplers.spi.SamplerExtensionPointDescriptor;
import java.util.List;

/**
 * Formats sampler extension point descriptors into human-readable CLI usage text suitable for
 * inclusion in a picocli command footer.
 */
final class SamplerExtensionPointsUsage {

    private SamplerExtensionPointsUsage() {}

    /**
     * Returns a multi-line usage string describing the given extension points, or an empty string
     * if the list is empty.
     *
     * <p>Example output:
     * <pre>
     * Expression syntax:
     *   query(&lt;sql&gt;)   Execute a SQL query
     * </pre>
     */
    static String format(List<SamplerExtensionPointDescriptor> extensionPoints) {
        if (extensionPoints.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        sb.append("Expression syntax:%n".formatted());
        int maxExpressionLength = extensionPoints.stream()
                .mapToInt(ep -> ep.usageExpression().length())
                .max()
                .orElse(0);
        for (var ep : extensionPoints) {
            String expression = ep.usageExpression();
            int padding = maxExpressionLength - expression.length() + 2;
            sb.append("  ")
                    .append(expression)
                    .repeat(" ", padding)
                    .append(ep.description())
                    .append("%n".formatted());
        }
        return sb.toString();
    }

    /**
     * A parse/resolution failure on a sampler expression is teachable: this returns the sampler's
     * available operations and one example built from the first of them, or {@code ""} if the
     * sampler declares none. The example's arguments are each parameter's own name, quoted as a
     * literal — not a realistic value, but valid, copy-pasteable expression syntax with no need for
     * per-sampler sample data.
     *
     * <p>Example output:
     * <pre>
     *   available: GET(&lt;url&gt;), POST(&lt;url&gt;, &lt;body&gt;)
     *   example:   roadrunner run -c 10 -n 100 http 'GET("url")'
     * </pre>
     */
    static String errorHint(String samplerName, List<SamplerExtensionPointDescriptor> extensionPoints) {
        if (extensionPoints.isEmpty()) {
            return "";
        }
        var available = extensionPoints.stream()
                .map(SamplerExtensionPointDescriptor::usageExpression)
                .collect(joining(", "));

        var first = extensionPoints.getFirst();
        var exampleArguments =
                first.parameterNames().stream().map(name -> "\"" + name + "\"").collect(joining(", "));
        var example = "roadrunner run -c 10 -n 100 %s '%s(%s)'".formatted(samplerName, first.name(), exampleArguments);

        return "%n  available: %s%n  example:   %s".formatted(available, example);
    }

    // extracted so the enrichment is testable without driving the whole CLI. No cause set: the new
    // message already embeds e's message verbatim, and Main.report() joins a cause chain's
    // messages with ": ", so chaining e here would print that text twice.
    static SamplerExpressionException wrapSamplerExpressionError(
            Throwable e, String samplerName, List<SamplerExtensionPointDescriptor> extensionPoints) {
        var hint = errorHint(samplerName, extensionPoints);
        return new SamplerExpressionException(e.getMessage() + hint);
    }
}
