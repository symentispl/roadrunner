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

import io.roadrunner.api.measurments.Measurements;
import io.roadrunner.api.measurments.MeasurementsReader;

final class DefaultMeasurements implements Measurements {
    private final MeasurementsReader measurementsReader;

    static Measurements from(MeasurementsReader measurementsReader) {
        return new DefaultMeasurements(measurementsReader);
    }

    private DefaultMeasurements(MeasurementsReader measurementsReader) {
        this.measurementsReader = measurementsReader;
    }

    @Override
    public MeasurementsReader measurementsReader() {
        return measurementsReader;
    }
}
