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

import io.roadrunner.api.ProtocolResponseListener;
import io.roadrunner.api.measurments.MeasurementsReader;
import io.roadrunner.api.protocol.Error;
import io.roadrunner.api.protocol.ProtocolResponse;
import io.roadrunner.api.protocol.Response;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvOutputProtocolResponseListener implements ProtocolResponseListener {

    private static final Logger LOG = LoggerFactory.getLogger(CsvOutputProtocolResponseListener.class);

    private final Path csvOutputFile;
    private BufferedWriter bufferedWriter;

    public CsvOutputProtocolResponseListener(Path csvOutputFile) {
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
    public void onResponses(Collection<? extends ProtocolResponse> batch) {
        for (ProtocolResponse<?> response : batch) {
            var row =
                    switch (response) {
                        case Error r -> toRow(r, "KO");
                        case Response<?> r -> toRow(r, "OK");
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
        return "%d,%d,%d,%d,%s"
                .formatted(
                        response.scheduledStartTime(),
                        response.startTime(),
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
    public MeasurementsReader measurementsReader() {
        return new CsvOutputMeasurementsReader(csvOutputFile);
    }
}
