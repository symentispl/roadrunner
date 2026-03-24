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

import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.protocol.Protocol;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class ClosedWorldStrategy implements ExecutionStrategy {

    public static ExecutionStrategy of(int concurrentUsers, long requests) {
        if (concurrentUsers <= 0) {
            throw new IllegalArgumentException("concurrentUsers must be positive: %d".formatted(concurrentUsers));
        }
        if (requests <= 0) {
            throw new IllegalArgumentException("requests must be positive: %d".formatted(requests));
        }
        return new ClosedWorldStrategy(concurrentUsers, requests);
    }

    private final int concurrentUsers;
    private final long requests;

    private ClosedWorldStrategy(int concurrentUsers, long requests) {
        this.concurrentUsers = concurrentUsers;
        this.requests = requests;
    }

    @Override
    public void execute(Supplier<Protocol> protocolFactory, QueueingProtocolResponsesJournal journal)
            throws InterruptedException {
        var delayedSupplier = new DelayedSupplier<>(protocolFactory, () -> 20L);
        try (var usersExecutor = Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name("roadrunner-users-").factory());
                var requestsExecutor = Executors.newCachedThreadPool(
                        Thread.ofVirtual().name("roadrunner-requests-").factory())) {
            var latch = new CountDownLatch(concurrentUsers);
            var measurementControl = new MeasurementControl(requests, journal, latch);
            for (int i = 0; i < concurrentUsers; i++) {
                usersExecutor.submit(new RoadrunnerUser(measurementControl, delayedSupplier.get(), requestsExecutor));
            }
            latch.await();
            usersExecutor.shutdown();
            usersExecutor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

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
                        // calculate delay from the intended start time
                        var inQueueTime = response.timestamp() - scheduledStartTime;
                        // calculate the service time (actual execution time)
                        var serviceTime = response.stopTime() - response.timestamp();

                        // create a corrected response latency that accounts for coordinated omission
                        // by adding the delay to the latency
                        var correctedLatency = serviceTime + inQueueTime;
                        measurementControl.request(response.withScheduledStartTime(scheduledStartTime)
                                .withLatency(correctedLatency));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        measurementControl.error(e);
                        break;
                    } catch (ExecutionException e) {
                        measurementControl.error(e);
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

        public void error(Exception e) {
            responsesJournal.error(e);
        }
    }
}
