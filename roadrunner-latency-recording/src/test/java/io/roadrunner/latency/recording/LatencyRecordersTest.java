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

import io.roadrunner.api.latency.LatencyRecorder;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class LatencyRecordersTest {

    @Test
    void emptyKindsReturnsNoopRecorder() {
        var recorder = LatencyRecorders.create(EnumSet.noneOf(PauseDetectorKind.class));
        assertThat(recorder).isSameAs(LatencyRecorder.noop());
        recorder.close();
    }

    @Test
    void singleKindReturnsRealRecorder() {
        var recorder = LatencyRecorders.create(EnumSet.of(PauseDetectorKind.VT_SCHEDULING));
        try {
            assertThat(recorder).isNotSameAs(LatencyRecorder.noop());
            recorder.record(1_500_000L);
        } finally {
            recorder.close();
        }
    }

    @Test
    void bothKindsReturnsRealRecorderUsingCompositeDetector() {
        var recorder = LatencyRecorders.create(EnumSet.allOf(PauseDetectorKind.class));
        try {
            assertThat(recorder).isNotSameAs(LatencyRecorder.noop());
            recorder.record(2_500_000L);
        } finally {
            recorder.close();
        }
    }
}
