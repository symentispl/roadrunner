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

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.api.attachments.AttachmentRegistry;
import io.roadrunner.api.metrics.MetricRegistry;
import io.roadrunner.api.metrics.MetricUnit;
import org.junit.jupiter.api.Test;

class DefaultSamplerResponseBuilderTest {

    @Test
    void buildsResponseWithMetricValues() {
        var registry = new MetricRegistry();
        var bytesKey = registry.register("bytes_read", MetricUnit.BYTES);
        var rowsKey = registry.register("row_count", MetricUnit.COUNT);
        var builder = new DefaultSamplerResponseBuilder(registry.size(), 0);

        var response = builder.response(100L, 200L, sink -> {
            sink.add(bytesKey, 1024.0);
            sink.add(rowsKey, 42.0);
        });

        assertThat(response.metricValueAt(bytesKey)).isEqualTo(1024.0);
        assertThat(response.metricValueAt(rowsKey)).isEqualTo(42.0);
    }

    @Test
    void buildsErrorWithMessage() {
        var attachmentRegistry = new AttachmentRegistry();
        var builder = new DefaultSamplerResponseBuilder(0, attachmentRegistry.size());

        var error = builder.error(100L, 200L, "timeout");

        assertThat(error.message()).isEqualTo("timeout");
    }

    @Test
    void buildsErrorWithMetricValues() {
        var metricRegistry = new MetricRegistry();
        var bytesKey = metricRegistry.register("bytes_read", MetricUnit.BYTES);
        var attachmentRegistry = new AttachmentRegistry();
        var builder = new DefaultSamplerResponseBuilder(metricRegistry.size(), attachmentRegistry.size());

        var error = builder.error(100L, 200L, "timeout", sink -> sink.add(bytesKey, 0.0));

        assertThat(error.metricValueAt(bytesKey)).isEqualTo(0.0);
        assertThat(error.message()).isEqualTo("timeout");
    }

    @Test
    void buildsResponseWithoutMetrics() {
        var builder = new DefaultSamplerResponseBuilder(0, 0);

        var response = builder.response(100L, 200L);

        assertThat(response).isNotNull();
        assertThat(response.timestamp()).isEqualTo(100L);
        assertThat(response.stopTime()).isEqualTo(200L);
    }

    @Test
    void buildsErrorWithoutMetrics() {
        var attachmentRegistry = new AttachmentRegistry();
        var builder = new DefaultSamplerResponseBuilder(0, attachmentRegistry.size());

        var error = builder.error(100L, 200L, "timeout");

        assertThat(error).isNotNull();
        assertThat(error.message()).isEqualTo("timeout");
    }

    @Test
    void reusableSinkDoesNotLeakBetweenRequests() {
        var registry = new MetricRegistry();
        var bytesKey = registry.register("bytes_read", MetricUnit.BYTES);
        var builder = new DefaultSamplerResponseBuilder(registry.size(), 0);

        var response1 = builder.response(0L, 1L, sink -> sink.add(bytesKey, 100.0));
        var response2 = builder.response(0L, 1L, sink -> sink.add(bytesKey, 200.0));

        assertThat(response1.metricValueAt(bytesKey)).isEqualTo(100.0);
        assertThat(response2.metricValueAt(bytesKey)).isEqualTo(200.0);
    }
}
