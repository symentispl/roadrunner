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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.EventListener;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.measurments.EventReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QueueingSamplerResponsesJournalTest {

    @Test
    void drainResponseJournal() {
        var listener = new CollectionEventListener();
        var response1 = new SamplerResponse.Response(1, 10);
        var response2 = new SamplerResponse.Response(2, 20);
        var response3 = new SamplerResponse.Response(3, 30);
        var response4 = new SamplerResponse.Response(4, 40);
        var response5 = new SamplerResponse.Response(5, 50);

        try (var journal = new QueueingSamplerResponsesJournal(listener)) {
            journal.start();
            journal.response(response1);
            journal.response(response2);
            journal.response(response3);
            journal.response(response4);
            journal.response(response5);
        }

        // Wait for responses to be processed before closing
        await().atMost(Duration.ofSeconds(5)).until(() -> listener.responses.size() >= 5);

        // Verify all responses were processed correctly
        assertThat(listener.responses).containsExactly(response1, response2, response3, response4, response5);
    }

    @Test
    void drainsEntireShutdownBacklogBeforeStopping() throws Exception {
        int producerCount = 4;
        int responsesPerProducer = 1500;
        var firstBatchStarted = new CountDownLatch(1);
        var allowFirstBatch = new CountDownLatch(1);
        var stopCalled = new CountDownLatch(1);
        var pauseFirstBatch = new AtomicBoolean(true);
        var responseCountAtStop = new AtomicInteger(-1);
        var listener = new CollectionEventListener() {
            @Override
            public void onEvent(Collection<? extends Event> batch) {
                if (pauseFirstBatch.compareAndSet(true, false)) {
                    firstBatchStarted.countDown();
                    await(allowFirstBatch);
                }
                super.onEvent(batch);
            }

            @Override
            public void onStop() {
                responseCountAtStop.set(responses.size());
                stopCalled.countDown();
            }
        };
        var journal = new QueueingSamplerResponsesJournal(listener);
        var initialResponse = new SamplerResponse.Response(0, 1);
        Collection<Event> expectedResponses = new ConcurrentLinkedQueue<>();
        expectedResponses.add(initialResponse);
        var producers = Executors.newFixedThreadPool(producerCount);
        var startProducers = new CountDownLatch(1);
        try {
            journal.response(initialResponse);
            journal.start();
            assertThat(firstBatchStarted.await(5, TimeUnit.SECONDS)).isTrue();
            List<Future<?>> producerTasks = new ArrayList<>();
            for (int producer = 0; producer < producerCount; producer++) {
                int producerId = producer;
                producerTasks.add(producers.submit(() -> {
                    await(startProducers);
                    for (int i = 0; i < responsesPerProducer; i++) {
                        var response = new SamplerResponse.Response(producerId * responsesPerProducer + i + 1, 1);
                        expectedResponses.add(response);
                        journal.response(response);
                    }
                }));
            }
            startProducers.countDown();
            producers.shutdown();
            assertThat(producers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            for (Future<?> task : producerTasks) {
                task.get();
            }

            var closer = Thread.ofPlatform().start(journal::close);
            await().atMost(Duration.ofSeconds(5)).until(() -> closer.getState() == Thread.State.TIMED_WAITING);
            allowFirstBatch.countDown();
            closer.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(closer.isAlive()).isFalse();
            assertThat(stopCalled.getCount()).isZero();
            assertThat(responseCountAtStop).hasValue(1 + producerCount * responsesPerProducer);
            assertThat(listener.responses).hasSize(1 + producerCount * responsesPerProducer);
            assertThat(listener.responses).containsExactlyInAnyOrderElementsOf(expectedResponses);
        } finally {
            allowFirstBatch.countDown();
            startProducers.countDown();
            producers.shutdownNow();
            journal.close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static class CollectionEventListener implements EventListener {

        // Using thread-safe collection since events might be added from different threads
        Collection<Event> responses = new ConcurrentLinkedQueue<>();

        @Override
        public void onStart() {}

        @Override
        public void onEvent(Collection<? extends Event> batch) {
            responses.addAll(batch);
        }

        @Override
        public void onStop() {}

        @Override
        public EventReader samplesReader() {
            return () -> responses.stream().iterator();
        }
    }
}
