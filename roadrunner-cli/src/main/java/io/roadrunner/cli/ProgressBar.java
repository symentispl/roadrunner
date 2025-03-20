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

final class ProgressBar implements MeasurementProgress {
    private static final char EMPTY = '░';
    private static final char FULL = '█';

    private final int progressBarSize;
    private final long startPosition;
    private final long finishPosition;
    private final Console console;

    ProgressBar(int progressBarSize, long startPosition, long finishPosition) {
        this.progressBarSize = progressBarSize;
        this.startPosition = startPosition;
        this.finishPosition = finishPosition;
        this.console = System.console();
    }

    @Override
    public void update(long currentPosition) {
        if (console != null) {
            var status = (int) (100 * (currentPosition - startPosition) / (finishPosition - startPosition));
            var move = Math.min((progressBarSize * status) / 100, progressBarSize);

            System.out.print(new StringBuilder(progressBarSize + 20)
                    .append("\r")
                    .append('[')
                    .repeat(FULL, move)
                    .append(status)
                    .append('%')
                    .repeat(EMPTY, progressBarSize - move)
                    .append("] ")
                    .append(currentPosition)
                    .append('/')
                    .append(finishPosition));
        }
    }
}
