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
import io.roadrunner.api.events.MeasurementError;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.logging.LoggingFacade;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.slf4j.Logger;

final class QueueingSamplerResponsesJournal implements ResponsesJournal {

    private static final Logger LOG = LoggingFacade.getLogger(QueueingSamplerResponsesJournal.class);

    private static final int BATCH_SIZE = 1000;
    private static final int CHUNK_SIZE = 1024;
    private static final long IDLE_PARK_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    // many sampler threads produce, the single journal thread consumes
    private final MpscUnboundedArrayQueue<Event> responses = new MpscUnboundedArrayQueue<>(CHUNK_SIZE);
    private final EventListener listener;
    private final ExecutorService executorService;
    private volatile boolean isRunning;

    QueueingSamplerResponsesJournal(EventListener listener) {
        this.listener = listener;
        this.executorService = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("responses-journal-").factory());
    }

    @Override
    public void start() {
        LOG.debug("starting responses journaling");
        isRunning = true;
        executorService.submit(() -> {
            listener.onStart();
            var batch = new ArrayList<Event>(BATCH_SIZE + 1);
            while (isRunning) {
                if (responses.drain(batch::add, BATCH_SIZE) > 0) {
                    try {
                        listener.onEvent(batch);
                    } finally {
                        batch.clear();
                    }
                } else {
                    LockSupport.parkNanos(IDLE_PARK_NANOS);
                }
            }
            // Drain any events that arrived after the last poll before stopping
            while (responses.drain(batch::add) > 0) {
                listener.onEvent(batch);
                batch.clear();
            }
            listener.onStop();
        });
    }

    @Override
    public void userEnters(UserEvent.Enter event) {
        responses.offer(event);
    }

    @Override
    public void response(SamplerResponse<?> response) {
        responses.offer(response);
    }

    @Override
    public void error(Exception e) {
        responses.offer(new MeasurementError(System.nanoTime(), e));
    }

    @Override
    public void userExits(UserEvent.Exit event) {
        responses.offer(event);
    }

    @Override
    public void close() {
        isRunning = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                LOG.warn("responses journal thread did not terminate within timeout, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOG.warn("interrupted while waiting for responses journal thread to terminate, forcing shutdown");
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }

    @Override
    public EventReader measurementsReader() {
        return listener.samplesReader();
    }
}
