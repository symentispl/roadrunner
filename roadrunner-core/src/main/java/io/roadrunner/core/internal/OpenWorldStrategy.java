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

import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.protocol.Protocol;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpenWorldStrategy implements ExecutionStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(OpenWorldStrategy.class);

    private final int requestsPerSecond;
    private final Duration testDuration;

    public OpenWorldStrategy(int requestsPerSecond, Duration testDuration) {
        this.requestsPerSecond = requestsPerSecond;
        this.testDuration = testDuration;
    }

    @Override
    public void execute(Supplier<Protocol> protocolFactory, QueueingProtocolResponsesJournal journal)
            throws InterruptedException {
        long intervalNanos = 1_000_000_000L / requestsPerSecond;
        long durationNanos = testDuration.toNanos();

        LOG.info(
                "Roadrunner open-world started: {} requests/sec, duration {}",
                requestsPerSecond,
                testDuration);

        var protocol = protocolFactory.get();
        journal.userEnters(UserEvent.enter());

        var requestsExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("roadrunner-requests-").factory());

        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + durationNanos;
        // Track the next arrival as an absolute nanos value to avoid drift accumulation
        long nextScheduledStartTime = startNanos;

        try {
            while (true) {
                nextScheduledStartTime += intervalNanos;
                if (nextScheduledStartTime >= deadlineNanos) {
                    break;
                }
                long now = System.nanoTime();
                long waitNanos = nextScheduledStartTime - now;
                if (waitNanos > 0) {
                    LockSupport.parkNanos(waitNanos);
                }
                long scheduledStartTime = nextScheduledStartTime;
                requestsExecutor.submit(() -> {
                    try {
                        var response = protocol.execute();
                        var inQueueTime = response.timestamp() - scheduledStartTime;
                        var serviceTime = response.stopTime() - response.timestamp();
                        var correctedLatency = serviceTime + inQueueTime;
                        journal.response(response.withScheduledStartTime(scheduledStartTime)
                                .withLatency(correctedLatency));
                    } catch (Exception e) {
                        LOG.error("Error executing protocol request", e);
                    }
                });
            }
        } finally {
            requestsExecutor.shutdown();
            requestsExecutor.awaitTermination(30, TimeUnit.SECONDS);
            journal.userExits(UserEvent.exit());
        }

        LOG.info("Roadrunner open-world stopped");
    }
}
