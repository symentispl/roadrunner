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
package io.roadrunner.core.internal;

import io.roadrunner.api.ProtocolResponseListener;
import io.roadrunner.api.protocol.Error;
import io.roadrunner.api.protocol.ProtocolResponse;
import io.roadrunner.api.protocol.Response;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CsvOutputProtocolResponseListener implements ProtocolResponseListener {

    private static final Logger LOG = LoggerFactory.getLogger(CsvOutputProtocolResponseListener.class);

    private BufferedWriter bufferedWriter;

    @Override
    public void onStart() {
        try {
            bufferedWriter = Files.newBufferedWriter(
                    Paths.get("output.csv"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            LOG.error("cannot open csv output", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onResponses(Collection<? extends ProtocolResponse> batch) {
        for (ProtocolResponse response : batch) {
            var s =
                    switch (response) {
                        case Error r -> "%d,%d,KO".formatted(r.startTime(), r.stopTime());
                        case Response<?> r -> "%d,%d,OK".formatted(r.startTime(), r.stopTime());
                        default -> throw new IllegalStateException("Unexpected value: " + response);
                    };
            try {
                bufferedWriter.write(s);
                bufferedWriter.newLine();
            } catch (IOException e) {
                LOG.error("cannot write csv output", e);
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onStop() {
        try {
            bufferedWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
