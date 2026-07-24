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

import io.roadrunner.api.measurments.ProgressSnapshot;
import io.roadrunner.console.Bars;

final class LiveProgressRenderer {

    private LiveProgressRenderer() {}

    static String render(double fraction, ProgressSnapshot s, boolean unicode) {
        var pct = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 100);
        var bar = Bars.horizontal(fraction, 24, unicode);
        var spark = Bars.sparkline(s.throughputSpark(), unicode);
        var errPct = s.processed() == 0 ? 0.0 : (double) s.errors() / s.processed() * 100;
        return String.format(
                "%s %d%%%n  %d reqs   %.0f req/s %s%n  errors %d (%.2f%%)",
                bar, pct, s.processed(), s.throughput(), spark, s.errors(), errPct);
    }
}
