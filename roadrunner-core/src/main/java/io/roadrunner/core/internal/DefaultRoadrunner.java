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

import io.roadrunner.api.Roadrunner;
import io.roadrunner.api.events.Event;
import io.roadrunner.api.events.EventListener;
import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.measurments.EventReader;
import io.roadrunner.api.measurments.MeasurementProgress;
import io.roadrunner.api.measurments.Measurements;
import io.roadrunner.api.measurments.ProgressSnapshot;
import io.roadrunner.api.parameters.ParameterSource;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.latency.recording.LatencyRecorders;
import io.roadrunner.latency.recording.PauseDetectorKind;
import io.roadrunner.output.csv.CsvOutputEventListener;
import io.roadrunner.samplers.spi.SamplerContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultRoadrunner implements Roadrunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRoadrunner.class);

    private final ExecutionStrategy strategy;
    private final MeasurementProgress measurementProgress;
    private final Path outputDir;
    private final ParameterSource parameterSource;
    private final EnumSet<PauseDetectorKind> pauseDetectorKinds;

    public DefaultRoadrunner(
            ExecutionStrategy strategy,
            MeasurementProgress measurementProgress,
            Path outputDir,
            ParameterSource parameterSource,
            EnumSet<PauseDetectorKind> pauseDetectorKinds) {
        this.strategy = strategy;
        this.measurementProgress = measurementProgress;
        this.outputDir = outputDir;
        this.parameterSource = parameterSource;
        this.pauseDetectorKinds = pauseDetectorKinds;
    }

    @Override
    public Measurements execute(SamplerProvider samplerSupplier) {
        LOG.info("Roadrunner started");
        var csvOutputFile = outputDir.resolve("output.csv");
        LOG.info("Writing responses to {}", csvOutputFile);

        var samplerContext = SamplerContext.of(samplerSupplier);

        var progressTrackingResponseListener = new ProgressTrackingResponseListener(
                new CsvOutputEventListener(
                        csvOutputFile, samplerContext.metricRegistry(), samplerContext.attachmentRegistry()),
                measurementProgress);
        try (var responsesJournal = new QueueingSamplerResponsesJournal(progressTrackingResponseListener);
                var gcProfiler = new GCProfiler();
                var latencyRecorder = LatencyRecorders.create(pauseDetectorKinds)) {
            var parameterFeed = ParameterCarousel.from(parameterSource);
            gcProfiler.start();
            responsesJournal.start();
            try {
                strategy.execute(samplerSupplier, parameterFeed, responsesJournal, latencyRecorder, samplerContext);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                try {
                    latencyRecorder.writeSnapshot(outputDir);
                } catch (IOException e) {
                    LOG.error("failed to write latency snapshot to {}", outputDir, e);
                }
            }
            return DefaultMeasurements.from(responsesJournal.measurementsReader());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {}

    static EventListener newProgressListener(EventListener delegate, MeasurementProgress progress) {
        return new ProgressTrackingResponseListener(delegate, progress);
    }

    private static class ProgressTrackingResponseListener implements EventListener {
        private static final int SPARK_SLICES = 16;

        private final EventListener delegate;
        private final MeasurementProgress measurementProgress;
        private final AtomicLong processed = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private final long[] spark = new long[SPARK_SLICES];
        private volatile long startNanos = 0L;

        ProgressTrackingResponseListener(EventListener delegate, MeasurementProgress measurementProgress) {
            this.delegate = delegate;
            this.measurementProgress = measurementProgress;
        }

        @Override
        public void onStart() {
            delegate.onStart();
        }

        @Override
        public void onEvent(Collection<? extends Event> batch) {
            delegate.onEvent(batch);
            // Skip the snapshot bookkeeping/allocation when no one is listening (Bootstrap's default),
            // so non-CLI usage doesn't pay for progress reporting on the event path.
            if (measurementProgress == MeasurementProgress.NO_OP) {
                return;
            }
            if (startNanos == 0L) {
                startNanos = System.nanoTime();
            }
            long batchCount = 0;
            long batchErrors = 0;
            for (var e : batch) {
                if (e instanceof SamplerResponse<?>) {
                    batchCount++;
                    if (e instanceof SamplerResponse.Error) {
                        batchErrors++;
                    }
                }
            }
            var total = processed.addAndGet(batchCount);
            var totalErrors = errors.addAndGet(batchErrors);
            var elapsed = Math.max(1L, System.nanoTime() - startNanos);
            var throughput = total / (elapsed / 1_000_000_000.0);
            // ponytail: coarse 1s ring, replace with a proper sliding window only if the live sparkline looks jerky.
            var slice = (int) ((elapsed / 1_000_000_000L) % SPARK_SLICES);
            spark[slice] = batchCount;
            measurementProgress.update(new ProgressSnapshot(total, totalErrors, elapsed, throughput, spark.clone()));
        }

        @Override
        public void onStop() {
            delegate.onStop();
        }

        @Override
        public EventReader samplesReader() {
            return delegate.samplesReader();
        }
    }
}
