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

import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.EventListener;
import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.EventReader;

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

    private final BlockingQueue<Event> responses = new LinkedBlockingQueue<>();
    private final EventListener listener;
    private final ExecutorService executorService;
    private volatile boolean isRunning;

    QueueingProtocolResponsesJournal(EventListener listener) {
        this.listener = listener;
        this.executorService = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("responses-journal-").factory());
    }

    void start() {
        LOG.debug("starting responses journaling");
        isRunning = true;
        executorService.submit(() -> {
            listener.onStart();
            var batch = new ArrayList<Event>(BATCH_SIZE + 1);
            while (isRunning) {
                try {
                    var response = responses.poll(1, TimeUnit.MILLISECONDS);
                    if (response != null) {
                        batch.add(response);
                        responses.drainTo(batch, BATCH_SIZE);
                        try {
                            listener.onEvent(batch);
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

    public void userEnters(UserEvent.Enter event) {
        responses.offer(event);
    }

    public void response(ProtocolResponse response) {
        responses.offer(response);
    }

    public void userExits(UserEvent.Exit event) {
        responses.offer(event);
    }

    @Override
    public void close() {
        isRunning = false;
        executorService.shutdown();
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
            // Drain any remaining responses and notify listener
            if (!responses.isEmpty()) {
                var remainingEvents = new ArrayList<Event>();
                responses.drainTo(remainingEvents);
                if (!remainingEvents.isEmpty()) {
                    LOG.warn("Processing {} remaining events before closing", remainingEvents.size());
                    listener.onEvent(remainingEvents);
                }
            }
        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public EventReader measurementsReader() {
        return listener.samplesReader();
    }
}
