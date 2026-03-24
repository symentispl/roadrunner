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
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpenWorldStrategy implements ExecutionStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(OpenWorldStrategy.class);

    private final int usersArrivalRate;
    private final Duration testDuration;

    public static OpenWorldStrategy of(int usersArrivalRate, Duration testDuration) {
        if (usersArrivalRate <= 0) {
            throw new IllegalArgumentException("usersArrivalRate must be positive: %d".formatted(usersArrivalRate));
        }
        if (testDuration.isNegative() || testDuration.isZero()) {
            throw new IllegalArgumentException("testDuration must be positive: %s".formatted(testDuration));
        }
        return new OpenWorldStrategy(usersArrivalRate, testDuration);
    }

    private OpenWorldStrategy(int usersArrivalRate, Duration testDuration) {
        this.usersArrivalRate = usersArrivalRate;
        this.testDuration = testDuration;
    }

    @Override
    public void execute(Supplier<Protocol> protocolFactory, QueueingProtocolResponsesJournal journal)
            throws InterruptedException {
        long intervalNanos = 1_000_000_000L / usersArrivalRate;
        if (intervalNanos <= 0) {
            throw new IllegalArgumentException("usersArrivalRate is too high, request interval is zero");
        }
        long durationNanos = testDuration.toNanos();

        LOG.info("Roadrunner open-world started: {} users/sec, duration {}", usersArrivalRate, testDuration);

        var requestsExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("roadrunner-users-").factory());

        var startNanos = System.nanoTime();
        var deadlineNanos = startNanos + durationNanos;
        // Track the next arrival as an absolute nanos value to avoid drift accumulation
        var nextScheduledStartTime = startNanos;

        // Phaser tracks in-flight users; registered parties = concurrent users in the system.
        // Phaser supports at most 65535 simultaneous parties, which bounds max concurrency, not
        // total request count.
        var phaser = new Phaser(1);

        try {
            while (true) {
                nextScheduledStartTime += intervalNanos;
                if (nextScheduledStartTime >= deadlineNanos) {
                    break;
                }
                var now = System.nanoTime();
                var waitNanos = nextScheduledStartTime - now;
                while (waitNanos > 0) {
                    LockSupport.parkNanos(waitNanos);
                    // handle possible spurious wake-ups
                    waitNanos = nextScheduledStartTime - System.nanoTime();
                }
                var scheduledStartTime = nextScheduledStartTime;
                phaser.register();
                requestsExecutor.submit(new RoadrunnerUser(journal, protocolFactory.get(), scheduledStartTime, phaser));
            }
        } finally {
            // Deregister the main party; when the last in-flight user also deregisters the phaser
            // terminates and awaitAdvance returns, giving us a precise "all users have left"
            // barrier without an arbitrary timeout.
            int phase = phaser.arriveAndDeregister();
            phaser.awaitAdvance(phase);
            requestsExecutor.shutdown();
            requestsExecutor.awaitTermination(1, TimeUnit.SECONDS);
        }

        LOG.info("Roadrunner open-world stopped");
    }

    private static class RoadrunnerUser implements Runnable {
        private final QueueingProtocolResponsesJournal journal;
        private final Protocol protocol;
        private final long scheduledStartTime;
        private final Phaser phaser;

        public RoadrunnerUser(
                QueueingProtocolResponsesJournal journal, Protocol protocol, long scheduledStartTime, Phaser phaser) {
            this.journal = journal;
            this.protocol = protocol;
            this.scheduledStartTime = scheduledStartTime;
            this.phaser = phaser;
        }

        @Override
        public void run() {
            try {
                journal.userEnters(UserEvent.enter());
                var response = protocol.execute();
                var inQueueTime = response.timestamp() - scheduledStartTime;
                var serviceTime = response.stopTime() - response.timestamp();
                var correctedLatency = serviceTime + inQueueTime;
                journal.response(
                        response.withScheduledStartTime(scheduledStartTime).withLatency(correctedLatency));
                journal.userExits(UserEvent.exit());
            } catch (Exception e) {
                journal.error(e);
            } finally {
                phaser.arriveAndDeregister();
            }
        }
    }
}
