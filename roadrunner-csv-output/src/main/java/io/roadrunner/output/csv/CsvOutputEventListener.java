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

import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.EventListener;
import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.SamplesReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvOutputEventListener implements EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(CsvOutputEventListener.class);

    private final Path csvOutputFile;
    private BufferedWriter bufferedWriter;

    public CsvOutputEventListener(Path csvOutputFile) {
        this.csvOutputFile = csvOutputFile;
    }

    @Override
    public void onStart() {
        try {
            bufferedWriter =
                    Files.newBufferedWriter(csvOutputFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            LOG.error("cannot open csv output", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onEvent(Collection<? extends Event> batch) {
        for (var response : batch) {
            var row =
                    switch (response) {
                        case ProtocolResponse.Error e -> toRow(e, "KO");
                        case ProtocolResponse.Response<?> e -> toRow(e, "OK");
                        case UserEvent.Enter e -> "USER,%d,ENTER".formatted(e.timestamp());
                        case UserEvent.Exit e -> "USER,%d,EXIT".formatted(e.timestamp());
                        default -> throw new IllegalStateException("Unexpected value: " + response);
                    };
            try {
                bufferedWriter.write(row);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            } catch (IOException e) {
                LOG.error("cannot write csv output", e);
                throw new RuntimeException(e);
            }
        }
    }

    private String toRow(ProtocolResponse<?> response, String status) {
        return "REQ,%d,%d,%d,%d,%s"
                .formatted(
                        response.scheduledStartTime(),
                        response.timestamp(),
                        response.stopTime(),
                        response.latency(),
                        status);
    }

    @Override
    public void onStop() {
        try {
            bufferedWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SamplesReader samplesReader() {
        return new CsvOutputSamplesReader(csvOutputFile);
    }
}
