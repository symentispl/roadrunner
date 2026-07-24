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

import io.roadrunner.api.attachments.AttachmentRegistry;
import io.roadrunner.api.metrics.MetricRegistry;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.api.samplers.SamplerSinkRegistrar;
import java.util.function.Supplier;

/**
 * A {@link SamplerProvider} backed by a sampler operation expression bound to an extension-point
 * target (e.g. {@code JDBCSampler}). It delegates {@link #newSampler()} to the bound factory and,
 * when the target implements {@link SamplerSinkRegistrar}, forwards metric/attachment registration
 * to it — so the target captures the keys it emits and the rest of the framework treats this like
 * any other provider.
 *
 * <p>Sampler modules subclass this to add protocol-specific accessors (connection pools, counters)
 * while inheriting the provider wiring.
 */
public class SamplerExtension implements SamplerProvider {

    private final Object extensionPoint;
    private final Supplier<Sampler> samplerFactory;

    public SamplerExtension(Object extensionPoint, String expressionText) {
        this.extensionPoint = extensionPoint;
        this.samplerFactory = SamplerExtensionPoint.bind(extensionPoint, expressionText);
    }

    @Override
    public Sampler newSampler() {
        return samplerFactory.get();
    }

    @Override
    public void registerMetrics(MetricRegistry registry) {
        if (extensionPoint instanceof SamplerSinkRegistrar registrar) {
            registrar.registerMetrics(registry);
        }
    }

    @Override
    public void registerAttachments(AttachmentRegistry registry) {
        if (extensionPoint instanceof SamplerSinkRegistrar registrar) {
            registrar.registerAttachments(registry);
        }
    }
}
