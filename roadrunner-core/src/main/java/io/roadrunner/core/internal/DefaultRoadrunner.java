/**
 *   Copyright 2024 Symentis.pl
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.roadrunner.core.internal;

import io.roadrunner.api.Roadrunner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultRoadrunner implements Roadrunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRoadrunner.class);

    public void execute(Supplier<Runnable> roadrunner, int concurrentUsers, long requests) {

        var histogram = new ConcurrentHistogram(2);
        LOG.info("Roadrunner started");
        var currentTimeMillis = System.currentTimeMillis();
        var executorService = Executors.newVirtualThreadPerTaskExecutor();
        var latch = new CountDownLatch(concurrentUsers);
        var measurementControl = new MeasurementControl(requests);
        for (int i = 0; i < concurrentUsers; i++) {
            executorService.submit(new RoadrunnerTask(histogram, latch, measurementControl, roadrunner.get()));
        }
        try {
            latch.await();
            LOG.info("Roadrunner stopped, time {}ms", System.currentTimeMillis() - currentTimeMillis);
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
            prettyPrintHistogramSummary(histogram);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void prettyPrintHistogramSummary(Histogram histogram) {
        // Extract key statistics from the histogram
        long totalCount = histogram.getTotalCount();
        double mean = histogram.getMean();
        long maxValue = histogram.getMaxValue();
        long minValue = histogram.getMinValue();
        double p50 = histogram.getValueAtPercentile(50.0);
        double p90 = histogram.getValueAtPercentile(90.0);
        double p99 = histogram.getValueAtPercentile(99.0);
        double p999 = histogram.getValueAtPercentile(99.9);

        // Print the summary in a human-readable way
        System.out.println("HdrHistogram Summary:");
        System.out.println("=====================");
        System.out.printf("Total Count    : %d%n", totalCount);
        System.out.printf("Min Value (ms) : %d ms%n", minValue);
        System.out.printf("Max Value (ms) : %d ms%n", maxValue);
        System.out.printf("Mean Value (ms): %.2f ms%n", mean);
        System.out.printf("50th Percentile: %.2f ms%n", p50);
        System.out.printf("90th Percentile: %.2f ms%n", p90);
        System.out.printf("99th Percentile: %.2f ms%n", p99);
        System.out.printf("99.9th Percentile: %.2f ms%n", p999);
        System.out.println("=====================");
    }

    private class RoadrunnerTask implements Runnable {

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
            while (measurementControl.isRunning()) {
                var scheduledTime = System.currentTimeMillis();
                task.run();
                var currentTime = System.currentTimeMillis();
                histogram.recordValue(currentTime - scheduledTime);
            }
            latch.countDown();
        }
    }

    private class MeasurementControl {

        private final AtomicLong counter;

        public MeasurementControl(long requests) {
            this.counter = new AtomicLong(requests);
        }

        boolean isRunning() {
            return counter.decrementAndGet() > 0;
        }
    }
}
