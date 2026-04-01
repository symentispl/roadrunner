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
import io.roadrunner.api.parameters.ParameterSource;
import io.roadrunner.api.samplers.SamplerProvider;
import io.roadrunner.output.csv.CsvOutputEventListener;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultRoadrunner implements Roadrunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRoadrunner.class);

    private final ExecutionStrategy strategy;
    private final MeasurementProgress measurementProgress;
    private final Path outputDir;
    private final ParameterSource parameterSource;

    public DefaultRoadrunner(
            ExecutionStrategy strategy,
            MeasurementProgress measurementProgress,
            Path outputDir,
            ParameterSource parameterSource) {
        this.strategy = strategy;
        this.measurementProgress = measurementProgress;
        this.outputDir = outputDir;
        this.parameterSource = parameterSource;
    }

    @Override
    public Measurements execute(SamplerProvider samplerSupplier) {
        LOG.info("Roadrunner started");
        var csvOutputFile = outputDir.resolve("output.csv");
        LOG.info("Writing responses to {}", csvOutputFile);

        var progressTrackingResponseListener = new ProgressTrackingResponseListener(new CsvOutputEventListener(csvOutputFile), measurementProgress);
        try (var responsesJournal = new QueueingSamplerResponsesJournal(progressTrackingResponseListener);
             var preloadedParameterSource = PreloadedParameterSource.from(parameterSource);
             var gcProfiler = new GCProfiler()) {
            gcProfiler.start();
            responsesJournal.start();
            var parameterFeed = preloadedParameterSource.load();
            try {
                strategy.execute(samplerSupplier, parameterFeed, responsesJournal);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return DefaultMeasurements.from(responsesJournal.measurementsReader());
        } catch (
                Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
    }

    private static class ProgressTrackingResponseListener implements EventListener {
        private final EventListener delegate;
        private final MeasurementProgress measurementProgress;
        private final AtomicLong processedRequests = new AtomicLong(0);

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
            // Update progress based on batch size
            var currentProcessed = processedRequests.addAndGet(
                    batch.stream().filter(SamplerResponse.class::isInstance).count());
            measurementProgress.update(currentProcessed);
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
