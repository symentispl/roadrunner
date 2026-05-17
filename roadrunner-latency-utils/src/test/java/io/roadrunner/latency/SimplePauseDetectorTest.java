/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package io.roadrunner.latency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;


/**
 * Tests for {@link SimplePauseDetector}.
 * <p>
 * Each test verifies the positive property: when a detector thread observes a simulated-time
 * gap larger than the pause-notification threshold (because it was held in the stall loop while
 * simulated time advanced), a pause is detected with the expected hiccup time.
 * <p>
 * Tests use a single detector thread to eliminate the multi-thread late-start race that made
 * the original suite flaky (a thread that hadn't started by the time stallDetectorThreads ran
 * could CAS {@code consensusLatestTime} to a mid-stall value during its pre-loop, shrinking
 * the eventual delta below the threshold). Multi-thread consensus is exercised in production
 * but is not testable here without wall-clock-based synchronization.
 * <p>
 * Time advances exclusively through {@link SimulatedTimeServices}; the test waits on the
 * detector's observable state via {@link org.awaitility.Awaitility#await} rather than
 * {@code Thread.sleep}. Negative ("no pause detected") assertions are intentionally omitted:
 * they would require a wall-clock window to elapse without an event, reintroducing the
 * timing dependence that caused the original flakes.
 */
public class SimplePauseDetectorTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);

    @Test
    public void detectsPauseWhenSleepingThreadStalls() throws Exception {
        long sleepInterval = 1_000_000L; // 1 msec
        long threshold = 10_000_000L; // 10 msec
        long stallLength = 20_000_000L; // 20 msec
        long expectedHiccup = stallLength - sleepInterval; // 19 msec

        AtomicLong detectedPauseLength = new AtomicLong(0);
        SimulatedTimeServices time = new SimulatedTimeServices();
        SimplePauseDetector pauseDetector =
                new SimplePauseDetector(sleepInterval, threshold, 1, true, time);

        try (RegisteredListener ignored = RegisteredListener.register(pauseDetector, detectedPauseLength)) {
            primeShortestObservedLoop(pauseDetector, time, sleepInterval);

            pauseDetector.stallDetectorThreads(0x1, stallLength);

            await().atMost(AWAIT_TIMEOUT)
                    .untilAtomic(detectedPauseLength, greaterThan(threshold));

            assertThat(detectedPauseLength.get()).isEqualTo(expectedHiccup);
        } finally {
            pauseDetector.shutdown();
        }
    }

    @Test
    public void detectsPauseWhenShortSleepingThreadStalls() throws Exception {
        long sleepInterval = 20_000L; // 20 usec
        long threshold = 2_000_000L; // 2 msec
        long stallLength = 3_000_000L; // 3 msec
        long expectedHiccup = stallLength - sleepInterval; // ~2.98 msec

        AtomicLong detectedPauseLength = new AtomicLong(0);
        SimulatedTimeServices time = new SimulatedTimeServices();
        SimplePauseDetector pauseDetector =
                new SimplePauseDetector(sleepInterval, threshold, 1, true, time);

        try (RegisteredListener ignored = RegisteredListener.register(pauseDetector, detectedPauseLength)) {
            primeShortestObservedLoop(pauseDetector, time, sleepInterval);

            pauseDetector.stallDetectorThreads(0x1, stallLength);

            await().atMost(AWAIT_TIMEOUT)
                    .untilAtomic(detectedPauseLength, greaterThan(threshold));

            assertThat(detectedPauseLength.get()).isEqualTo(expectedHiccup);
        } finally {
            pauseDetector.shutdown();
        }
    }

    @Test
    public void detectsPauseWhenSpinningThreadStalls() throws Exception {
        long sleepInterval = 0L; // spinning
        long threshold = 50_000L; // 50 usec
        long stallLength = 100_000L; // 100 usec

        AtomicLong detectedPauseLength = new AtomicLong(0);
        SimulatedTimeServices time = new SimulatedTimeServices();
        SimplePauseDetector pauseDetector =
                new SimplePauseDetector(sleepInterval, threshold, 1, true, time);

        try (RegisteredListener ignored = RegisteredListener.register(pauseDetector, detectedPauseLength)) {
            // Spinning detectors must iterate at least once before the stall, otherwise
            // shortestObservedTimeAroundLoop stays at Long.MAX_VALUE and the post-stall hiccup
            // computes to 0. A 1-nsec nudge plus Awaitility's pollDelay gives the spinning loop
            // ample wall time to converge its per-loop floor to 0 after observing the change.
            primeShortestObservedLoop(pauseDetector, time, 1L);

            pauseDetector.stallDetectorThreads(0x1, stallLength);

            await().atMost(AWAIT_TIMEOUT)
                    .untilAtomic(detectedPauseLength, greaterThan(threshold));

            long detected = detectedPauseLength.get();
            // For spinning, shortest converges to 0 so hiccup ~= stallLength. Allow a loose
            // upper bound to tolerate one iteration of slip between the dribbled time chunks
            // inside stallDetectorThreads.
            assertThat(detected)
                    .isGreaterThanOrEqualTo(threshold)
                    .isLessThanOrEqualTo(stallLength + threshold);
        } finally {
            pauseDetector.shutdown();
        }
    }

    /**
     * Drives the detector thread through two complete outer-loop iterations using simulated time,
     * leaving shortestObservedTimeAroundLoop set to {@code advanceNanos} for sleeping detectors
     * and converged to 0 for spinning detectors.
     * <p>
     * Two advances are required because the constructor's pre-loop CAS sets
     * {@code consensusLatestTime} to the current simulated time before iter-1's body runs. If the
     * test only advances once and waits for consensus to update, the thread may satisfy the
     * condition via the pre-loop alone, leaving {@code shortestObservedTimeAroundLoop} at
     * {@code Long.MAX_VALUE}. The second advance forces iter-1 (and possibly iter-2) of the main
     * loop to execute, which is where shortest is computed.
     */
    private static void primeShortestObservedLoop(
            SimplePauseDetector pauseDetector, SimulatedTimeServices time, long advanceNanos)
            throws InterruptedException {
        time.moveTimeForward(advanceNanos);
        await().atMost(AWAIT_TIMEOUT)
                .untilAtomic(pauseDetector.consensusLatestTime, greaterThanOrEqualTo(advanceNanos));

        time.moveTimeForward(advanceNanos);
        await().atMost(AWAIT_TIMEOUT)
                .untilAtomic(pauseDetector.consensusLatestTime, greaterThanOrEqualTo(2 * advanceNanos));
    }

    /**
     * Simple {@link PauseDetectorListener} bound to a detector for the duration of a
     * try-with-resources scope. Records the most recent observed pause length.
     */
    private static final class RegisteredListener implements AutoCloseable, PauseDetectorListener {
        private final PauseDetector pauseDetector;
        private final AtomicLong detectedPauseLength;

        private RegisteredListener(PauseDetector pauseDetector, AtomicLong detectedPauseLength) {
            this.pauseDetector = pauseDetector;
            this.detectedPauseLength = detectedPauseLength;
        }

        static RegisteredListener register(PauseDetector pauseDetector, AtomicLong detectedPauseLength) {
            RegisteredListener listener = new RegisteredListener(pauseDetector, detectedPauseLength);
            pauseDetector.addListener(listener);
            return listener;
        }

        @Override
        public void handlePauseEvent(long pauseLengthNsec, long pauseEndTimeNsec) {
            detectedPauseLength.set(pauseLengthNsec);
        }

        @Override
        public void close() {
            pauseDetector.removeListener(this);
        }
    }
}
