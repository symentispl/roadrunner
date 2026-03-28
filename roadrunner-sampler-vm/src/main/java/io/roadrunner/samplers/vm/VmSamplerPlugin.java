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
package io.roadrunner.samplers.vm;

import java.util.concurrent.Executors;

public class VmSamplerPlugin implements io.roadrunner.samplers.spi.SamplerPlugin<VmSamplerProvider, VmSamplerOptions> {
    @Override
    public String name() {
        return "vm";
    }

    @Override
    public VmSamplerProvider newSamplerProvider(VmSamplerOptions options) {
        return new VmSamplerProvider(Executors.newCachedThreadPool(), options.sleepTime);
    }

    @Override
    public VmSamplerOptions options() {
        return new VmSamplerOptions(this);
    }
}
