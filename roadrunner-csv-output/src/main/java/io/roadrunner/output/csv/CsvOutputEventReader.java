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

import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.api.measurments.Sample;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class CsvOutputEventReader implements EventReader {
    private final Path csvOutputFile;

    public CsvOutputEventReader(Path csvOutputFile) {
        this.csvOutputFile = csvOutputFile;
    }

    @Override
    public Iterator<Event> iterator() {
        try {
            var in = Files.newBufferedReader(csvOutputFile);
            return CSVFormat.DEFAULT.parse(in).stream()
                    .map(CsvOutputEventReader::from)
                    .iterator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Event from(CSVRecord record) {
        var type = record.get(0);
        return switch (type) {
            case "REQ" -> toResponse(record);
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

    private static Event toResponse(CSVRecord record) {
        var scheduledStartTime = parseLong(record.get(1));
        var timestamp = parseLong(record.get(2));
        var stopTime = parseLong(record.get(3));
        var latency = parseLong(record.get(4));
        var status = Sample.Status.valueOf(record.get(5));
        return switch (status) {
            case Sample.Status.OK ->
                new ProtocolResponse.Response<>(timestamp, stopTime, null)
                        .withLatency(latency)
                        .withScheduledStartTime(scheduledStartTime);
            case Sample.Status.KO ->
                new ProtocolResponse.Error(timestamp, stopTime, null)
                        .withLatency(latency)
                        .withScheduledStartTime(scheduledStartTime);
        };
    }
}
