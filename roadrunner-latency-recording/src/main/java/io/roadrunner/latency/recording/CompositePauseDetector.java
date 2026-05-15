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

import io.roadrunner.latency.PauseDetector;
import io.roadrunner.latency.PauseDetectorListener;
import java.util.List;

/**
 * Aggregates several {@link PauseDetector}s into one. {@link io.roadrunner.latency.LatencyStats}
 * takes a single {@code PauseDetector} in its constructor, so we use this composite to
 * fan in events from multiple detectors.
 */
final class CompositePauseDetector extends PauseDetector {

    private final List<PauseDetector> underlying;
    private final PauseDetectorListener forwarding = this::notifyListeners;

    CompositePauseDetector(List<PauseDetector> underlying) {
        this.underlying = List.copyOf(underlying);
        for (PauseDetector detector : this.underlying) {
            detector.addListener(forwarding, true /* high priority */);
        }
    }

    @Override
    public void shutdown() {
        for (PauseDetector detector : underlying) {
            detector.removeListener(forwarding);
            detector.shutdown();
        }
        super.shutdown();
    }
}
