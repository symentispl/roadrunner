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
package io.roadrunner.cli;

import io.roadrunner.api.measurments.MeasurementProgress;
import java.io.Console;
import java.time.Duration;

final class TimeBasedProgressBar implements MeasurementProgress {
    private static final char EMPTY = '░';
    private static final char FULL = '█';

    private final int progressBarSize;
    private final long totalDurationNanos;
    private final long startNanos;
    private final Console console;

    TimeBasedProgressBar(Duration duration) {
        this.progressBarSize = 100;
        this.totalDurationNanos = duration.toNanos();
        this.startNanos = System.nanoTime();
        this.console = System.console();
    }

    @Override
    public void update(long ignored) {
        if (console != null) {
            var elapsedNanos = System.nanoTime() - startNanos;
            var status = (int) Math.min(100, 100 * elapsedNanos / totalDurationNanos);
            var move = Math.min((progressBarSize * status) / 100, progressBarSize);

            System.out.print(new StringBuilder(progressBarSize + 20)
                    .append("\r")
                    .append('[')
                    .repeat(FULL, move)
                    .append(status)
                    .append('%')
                    .repeat(EMPTY, progressBarSize - move)
                    .append(']'));
        }
    }
}
