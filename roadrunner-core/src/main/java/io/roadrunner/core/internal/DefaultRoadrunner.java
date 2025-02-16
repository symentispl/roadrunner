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

import io.roadrunner.api.MeasurementProgress;
import io.roadrunner.api.Measurements;
import io.roadrunner.api.Roadrunner;
import io.roadrunner.shaded.hdrhistogram.ConcurrentHistogram;
import io.roadrunner.shaded.hdrhistogram.Histogram;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultRoadrunner implements Roadrunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRoadrunner.class);

    private final int concurrentUsers;
    private final long requests;
    private final MeasurementProgress measurementProgress;

    public DefaultRoadrunner(int concurrentUsers, long requests, MeasurementProgress measurementProgress) {
        this.concurrentUsers = concurrentUsers;
        this.requests = requests;
        this.measurementProgress = measurementProgress;
    }

    @Override
    public Measurements execute(Supplier<Runnable> roadrunner) {

        var histogram = new ConcurrentHistogram(2);
        LOG.info("Roadrunner started: {} concurrent users, {} total number of requests", concurrentUsers, requests);
        var currentTimeMillis = System.currentTimeMillis();
        try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            var latch = new CountDownLatch(concurrentUsers);
            var measurementControl = new MeasurementControl(measurementProgress, requests);
            for (int i = 0; i < concurrentUsers; i++) {
                executorService.submit(new RoadrunnerTask(histogram, latch, measurementControl, roadrunner.get()));
            }

            try {
                latch.await();
                LOG.info("Roadrunner stopped, time {}ms", System.currentTimeMillis() - currentTimeMillis);
                executorService.shutdown();
                executorService.awaitTermination(10, TimeUnit.SECONDS);
                return DefaultMeasurements.create(histogram);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class RoadrunnerTask implements Runnable {

        private final Histogram histogram;
        private final CountDownLatch latch;
        private final MeasurementControl measurementControl;
        private final Runnable task;

        private RoadrunnerTask(
                Histogram histogram, CountDownLatch latch, MeasurementControl measurementControl, Runnable task) {
            this.histogram = histogram;
            this.latch = latch;
            this.measurementControl = measurementControl;
            this.task = task;
        }

        @Override
        public void run() {
            try {
                while (measurementControl.isRunning()) {
                    var scheduledTime = System.currentTimeMillis();
                    task.run();
                    var currentTime = System.currentTimeMillis();
                    histogram.recordValue(currentTime - scheduledTime);
                    measurementControl.countDown();
                }
            } finally {
                latch.countDown();
            }
        }
    }

    private static class MeasurementControl {

        private final AtomicLong counter;
        private final MeasurementProgress measurementProgress;
        private final long requests;

        MeasurementControl(MeasurementProgress measurementProgress, long requests) {
            this.measurementProgress = measurementProgress;
            this.requests = requests;
            this.counter = new AtomicLong(requests);
        }

        boolean isRunning() {
            return counter.get() > 0;
        }

        public void countDown() {
            measurementProgress.update(requests - counter.decrementAndGet());
        }
    }
}
