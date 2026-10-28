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

/**
 * Options of a sampler, filled in from the command line.
 * <p>
 * Options are declared with {@link io.roadrunner.samplers.spi.cli.SamplerOption},
 * {@link io.roadrunner.samplers.spi.cli.SamplerParameter} and
 * {@link io.roadrunner.samplers.spi.cli.SamplerOptionGroup} on the fields of the implementing class, which keeps
 * samplers independent of the command line library used to parse them.
 */
public interface SamplerOptions<T extends SamplerProvider> {
    T samplerProvider();

    /** Description of the sampler shown in its usage. */
    default String commandDescription() {
        return "";
    }
}
