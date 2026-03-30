/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package io.roadrunner.latency.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;


/**
 * JUnit test for {@link io.roadrunner.latency.utils.TimeCappedMovingAverageIntervalEstimator}
 */
public class TimeCappedMovingAverageIntervalEstimatorTest {

    @Test
    public void testWindowBehavior() throws Exception {
        MyArtificialPauseDetector pauseDetector = new MyArtificialPauseDetector();
        TimeCappedMovingAverageIntervalEstimator estimator = new TimeCappedMovingAverageIntervalEstimator(32, 1000000000L /* 1 sec */, pauseDetector);
        // Listeners are added as a concurrent operation, so wait a bit to let the listener register before we
        // start notifying of pauses:
        TimeUnit.NANOSECONDS.sleep(20000000L);

        long now = 0;

        for (int i = 0; i < 10000; i++) {
            now += 20;
            estimator.recordInterval(now);
        }

        assertEquals(20, estimator.getEstimatedInterval(now), "expected interval to be 20");

        for (int i = 0; i < 16; i++) {
            now += 40;
            estimator.recordInterval(now);
        }

        assertEquals(30, estimator.getEstimatedInterval(now), "expected interval to be 30");

        for (int i = 0; i < 8; i++) {
            now += 60;
            estimator.recordInterval(now);
        }

        assertEquals(40, estimator.getEstimatedInterval(now), "expected interval to be 40");
//
//        estimator.recordInterval(2000000000L);
//
//        assertEquals(Long.MAX_VALUE, estimator.getEstimatedInterval(2000000000L), "expected interval to be MAX_VALUE");

        pauseDetector.recordPause(1500000000L, now + 1500000000L);
        now += 1500000000L;
        TimeUnit.NANOSECONDS.sleep(20000000L);

        assertEquals(40, estimator.getEstimatedInterval(now), "expected interval to be 40");

        for (int i = 0; i < 8; i++) {
            estimator.recordInterval(now);
            now += 60;
        }

        // the pause should still be inside the window:

        assertEquals(50, estimator.getEstimatedInterval(now), "expected interval to be 50");


        now = 4000000000L;

        // 4.0 sec is outside of the window (more than 1 second past the end of the 1.5 sec pause and small stuff that followed it) :

        assertEquals(Long.MAX_VALUE, estimator.getEstimatedInterval(now), "expected interval to be MAX_VALUE");

        estimator.recordInterval(now);

        for (int i = 0; i < 16; i++) {
            now += 20;
            estimator.recordInterval(now);
        }

        assertEquals(20, estimator.getEstimatedInterval(now), "expected interval to be 20");

        // Record two pauses:

        pauseDetector.recordPause(1500000000L, 5501000000L); /* 1.5 sec pause, started 4.001 sec. in */
        now = 5501000000L;
        TimeUnit.NANOSECONDS.sleep(20000000L);

        estimator.recordInterval(now);

        for (int i = 0; i < 14; i++) {
            now += 40;
            estimator.recordInterval(now);
        }

        now = 5501000000L + (30 * 1000000);

        assertEquals(1000000, estimator.getEstimatedInterval(now), "expected interval to be 1000000");

        pauseDetector.recordPause(1500000000L /* 1.5 seconds pause */, 7100000000L /* started 5.6 seconds in */);
        now = 7100000000L;
        TimeUnit.NANOSECONDS.sleep(20000000L);

        now = 7100000000L + (21 * 10000000);

        // At this point, there are 3 seconds of pauses recorded that fit in the time cap, so the time cap is 4 sec.
        // and things recorded after 2.1 sec still count... :

        // There are 32 results in the 310,000,000 nsec + 3 sec interval that has 3 seconds of pause in it,
        // So avg interval is 310,000,000 / 31:
        assertEquals(10000000, estimator.getEstimatedInterval(now), "expected interval to be 10000000");

        for (int i = 0; i < 6; i++) {
            now += 40;
            estimator.recordInterval(now);
        }

        now = 8001000000L;

        // There are 21 results in the 4.0 seconds window that has a 3.0 sec pause in it (so avg interval is 1 sec / 20):
        assertEquals(50000000, estimator.getEstimatedInterval(now), "expected interval to be 50000000");

        estimator.recordInterval(now);   // Add one recorded interval

        now = 8001000040L;  // Move time to lose one recorded interval

        // There are 21 results in the 2.5 seconds window that has a 1.5 sec pause in it (so avg interval is 1 sec / 20):

        assertEquals(50000000, estimator.getEstimatedInterval(now), "expected interval to be 50000000");

        now = 9001000000L;

        assertEquals(Long.MAX_VALUE, estimator.getEstimatedInterval(now), "expected interval to be MAX_VALUE");

        now = 12000000000L;

        estimator.recordInterval(now);   // Add one recorded interval
        estimator.recordInterval(now);   // Add one recorded interval

        pauseDetector.recordPause(1500000000L /* 1.5 seconds pause */, 13500000000L /* started 12 seconds in */);
        now = 13500000000L;
        TimeUnit.NANOSECONDS.sleep(20000000L);

        pauseDetector.recordPause(1500000000L /* 1.5 seconds pause */, 15500000000L /* started 14 seconds in */);
        now = 15500000000L;
        TimeUnit.NANOSECONDS.sleep(20000000L);

        // 2 recorded value within the past 4 seconds window that has 3 seconds of pause in it:
        assertEquals(500000000, estimator.getEstimatedInterval(now), "expected interval to be 500000000");

        now = 16600000000L;

        // Should be able to peel off both pauses and see empty window:
        assertEquals(Long.MAX_VALUE, estimator.getEstimatedInterval(now), "expected interval to be MAX_VALUE");
    }

    @Test
    public void testToString() throws Exception {
        SimulatedTimeServices time = new SimulatedTimeServices();
        MyArtificialPauseDetector pauseDetector = new MyArtificialPauseDetector();
        TimeCappedMovingAverageIntervalEstimator estimator = new TimeCappedMovingAverageIntervalEstimator(1024, 10000000000L /* 10 sec */, pauseDetector);

        for (int i = 0; i < 2000; i++) {
            estimator.recordInterval(time.nanoTime());
            time.moveTimeForwardMsec(1);
            TimeUnit.NANOSECONDS.sleep(100000L); // let things propagate
        }

        time.moveTimeForwardMsec(1000);
        TimeUnit.NANOSECONDS.sleep(1000000L); // let things propagate

        estimator.getEstimatedInterval(time.nanoTime());
        System.out.print("toString():\n" + estimator);
    }

    @Test
    public void testIntervalWithSleeping() throws Exception {
        SimulatedTimeServices time = new SimulatedTimeServices();
        MyArtificialPauseDetector pauseDetector = new MyArtificialPauseDetector();
        TimeCappedMovingAverageIntervalEstimator estimator = new TimeCappedMovingAverageIntervalEstimator(128, 10000000000L /* 10 sec */, pauseDetector);

        System.out.println("\nTesting Interval Estimator with sleeps:\n");
        long startTime = time.nanoTime();
        for (int i = 0; i < 5; i++) {
            estimator.getEstimatedInterval(time.nanoTime());
            System.out.println("Interval estimator " + (time.nanoTime() - startTime) + " in:\n" +
                    estimator.toString());
            if (i > 1) {
                assertEquals(1000000, estimator.getEstimatedInterval(time.nanoTime()), "expected interval to be 1000000");
            }
            for (int j = 0; j < 64; j++) {
                time.moveTimeForwardMsec(1);
                TimeUnit.NANOSECONDS.sleep(100000L); // let things propagate
                estimator.recordInterval(time.nanoTime());
            }
        }
        long pauseStartTime = time.nanoTime();
        time.moveTimeForwardMsec(500);
        TimeUnit.NANOSECONDS.sleep(1000000L); // let things propagate
        long pauseEndTime = time.nanoTime();
        pauseDetector.recordPause(pauseEndTime - pauseStartTime, pauseEndTime);
        System.out.println("\n*** Paused for " + (pauseEndTime - pauseStartTime) + " nsec\n");
        for (int i = 0; i < 5; i++) {
            estimator.getEstimatedInterval(time.nanoTime());
            System.out.println("Interval estimator " + (time.nanoTime() - startTime) + " in:\n" +
                    estimator.toString());
            if (i > 1) {
                assertEquals(2000000, estimator.getEstimatedInterval(time.nanoTime()), "expected interval to be 2000000");
            }
            for (int j = 0; j < 64; j++) {
                time.moveTimeForwardMsec(2);
                TimeUnit.NANOSECONDS.sleep(100000L); // let things propagate
                estimator.recordInterval(time.nanoTime());
            }
        }

    }


    class MyArtificialPauseDetector extends PauseDetector {
        public volatile long latestPauseEndTime = 0;
        public void recordPause(long length, long when) {
            notifyListeners(length, when);
            latestPauseEndTime = when;
        }
    }
}
