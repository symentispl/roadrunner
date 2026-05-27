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
import io.roadrunner.api.events.SamplerResponse;
import java.util.function.Consumer;

public class DefaultSamplerResponseBuilder implements SamplerResponseBuilder {

    private final int metricCapacity;
    private final int attachmentCapacity;
    private final ReusableSamplerSink sink = new ReusableSamplerSink();

    public DefaultSamplerResponseBuilder(int metricCapacity, int attachmentCapacity) {
        this.metricCapacity = metricCapacity;
        this.attachmentCapacity = attachmentCapacity;
    }

    @Override
    public SamplerResponse.Response response(long start, long stop) {
        return new SamplerResponse.Response(start, stop, 0, 0);
    }

    @Override
    public SamplerResponse.Response response(long start, long stop, Consumer<SamplerSink> writer) {
        var response = new SamplerResponse.Response(start, stop, metricCapacity, attachmentCapacity);
        sink.attachTo(response);
        writer.accept(sink);
        sink.detach();
        return response;
    }

    @Override
    public SamplerResponse.Error error(long start, long stop, String message) {
        var error = new SamplerResponse.Error(start, stop, 0, attachmentCapacity);
        error.setAttachmentValue(AttachmentRegistry.ERROR_MESSAGE, message);
        return error;
    }

    @Override
    public SamplerResponse.Error error(long start, long stop, String message, Consumer<SamplerSink> writer) {
        var error = new SamplerResponse.Error(start, stop, metricCapacity, attachmentCapacity);
        error.setAttachmentValue(AttachmentRegistry.ERROR_MESSAGE, message);
        sink.attachTo(error);
        writer.accept(sink);
        sink.detach();
        return error;
    }
}
