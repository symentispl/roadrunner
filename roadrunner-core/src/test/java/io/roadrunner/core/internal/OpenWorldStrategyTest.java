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
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.EventListener;
import io.roadrunner.api.events.ProtocolResponse;
import io.roadrunner.api.events.UserEvent;
import io.roadrunner.api.measurments.EventReader;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class OpenWorldStrategyTest {

    @Test
    void fireRequestsAtTargetRate() throws InterruptedException {
        var listener = new CollectionEventListener();

        try (var journal = new QueueingProtocolResponsesJournal(listener)) {
            journal.start();
            var strategy = new OpenWorldStrategy(5, Duration.ofSeconds(2));
            strategy.execute(
                    () -> () -> {
                        var start = System.nanoTime();
                        var stop = System.nanoTime();
                        return ProtocolResponse.empty(start, stop);
                    },
                    journal);
        }

        assertThat(listener.events)
                .first(type(UserEvent.Enter.class))
                .satisfies(e -> assertThat(e.timestamp()).isGreaterThan(0));

        assertThat(listener.events)
                .last(type(UserEvent.Exit.class))
                .satisfies(e -> assertThat(e.timestamp()).isGreaterThan(0));

        // 5 rps * 2s = 10 expected requests; allow ±4 tolerance for scheduling jitter
        assertThat(listener.events)
                .filteredOn(ProtocolResponse.class::isInstance)
                .asInstanceOf(collection(ProtocolResponse.class))
                .hasSizeBetween(6, 14)
                .allSatisfy(r -> {
                    assertThat(r.scheduledStartTime()).isGreaterThan(0);
                    assertThat(r.timestamp()).isGreaterThanOrEqualTo(r.scheduledStartTime());
                    assertThat(r.latency()).isGreaterThanOrEqualTo(0);
                });
    }

    private static class CollectionEventListener implements EventListener {
        final List<Event> events = new CopyOnWriteArrayList<>();

        @Override
        public void onStart() {}

        @Override
        public void onEvent(Collection<? extends Event> batch) {
            events.addAll(batch);
        }

        @Override
        public void onStop() {}

        @Override
        public EventReader samplesReader() {
            return () -> events.stream().iterator();
        }
    }
}
