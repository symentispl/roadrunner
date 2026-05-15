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

import io.roadrunner.api.latency.LatencyRecorder;
import io.roadrunner.latency.PauseDetector;
import io.roadrunner.latency.SimplePauseDetector;
import io.roadrunner.latency.VirtualThreadSchedulingDetector;
import java.util.ArrayList;
import java.util.EnumSet;

/**
 * Builds {@link LatencyRecorder} instances configured with the requested pause detectors.
 */
public final class LatencyRecorders {

    private LatencyRecorders() {}

    /**
     * @param kinds which pause detectors to wire in. An empty set returns {@link LatencyRecorder#noop()}.
     */
    public static LatencyRecorder create(EnumSet<PauseDetectorKind> kinds) {
        if (kinds.isEmpty()) {
            return LatencyRecorder.noop();
        }

        var detectors = new ArrayList<PauseDetector>();
        for (PauseDetectorKind kind : kinds) {
            detectors.add(
                    switch (kind) {
                        case VT_SCHEDULING -> new VirtualThreadSchedulingDetector();
                        case JVM_PAUSE -> new SimplePauseDetector();
                    });
        }

        PauseDetector pauseDetector = detectors.size() == 1 ? detectors.get(0) : new CompositePauseDetector(detectors);
        return new LatencyStatsRecorder(pauseDetector);
    }
}
