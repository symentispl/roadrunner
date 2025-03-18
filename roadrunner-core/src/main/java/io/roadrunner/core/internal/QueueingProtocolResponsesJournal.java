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
import io.roadrunner.api.measurments.MeasurementsReader;
import io.roadrunner.api.protocol.ProtocolResponse;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class QueueingProtocolResponsesJournal implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(QueueingProtocolResponsesJournal.class);

    private static final int BATCH_SIZE = 1000;

    private final BlockingQueue<ProtocolResponse> responses = new LinkedBlockingQueue<>();
    private final ProtocolResponseListener listener;
    private final ExecutorService executorService;
    private volatile boolean isRunning;

    QueueingProtocolResponsesJournal(ProtocolResponseListener listener) {
        this.listener = listener;
        this.executorService = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("responses-journal-").factory());
    }

    void start() {
        LOG.debug("starting responses journaling");
        isRunning = true;
        executorService.submit(() -> {
            listener.onStart();
            var batch = new ArrayList<ProtocolResponse>(BATCH_SIZE + 1);
            while (isRunning) {
                try {
                    var response = responses.poll(1, TimeUnit.MILLISECONDS);
                    if (response != null) {
                        batch.add(response);
                        responses.drainTo(batch, BATCH_SIZE);
                        try {
                            listener.onResponses(batch);
                        } finally {
                            batch.clear();
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            listener.onStop();
        });
    }

    public void append(ProtocolResponse response) {
        responses.offer(response);
    }

    @Override
    public void close() {
        isRunning = false;
        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.SECONDS);

            // TODO drain remaining items

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public MeasurementsReader measurementsReader() {
        return listener.measurementsReader();
    }
}
