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
import io.roadrunner.api.samplers.DefaultSamplerResponseBuilder;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.api.samplers.SamplerProvider;

/**
 * Captures the result of the plugin registration lifecycle and acts as the single place
 * that knows how to create correctly-sized {@link DefaultSamplerResponseBuilder} instances.
 *
 * <p>Use {@link #create(SamplerPlugin, SamplerOptions)} in tests so the full plugin
 * lifecycle (provider creation + metric/attachment registration) is exercised identically
 * to the framework path in {@code DefaultRoadrunner}.
 *
 * <p>Plugin lifecycle (close) remains the caller's responsibility.
 */
public final class SamplerContext {

    private final SamplerProvider provider;
    private final MetricRegistry metricRegistry;
    private final AttachmentRegistry attachmentRegistry;

    private SamplerContext(
            SamplerProvider provider, MetricRegistry metricRegistry, AttachmentRegistry attachmentRegistry) {
        this.provider = provider;
        this.metricRegistry = metricRegistry;
        this.attachmentRegistry = attachmentRegistry;
    }

    /**
     * Framework path — provider is created and managed externally.
     */
    public static SamplerContext of(SamplerProvider provider) {
        var metricRegistry = new MetricRegistry();
        var attachmentRegistry = new AttachmentRegistry();
        provider.registerMetrics(metricRegistry);
        provider.registerAttachments(attachmentRegistry);
        return new SamplerContext(provider, metricRegistry, attachmentRegistry);
    }

    /**
     * Test path — creates the provider from the given plugin and options, then runs the
     * full registration lifecycle. The plugin's lifecycle remains the caller's responsibility.
     */
    public static <T extends SamplerProvider, O extends SamplerOptions<T>> SamplerContext create(
            SamplerPlugin<T, O> plugin, O options) {
        return of(plugin.newSamplerProvider(options));
    }

    public MetricRegistry metricRegistry() {
        return metricRegistry;
    }

    public AttachmentRegistry attachmentRegistry() {
        return attachmentRegistry;
    }

    public DefaultSamplerResponseBuilder newResponseBuilder() {
        return new DefaultSamplerResponseBuilder(metricRegistry.size(), attachmentRegistry.size());
    }

    public Sampler newSampler() {
        return provider.newSampler();
    }
}
