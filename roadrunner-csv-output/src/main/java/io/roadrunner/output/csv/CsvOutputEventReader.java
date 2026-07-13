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

import static java.lang.Long.parseLong;

import io.roadrunner.api.attachments.AttachmentKey;
import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.api.measurments.Sample;
import io.roadrunner.api.metrics.MetricKey;
import io.roadrunner.api.metrics.MetricUnit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class CsvOutputEventReader implements EventReader {

    private static final int FIRST_METRIC_COLUMN = 6;

    private final Path csvOutputFile;
    private final List<MetricKey> metricKeys;
    private final List<AttachmentKey> attachmentKeys;
    private final int firstAttachmentColumn;

    public CsvOutputEventReader(Path csvOutputFile) {
        this.csvOutputFile = csvOutputFile;
        var parsed = parseColumnsFromHeader(csvOutputFile);
        this.metricKeys = parsed.metricKeys();
        this.attachmentKeys = parsed.attachmentKeys();
        this.firstAttachmentColumn = FIRST_METRIC_COLUMN + metricKeys.size();
    }

    @Override
    public Collection<MetricKey> metricKeys() {
        return Collections.unmodifiableList(metricKeys);
    }

    @Override
    public Collection<AttachmentKey> attachmentKeys() {
        return Collections.unmodifiableList(attachmentKeys);
    }

    @Override
    public Iterator<Event> iterator() {
        try {
            var in = Files.newBufferedReader(csvOutputFile);
            var metricCount = metricKeys.size();
            var attachmentCount = attachmentKeys.size();
            return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(in).stream()
                    .map(record -> from(
                            record, metricKeys, metricCount, attachmentKeys, attachmentCount, firstAttachmentColumn))
                    .iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ParsedColumns parseColumnsFromHeader(Path csvOutputFile) {
        try (var in = Files.newBufferedReader(csvOutputFile)) {
            var headerLine = in.readLine();
            if (headerLine == null) {
                return new ParsedColumns(List.of(), List.of());
            }
            var columns = headerLine.split(",", -1);
            var metrics = new ArrayList<MetricKey>();
            var attachments = new ArrayList<AttachmentKey>();
            for (int i = FIRST_METRIC_COLUMN; i < columns.length; i++) {
                var col = columns[i];
                if (col.startsWith("metric:")) {
                    var parts = col.substring("metric:".length()).split(":", 2);
                    var name = parts[0];
                    var unit = parts.length > 1 ? MetricUnit.valueOf(parts[1]) : MetricUnit.COUNT;
                    metrics.add(new MetricKey(metrics.size(), name, unit));
                } else if (col.startsWith("attachment:")) {
                    var name = col.substring("attachment:".length());
                    attachments.add(new AttachmentKey(attachments.size(), name));
                }
            }
            return new ParsedColumns(List.copyOf(metrics), List.copyOf(attachments));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record ParsedColumns(List<MetricKey> metricKeys, List<AttachmentKey> attachmentKeys) {}

    private static Event from(
            CSVRecord record,
            List<MetricKey> metricKeys,
            int metricCount,
            List<AttachmentKey> attachmentKeys,
            int attachmentCount,
            int firstAttachmentColumn) {
        var type = record.get(0);
        return switch (type) {
            case "REQ" ->
                toResponse(record, metricKeys, metricCount, attachmentKeys, attachmentCount, firstAttachmentColumn);
            case "USER" -> toUser(record);
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private static Event toUser(CSVRecord record) {
        var timestamp = parseLong(record.get(1));
        var type = record.get(2);
        return switch (type) {
            case "ENTER" -> new UserEvent.Enter(timestamp);
            case "EXIT" -> new UserEvent.Exit(timestamp);
            default -> throw new IllegalArgumentException("Unexpected value: " + type);
        };
    }

    private static Event toResponse(
            CSVRecord record,
            List<MetricKey> metricKeys,
            int metricCount,
            List<AttachmentKey> attachmentKeys,
            int attachmentCount,
            int firstAttachmentColumn) {
        var scheduledStartTime = parseLong(record.get(1));
        var timestamp = parseLong(record.get(2));
        var stopTime = parseLong(record.get(3));
        var latency = parseLong(record.get(4));
        var status = Sample.Status.valueOf(record.get(5));
        SamplerResponse<?> response =
                switch (status) {
                    case Sample.Status.OK ->
                        new SamplerResponse.Response(timestamp, stopTime, metricCount, attachmentCount)
                                .withLatency(latency)
                                .withScheduledStartTime(scheduledStartTime);
                    case Sample.Status.KO ->
                        new SamplerResponse.Error(timestamp, stopTime, metricCount, attachmentCount)
                                .withLatency(latency)
                                .withScheduledStartTime(scheduledStartTime);
                };
        for (int i = 0; i < metricCount; i++) {
            var col = FIRST_METRIC_COLUMN + i;
            if (col < record.size()) {
                var raw = record.get(col);
                if (!raw.isEmpty()) {
                    response.setMetricValue(metricKeys.get(i), Double.parseDouble(raw));
                }
            }
        }
        for (int i = 0; i < attachmentCount; i++) {
            var col = firstAttachmentColumn + i;
            if (col < record.size()) {
                var raw = record.get(col);
                if (!raw.isEmpty()) {
                    response.setAttachmentValue(attachmentKeys.get(i), raw);
                }
            }
        }
        return response;
    }
}
