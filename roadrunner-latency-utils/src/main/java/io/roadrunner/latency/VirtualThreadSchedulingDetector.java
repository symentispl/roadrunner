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
package io.roadrunner.latency;

import io.roadrunner.latency.internal.PauseDetector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Detects virtual thread scheduling delays caused by carrier thread saturation.
 * <p>
 * {@link io.roadrunner.latency.internal.SimplePauseDetector} detects JVM-wide pauses (GC, safepoints)
 * by observing consensus across platform threads. However, it cannot observe a class of latency
 * unique to virtual threads: when all carrier (platform) threads are busy running other virtual threads,
 * newly submitted virtual threads queue up waiting to be mounted. The carrier threads are not paused —
 * they are busy — so {@code SimplePauseDetector} sees nothing.
 * <p>
 * This detector measures that scheduling lag by periodically submitting a probe task to a virtual thread
 * executor from a platform thread scheduler. The time between submission and the probe task's first
 * instruction is the scheduling delay. If that delay exceeds the configured threshold, it is reported
 * to registered {@link io.roadrunner.latency.internal.PauseDetectorListener}s so that
 * {@link io.roadrunner.latency.internal.LatencyStats} can apply coordinated-omission correction.
 */
public class VirtualThreadSchedulingDetector extends PauseDetector {

    private static final long DEFAULT_PROBE_INTERVAL_NS = 1_000_000L; // 1 ms
    private static final long DEFAULT_THRESHOLD_NS = 1_000_000L; // 1 ms

    private final long threshold;
    private final ScheduledExecutorService probeScheduler;
    private final ExecutorService virtualExecutor;

    /**
     * Creates a detector with a default probe interval (1 ms) and threshold (1 ms).
     */
    public VirtualThreadSchedulingDetector() {
        this(DEFAULT_PROBE_INTERVAL_NS, DEFAULT_THRESHOLD_NS);
    }

    /**
     * Creates a detector with explicit probe interval and notification threshold.
     *
     * @param probeIntervalNs how often to submit a probe task, in nanoseconds
     * @param thresholdNs     minimum scheduling delay to report as a pause, in nanoseconds
     */
    public VirtualThreadSchedulingDetector(long probeIntervalNs, long thresholdNs) {
        this.threshold = thresholdNs;
        this.probeScheduler = Executors.newScheduledThreadPool(
                1, Thread.ofPlatform().daemon(true).name("vt-probe-scheduler").factory());
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        probeScheduler.scheduleAtFixedRate(this::probe, probeIntervalNs, probeIntervalNs, TimeUnit.NANOSECONDS);
    }

    private void probe() {
        long submittedAt = System.nanoTime();
        virtualExecutor.submit(() -> {
            long startedAt = System.nanoTime();
            long schedulingDelay = startedAt - submittedAt;
            if (schedulingDelay > threshold) {
                notifyListeners(schedulingDelay, startedAt);
            }
        });
    }

    @Override
    public void shutdown() {
        probeScheduler.shutdownNow();
        virtualExecutor.shutdownNow();
        super.shutdown();
    }
}
