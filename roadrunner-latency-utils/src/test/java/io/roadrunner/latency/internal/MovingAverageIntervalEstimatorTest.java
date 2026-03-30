/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package io.roadrunner.latency.internal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * JUnit test for {@link io.roadrunner.latency.internal.MovingAverageIntervalEstimator}
 */
public class MovingAverageIntervalEstimatorTest {

    static {
        System.setProperty("LatencyUtils.useActualTime", "false");
    }

    static long detectedPauseLength = 0;

    @Test
    public void testMovingAverageIntervalEstimator() throws Exception {
        MovingAverageIntervalEstimator estimator = new MovingAverageIntervalEstimator(1024);

        long now = 0;

        for (int i = 0; i < 10000; i++) {
            now += 20;
            estimator.recordInterval(now);
        }

        assertEquals(20, estimator.getEstimatedInterval(now), "expected interval to be 20");


        for (int i = 0; i < 512; i++) {
            now += 40;
            estimator.recordInterval(now);
        }

        assertEquals(30, estimator.getEstimatedInterval(0), "expected interval to be 20");

        for (int i = 0; i < 256; i++) {
            now += 60;
            estimator.recordInterval(now);
        }

        assertEquals(40, estimator.getEstimatedInterval(0), "expected interval to be 20");
    }
}
