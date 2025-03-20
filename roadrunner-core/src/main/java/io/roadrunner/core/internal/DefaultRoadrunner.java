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

import io.roadrunner.api.Roadrunner;
import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.EventListener;
import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.api.measurments.MeasurementProgress;
import io.roadrunner.api.measurments.Measurements;
import io.roadrunner.api.protocol.Protocol;
import io.roadrunner.output.csv.CsvOutputEventListener;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultRoadrunner implements Roadrunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRoadrunner.class);

    private final int concurrentUsers;
    private final long requests;
    private final MeasurementProgress measurementProgress;
    private final Path outputDir;

    public DefaultRoadrunner(
            int concurrentUsers,
            long requests,
            MeasurementProgress measurementProgress,
            // we should inject listener instance here
            Path outputDir) {
        this.concurrentUsers = concurrentUsers;
        this.requests = requests;
        this.measurementProgress = measurementProgress;
        this.outputDir = outputDir;
    }

    @Override
    public Measurements execute(Supplier<Protocol> requestsFactory) {

        LOG.info("Roadrunner started: {} concurrent users, {} total number of requests", concurrentUsers, requests);
        var currentTimeMillis = System.currentTimeMillis();
        var delayedSupplier = new DelayedSupplier<>(requestsFactory, () -> 20L);

        var csvOutputFile = outputDir.resolve("output.csv");
        LOG.info("writing responses to {}", csvOutputFile);

        try (var responsesJournal = new QueueingProtocolResponsesJournal(
                new ProgressTrackingResponseListener(new CsvOutputEventListener(csvOutputFile), measurementProgress))) {
            responsesJournal.start();
            try (var usersExecutor = Executors.newThreadPerTaskExecutor(
                            Thread.ofVirtual().name("roadrunner-users-").factory());
                    var requestsExecutor = Executors.newCachedThreadPool(
                            Thread.ofVirtual().name("roadrunner-requests-").factory())) {
                var latch = new CountDownLatch(concurrentUsers);
                var measurementControl = new MeasurementControl(requests, responsesJournal, latch);
                for (int i = 0; i < concurrentUsers; i++) {
                    usersExecutor.submit(
                            new RoadrunnerUser(measurementControl, delayedSupplier.get(), requestsExecutor));
                }

                try {
                    latch.await();
                    LOG.info("Roadrunner stopped, time {}ms", System.currentTimeMillis() - currentTimeMillis);
                    usersExecutor.shutdown();
                    usersExecutor.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return DefaultMeasurements.from(responsesJournal.measurementsReader());
        }
    }

    @Override
    public void close() {}

    private static class RoadrunnerUser implements Runnable {
        private final MeasurementControl measurementControl;
        private final Protocol protocol;
        private final ExecutorService requestsExecutor;

        private RoadrunnerUser(
                MeasurementControl measurementControl, Protocol protocol, ExecutorService requestsExecutor) {
            this.measurementControl = measurementControl;
            this.protocol = protocol;
            this.requestsExecutor = requestsExecutor;
        }

        @Override
        public void run() {
            measurementControl.userEnters();
            try {
                while (measurementControl.isRunning()) {
                    try {
                        // when the next request should start
                        long scheduledStartTime = System.nanoTime();
                        // execute the request
                        var response =
                                requestsExecutor.submit(protocol::execute).get();
                        // calculate delay from intended start time
                        var inQueueTime = response.timestamp() - scheduledStartTime;
                        // calculate the service time (actual execution time)
                        var serviceTime = response.stopTime() - response.timestamp();

                        // create a corrected response latency that accounts for coordinated omission
                        // by adding the delay to the latency
                        var correctedLatency = serviceTime + inQueueTime;
                        measurementControl.request(response.withScheduledStartTime(scheduledStartTime)
                                .withLatency(correctedLatency));

                        // Calculate when the next request should start
                        // This assumes a closed-world model where we want to maintain a constant rate
                        // NOTICE: are we accumulating delay over time?
                        // scheduledStartTime = scheduledStartTime + serviceTime;
                    } catch (InterruptedException | ExecutionException e) {
                        System.out.println("<<>>");
                    }
                }
            } finally {
                measurementControl.userExits();
            }
        }
    }

    private static class MeasurementControl {

        private final AtomicLong counter;
        private final QueueingProtocolResponsesJournal responsesJournal;
        private final CountDownLatch latch;

        MeasurementControl(long requests, QueueingProtocolResponsesJournal responsesJournal, CountDownLatch latch) {
            this.counter = new AtomicLong(requests);
            this.responsesJournal = responsesJournal;
            this.latch = latch;
        }

        boolean isRunning() {
            return counter.get() > 0;
        }

        public void userEnters() {
            responsesJournal.userEnters(UserEvent.enter());
        }

        public void request(ProtocolResponse response) {
            counter.decrementAndGet();
            responsesJournal.response(response);
        }

        public void userExits() {
            responsesJournal.userExits(UserEvent.exit());
            latch.countDown();
        }
    }

    /**
     * A decorator for ProtocolResponseListener that tracks progress
     */
    private static class ProgressTrackingResponseListener implements EventListener {
        private final EventListener delegate;
        private final MeasurementProgress measurementProgress;
        private final AtomicLong processedRequests = new AtomicLong(0);

        ProgressTrackingResponseListener(EventListener delegate, MeasurementProgress measurementProgress) {
            this.delegate = delegate;
            this.measurementProgress = measurementProgress;
        }

        @Override
        public void onStart() {
            delegate.onStart();
        }

        @Override
        public void onEvent(Collection<? extends Event> batch) {
            delegate.onEvent(batch);
            // Update progress based on batch size
            var currentProcessed = processedRequests.addAndGet(
                    batch.stream().filter(ProtocolResponse.class::isInstance).count());
            measurementProgress.update(currentProcessed);
        }

        @Override
        public void onStop() {
            delegate.onStop();
        }

        @Override
        public EventReader samplesReader() {
            return delegate.samplesReader();
        }
    }

    private static class NoopEventListener implements EventListener {
        @Override
        public void onStart() {}

        @Override
        public void onEvent(Collection<? extends Event> batch) {}

        @Override
        public void onStop() {}

        @Override
        public EventReader samplesReader() {
            return () -> new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public Event next() {
                    throw new NoSuchElementException();
                }
            };
        }
    }
}
