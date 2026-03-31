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

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        description = "In-VM sampler, used as baseline to calculate roadrunner overhead",
        mixinStandardHelpOptions = true)
public class VmSamplerOptions implements io.roadrunner.samplers.spi.SamplerOptions<VmSamplerProvider> {

    @Option(names = "--sleep-time", description = "sleep time in ms", required = true)
    long sleepTime;

    private final VmSamplerPlugin vmSamplerPlugin;

    public VmSamplerOptions(VmSamplerPlugin vmSamplerPlugin) {
        this.vmSamplerPlugin = vmSamplerPlugin;
    }

    @Override
    public VmSamplerProvider samplerProvider() {
        return vmSamplerPlugin.newSamplerProvider(this);
    }
}
