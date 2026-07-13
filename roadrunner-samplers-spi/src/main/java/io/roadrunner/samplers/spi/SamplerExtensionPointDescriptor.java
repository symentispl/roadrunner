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

import java.util.List;

/**
 * Describes a single sampler extension point that can be used in a CLI operation expression.
 *
 * <p>Extension points are methods on a sampler target object that accept string arguments and
 * return a {@link io.roadrunner.api.samplers.Sampler}. They are invoked via an expression such
 * as {@code query("SELECT 1")} passed on the command line.
 *
 * @param name           the method name of the extension point (e.g. {@code "query"})
 * @param parameterNames the ordered list of parameter names (e.g. {@code ["sql"]}), empty for
 *                       zero-argument extension points
 * @param description    a short human-readable description of what this extension point does
 */
public record SamplerExtensionPointDescriptor(String name, List<String> parameterNames, String description) {

    public SamplerExtensionPointDescriptor {
        parameterNames = List.copyOf(parameterNames);
    }

    /**
     * Returns the extension point formatted as a call-site expression with placeholder parameter
     * names, e.g. {@code query("<sql>")} or {@code noArgs()}.
     */
    public String usageExpression() {
        if (parameterNames.isEmpty()) {
            return name + "()";
        }
        var params = String.join(", ", parameterNames.stream().map(p -> "<" + p + ">").toList());
        return name + "(" + params + ")";
    }
}
