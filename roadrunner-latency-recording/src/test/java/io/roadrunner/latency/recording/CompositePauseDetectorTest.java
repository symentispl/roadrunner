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
package io.roadrunner.latency.recording;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.latency.PauseDetector;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class CompositePauseDetectorTest {

    @Test
    void forwardsPauseEventsFromAllUnderlyingDetectors() throws Exception {
        var detectorA = new TestDetector();
        var detectorB = new TestDetector();

        var composite = new CompositePauseDetector(List.of(detectorA, detectorB));

        var received = new CopyOnWriteArrayList<long[]>();
        composite.addListener((pauseLength, pauseEndTime) -> received.add(new long[] {pauseLength, pauseEndTime}));

        detectorA.emit(100L, 1_000L);
        detectorB.emit(200L, 2_000L);

        // PauseDetector dispatches asynchronously; wait briefly for both events to flow.
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (received.size() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertThat(received).hasSize(2);
        assertThat(received).anyMatch(p -> p[0] == 100L && p[1] == 1_000L);
        assertThat(received).anyMatch(p -> p[0] == 200L && p[1] == 2_000L);

        composite.shutdown();
    }

    @Test
    void shutdownTearsDownEveryUnderlyingDetector() {
        var detectorA = new TestDetector();
        var detectorB = new TestDetector();
        var composite = new CompositePauseDetector(List.of(detectorA, detectorB));

        composite.shutdown();

        assertThat(detectorA.isShutdown()).isTrue();
        assertThat(detectorB.isShutdown()).isTrue();
    }

    /** A bare-bones PauseDetector that exposes notifyListeners for the test. */
    private static final class TestDetector extends PauseDetector {
        private volatile boolean shutdown;

        void emit(long pauseLength, long pauseEndTime) {
            notifyListeners(pauseLength, pauseEndTime);
        }

        boolean isShutdown() {
            return shutdown;
        }

        @Override
        public void shutdown() {
            shutdown = true;
            super.shutdown();
        }
    }
}
