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

import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.EventListener;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.api.measurments.MeasurementProgress;
import io.roadrunner.api.measurments.ProgressSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProgressTrackingResponseListenerTest {

    @Test
    void countsProcessedAndErrors() {
        var captured = new AtomicReference<ProgressSnapshot>();
        MeasurementProgress progress = captured::set;
        var noop = new EventListener() {
            @Override
            public void onStart() {}

            @Override
            public void onEvent(Collection<? extends Event> batch) {}

            @Override
            public void onStop() {}

            @Override
            public EventReader samplesReader() {
                return null;
            }
        };
        var listener = DefaultRoadrunner.newProgressListener(noop, progress);

        listener.onEvent(List.of(
                new SamplerResponse.Response(0L, 1_000_000L), new SamplerResponse.Error(0L, 2_000_000L)));

        assertThat(captured.get().processed()).isEqualTo(2L);
        assertThat(captured.get().errors()).isEqualTo(1L);
    }
}
