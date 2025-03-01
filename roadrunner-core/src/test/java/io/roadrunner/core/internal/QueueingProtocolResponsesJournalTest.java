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

import io.roadrunner.api.ProtocolResponseListener;
import io.roadrunner.api.measurments.Measurement;
import io.roadrunner.api.measurments.MeasurementsReader;
import io.roadrunner.api.protocol.ProtocolResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class QueueingProtocolResponsesJournalTest {

    @Test
    void drainResponseJournal() {
        var listener = new CollectionProtocolResponseListener();
        var journal = new QueueingProtocolResponsesJournal(listener);

        var response1 = ProtocolResponse.response(0, 0, "1");
        var response2 = ProtocolResponse.response(0, 0, "2");
        var response3 = ProtocolResponse.response(0, 0, "3");
        var response4 = ProtocolResponse.response(0, 0, "4");
        var response5 = ProtocolResponse.response(0, 0, "5");

        journal.start();
        journal.append(response1);
        journal.append(response2);
        journal.append(response3);
        journal.append(response4);
        journal.append(response5);

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(listener.responses).containsExactly(response1, response2, response3, response4, response5);
        });

        journal.close();
    }

    private static class CollectionProtocolResponseListener implements ProtocolResponseListener {

        List<ProtocolResponse> responses = new ArrayList<>();

        @Override
        public void onStart() {}

        @Override
        public void onResponses(Collection<? extends ProtocolResponse> batch) {
            responses.addAll(batch);
        }

        @Override
        public void onStop() {}

        @Override
        public MeasurementsReader measurementsReader() {
            return new MeasurementsReader() {
                @Override
                public Iterator<Measurement> iterator() {
                    return responses.stream()
                            .map(r -> new Measurement(r.startTime(), r.stopTime(), Measurement.Status.OK))
                            .iterator();
                }
            };
        }
    }
}
