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
import io.roadrunner.core.internal.ClosedWorldStrategy;
import io.roadrunner.core.internal.DefaultRoadrunner;
import io.roadrunner.core.internal.ExecutionStrategy;
import io.roadrunner.core.internal.OpenWorldStrategy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(Bootstrap.class);

    private ExecutionStrategy strategy;
    private MeasurementProgress measurementProgress = MeasurementProgress.NO_OP;
    private Path outputDir;

    /**
     * Configure the closed-world load model: N concurrent users each loop until the total
     * request count is reached.
     */
    public Bootstrap withClosedWorldModel(int concurrentUsers, long requests) {
        this.strategy = ClosedWorldStrategy.of(concurrentUsers, requests);
        return this;
    }

    /**
     * Configure the open-world load model: requests arrive at a fixed rate for the given
     * duration, independent of whether previous requests have completed.
     */
    public Bootstrap withOpenWorldModel(int usersArrivalRate, Duration duration) {
        this.strategy = OpenWorldStrategy.of(usersArrivalRate, duration);
        return this;
    }

    public Bootstrap withMeasurementProgress(MeasurementProgress measurementProgress) {
        this.measurementProgress = measurementProgress;
        return this;
    }

    public Bootstrap withOutputDir(Path outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    public Path outputDir() {
        return outputDir;
    }

    public Roadrunner build() throws IOException {
        if (outputDir == null) {
            outputDir = Files.createTempDirectory(Paths.get("."), "roadrunner-");
            LOG.warn("setting output directory to {}", outputDir);
        }
        if (strategy == null) {
            throw new IllegalStateException("Load strategy must be configured");
        }
        return new DefaultRoadrunner(strategy, measurementProgress, outputDir);
    }
}
