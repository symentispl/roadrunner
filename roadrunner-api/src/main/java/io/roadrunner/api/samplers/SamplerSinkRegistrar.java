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
package io.roadrunner.api.samplers;

import io.roadrunner.api.attachments.AttachmentRegistry;
import io.roadrunner.api.metrics.MetricRegistry;

/**
 * Registration hook for the metric and attachment keys a sampler emits. Implemented by the object
 * that actually builds {@link io.roadrunner.api.events.SamplerResponse} instances — the {@link SamplerProvider} for
 * self-contained samplers, or the extension-point target (e.g. {@code JDBCSampler}) for samplers
 * bound via {@link io.roadrunner.api.samplers}'s extension mechanism.
 *
 * <p>Both methods are invoked once, during initialization, before any sampler runs. Implementations
 * capture the returned {@link io.roadrunner.api.metrics.MetricKey}/
 * {@link io.roadrunner.api.attachments.AttachmentKey} instances and use them when writing to the
 * {@link SamplerSink} in {@code Sampler.execute}.
 */
public interface SamplerSinkRegistrar {

    default void registerMetrics(MetricRegistry registry) {}

    default void registerAttachments(AttachmentRegistry registry) {}
}
