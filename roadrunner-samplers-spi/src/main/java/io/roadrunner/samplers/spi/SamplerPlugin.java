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

import io.roadrunner.api.samplers.SamplerProvider;
import java.util.List;

public interface SamplerPlugin<T extends SamplerProvider, O extends SamplerOptions<T>> extends AutoCloseable {
    String name();

    T newSamplerProvider(O options);

    O options();

    /**
     * Returns descriptors for each extension point this sampler exposes.
     *
     * <p>Extension points are operations that can be expressed as CLI expressions such as
     * {@code query("SELECT 1")}. Implementations should return one descriptor per available
     * operation so that CLI tooling can include them in usage help.
     *
     * <p>Returns an empty list by default (for samplers that do not use extension points).
     */
    default List<SamplerExtensionPointDescriptor> extensionPoints() {
        return List.of();
    }

    default void close() {}
}
