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
                    .append(" ".repeat(padding))
                    .append(ep.description())
                    .append("%n".formatted());
        }
        return sb.toString();
    }
}
