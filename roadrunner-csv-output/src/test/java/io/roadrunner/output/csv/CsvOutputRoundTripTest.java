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
package io.roadrunner.output.csv;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.api.attachments.AttachmentRegistry;
import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.metrics.MetricRegistry;
import io.roadrunner.api.metrics.MetricUnit;
import io.roadrunner.api.samplers.DefaultSamplerResponseBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvOutputRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripsResponsesAttachmentsAndUserEvents() {
        var metrics = new MetricRegistry();
        var latencyKey = metrics.register("latency", MetricUnit.NANOSECONDS);
        var bytesKey = metrics.register("bytes", MetricUnit.BYTES);

        var attachments = new AttachmentRegistry();
        var bodyKey = attachments.register("body");
        var noteKey = attachments.register("note");

        var listener = new CsvOutputEventListener(tempDir.resolve("out.csv"), metrics, attachments);

        var ok = new SamplerResponse.Response(100L, 200L, metrics.size(), attachments.size())
                .withScheduledStartTime(90L)
                .withLatency(50L);
        ok.setMetricValue(latencyKey, 12.5);
        ok.setMetricValue(bytesKey, 1024.0);
        ok.setAttachmentValue(bodyKey, "hello, \"world\"\nwith a newline");
        // noteKey deliberately left unset to exercise the absent-attachment case

        var ko = new SamplerResponse.Error(300L, 400L, metrics.size(), attachments.size())
                .withScheduledStartTime(290L)
                .withLatency(75L);
        ko.setAttachmentValue(AttachmentRegistry.ERROR_MESSAGE, "boom, it broke");
        ko.setAttachmentValue(noteKey, "note value");
        // metric slots deliberately left unset to exercise the absent-metric case

        var enter = new UserEvent.Enter(150L);
        var exit = new UserEvent.Exit(160L);

        listener.onStart();
        listener.onEvent(List.of(ok, enter, ko, exit));
        listener.onStop();

        var reader = listener.samplesReader();
        assertThat(reader.metricKeys()).containsExactlyElementsOf(metrics.registeredKeys());
        assertThat(reader.attachmentKeys()).containsExactlyElementsOf(attachments.registeredKeys());

        var events = new ArrayList<Event>();
        reader.forEach(events::add);
        assertThat(events).hasSize(4);

        assertThat(events.get(0)).isInstanceOfSatisfying(SamplerResponse.Response.class, response -> {
            assertThat(response.timestamp()).isEqualTo(100L);
            assertThat(response.scheduledStartTime()).isEqualTo(90L);
            assertThat(response.stopTime()).isEqualTo(200L);
            assertThat(response.latency()).isEqualTo(50L);
            assertThat(response.metricValueAt(latencyKey)).isEqualTo(12.5);
            assertThat(response.metricValueAt(bytesKey)).isEqualTo(1024.0);
            assertThat(response.attachmentValueAt(bodyKey)).isEqualTo("hello, \"world\"\nwith a newline");
            assertThat(response.attachmentValueAt(noteKey)).isNull();
        });

        assertThat(events.get(1))
                .isInstanceOfSatisfying(
                        UserEvent.Enter.class, e -> assertThat(e.timestamp()).isEqualTo(150L));

        assertThat(events.get(2)).isInstanceOfSatisfying(SamplerResponse.Error.class, error -> {
            assertThat(error.timestamp()).isEqualTo(300L);
            assertThat(error.scheduledStartTime()).isEqualTo(290L);
            assertThat(error.stopTime()).isEqualTo(400L);
            assertThat(error.latency()).isEqualTo(75L);
            assertThat(error.message()).isEqualTo("boom, it broke");
            assertThat(error.attachmentValueAt(noteKey)).isEqualTo("note value");
            // metrics were never set on the KO response; absent slots read back as 0.0,
            // indistinguishable from an explicit zero (a known limitation of the current format).
            assertThat(error.metricValueAt(latencyKey)).isEqualTo(0.0);
            assertThat(error.metricValueAt(bytesKey)).isEqualTo(0.0);
        });

        assertThat(events.get(3))
                .isInstanceOfSatisfying(
                        UserEvent.Exit.class, e -> assertThat(e.timestamp()).isEqualTo(160L));
    }

    @Test
    void roundTripsPerSampleJdbcTimings() throws Exception {
        var metrics = new MetricRegistry();
        var acquireKey = metrics.register("jdbc.acquire_ns", MetricUnit.NANOSECONDS);
        var queryKey = metrics.register("jdbc.query_ns", MetricUnit.NANOSECONDS);
        var attachments = new AttachmentRegistry();
        var builder = new DefaultSamplerResponseBuilder(metrics.size(), attachments.size());
        var first = builder.response(100L, 1000L, sink -> {
            sink.add(acquireKey, 400.0);
            sink.add(queryKey, 500.0);
        }).withScheduledStartTime(100L).withLatency(900L);
        var second = builder.response(2000L, 3000L, sink -> {
            sink.add(acquireKey, 100.0);
            sink.add(queryKey, 900.0);
        }).withScheduledStartTime(2000L).withLatency(1000L);
        var output = tempDir.resolve("jdbc-timings.csv");
        var listener = new CsvOutputEventListener(output, metrics, attachments);

        listener.onStart();
        listener.onEvent(List.of(first, second));
        listener.onStop();

        assertThat(Files.readAllLines(output)).containsExactly(
                "type,scheduledStartTime,timestamp,stopTime,latency,status,"
                        + "metric:jdbc.acquire_ns:NANOSECONDS,metric:jdbc.query_ns:NANOSECONDS,"
                        + "attachment:errorMessage",
                "REQ,100,100,1000,900,OK,400.0,500.0,",
                "REQ,2000,2000,3000,1000,OK,100.0,900.0,");
        var reader = listener.samplesReader();
        assertThat(reader.metricKeys()).containsExactlyElementsOf(metrics.registeredKeys());
        var events = new ArrayList<Event>();
        reader.forEach(events::add);
        assertThat(events).hasSize(2);
        var firstRead = (SamplerResponse<?>) events.getFirst();
        var secondRead = (SamplerResponse<?>) events.getLast();
        assertThat(firstRead.metricValueAt(acquireKey)).isEqualTo(400.0);
        assertThat(firstRead.metricValueAt(queryKey)).isEqualTo(500.0);
        assertThat(secondRead.metricValueAt(acquireKey)).isEqualTo(100.0);
        assertThat(secondRead.metricValueAt(queryKey)).isEqualTo(900.0);
    }

    @Test
    void reconstructsHeaderColumnsInRegistrationOrder() {
        var metrics = new MetricRegistry();
        metrics.register("latency", MetricUnit.NANOSECONDS);
        metrics.register("bytes", MetricUnit.BYTES);

        var attachments = new AttachmentRegistry();
        attachments.register("body");
        attachments.register("note");

        var listener = new CsvOutputEventListener(tempDir.resolve("header.csv"), metrics, attachments);
        var response = new SamplerResponse.Response(1L, 2L, metrics.size(), attachments.size());

        listener.onStart();
        listener.onEvent(List.of(response));
        listener.onStop();

        var reader = listener.samplesReader();
        assertThat(reader.metricKeys()).containsExactlyElementsOf(metrics.registeredKeys());
        assertThat(reader.attachmentKeys()).containsExactlyElementsOf(attachments.registeredKeys());
    }

    @Test
    void reconstructsHeaderColumnsWhenNamesContainDelimiterCharacters() {
        var metrics = new MetricRegistry();
        metrics.register("latency,ms", MetricUnit.NANOSECONDS);

        var attachments = new AttachmentRegistry();
        attachments.register("note \"quoted\", with comma");

        var listener = new CsvOutputEventListener(tempDir.resolve("header-delimiters.csv"), metrics, attachments);
        var response = new SamplerResponse.Response(1L, 2L, metrics.size(), attachments.size());

        listener.onStart();
        listener.onEvent(List.of(response));
        listener.onStop();

        var reader = listener.samplesReader();
        assertThat(reader.metricKeys()).containsExactlyElementsOf(metrics.registeredKeys());
        assertThat(reader.attachmentKeys()).containsExactlyElementsOf(attachments.registeredKeys());
    }

    @Test
    void reconstructsHeaderColumnsWhenNamesContainEmbeddedNewlines() {
        var metrics = new MetricRegistry();
        metrics.register("latency\nms", MetricUnit.NANOSECONDS);

        var attachments = new AttachmentRegistry();
        attachments.register("multi\nline\nnote");

        var listener = new CsvOutputEventListener(tempDir.resolve("header-newlines.csv"), metrics, attachments);
        var response = new SamplerResponse.Response(1L, 2L, metrics.size(), attachments.size());

        listener.onStart();
        listener.onEvent(List.of(response));
        listener.onStop();

        var reader = listener.samplesReader();
        assertThat(reader.metricKeys()).containsExactlyElementsOf(metrics.registeredKeys());
        assertThat(reader.attachmentKeys()).containsExactlyElementsOf(attachments.registeredKeys());
    }
}
