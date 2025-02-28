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
package io.roadrunner.core;

import io.roadrunner.api.Roadrunner;
import io.roadrunner.api.measurments.MeasurementProgress;
import io.roadrunner.core.internal.DefaultRoadrunner;

public class Bootstrap {
    private int concurrency;
    private int requests;
    private MeasurementProgress measurementProgress = MeasurementProgress.NO_OP;

    public Bootstrap withConcurrency(int concurrency) {
        this.concurrency = concurrency;
        return this;
    }

    public Bootstrap withRequests(int requests) {
        this.requests = requests;
        return this;
    }

    public Bootstrap withMeasurementProgress(MeasurementProgress measurementProgress) {
        this.measurementProgress = measurementProgress;
        return this;
    }

    public Roadrunner build() {
        return new DefaultRoadrunner(concurrency, requests, measurementProgress);
    }
}
