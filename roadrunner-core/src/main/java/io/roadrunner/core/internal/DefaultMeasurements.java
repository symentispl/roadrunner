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

import io.roadrunner.api.Measurements;
import io.roadrunner.shaded.hdrhistogram.ConcurrentHistogram;

final class DefaultMeasurements {

    static Measurements create(ConcurrentHistogram histogram) {
        long totalCount = histogram.getTotalCount();
        double mean = histogram.getMean();
        long maxValue = histogram.getMaxValue();
        long minValue = histogram.getMinValue();
        double p50 = histogram.getValueAtPercentile(50.0);
        double p90 = histogram.getValueAtPercentile(90.0);
        double p99 = histogram.getValueAtPercentile(99.0);
        double p999 = histogram.getValueAtPercentile(99.9);
        return new Measurements(totalCount, mean, maxValue, minValue, p50, p90, p99, p999);
    }
}
