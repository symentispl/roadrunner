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
import io.roadrunner.api.measurments.ProgressSnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

final class TimeBasedProgressBar implements MeasurementProgress {

    private final long totalDurationNanos;
    private final Terminal terminal;
    private final boolean unicode;
    private final boolean live;
    private int lastLines = 0;

    TimeBasedProgressBar(Duration duration) {
        this.totalDurationNanos = duration.toNanos();
        this.terminal = buildTerminal();
        var type = terminal.getType() == null ? "" : terminal.getType();
        this.unicode = !type.contains("dumb");
        // ponytail: mirror the old System.console() != null guard - stay silent when output isn't a real tty
        this.live = System.console() != null;
    }

    private static Terminal buildTerminal() {
        try {
            return TerminalBuilder.builder().build();
        } catch (IOException e) {
            // ponytail: no real tty available (e.g. CI) -> degrade to a dumb terminal instead of failing the run
            try {
                return TerminalBuilder.builder().dumb(true).build();
            } catch (IOException inner) {
                throw new UncheckedIOException(inner);
            }
        }
    }

    @Override
    public void update(ProgressSnapshot snapshot) {
        if (!live) {
            return;
        }
        var fraction = Math.max(0.0, Math.min(1.0, snapshot.elapsedNanos() / (double) totalDurationNanos));
        var panel = LiveProgressRenderer.render(fraction, snapshot, unicode);
        var writer = terminal.writer();
        if (unicode) {
            // ansi-capable: move up over the previous panel and clear each line before redrawing
            if (lastLines > 0) {
                writer.print("\r\033[" + lastLines + "A");
            }
            for (var line : panel.split("\n")) {
                writer.print("\033[2K");
                writer.println(line);
            }
            lastLines = panel.split("\n").length;
        } else {
            // ponytail: dumb terminal -> collapse to one plain overwritten line, no ANSI
            writer.print("\r" + panel.lines().findFirst().orElse(panel));
        }
        writer.flush();
    }
}
