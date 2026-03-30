/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */

package io.roadrunner.latency.utils;

import java.util.concurrent.TimeUnit;

/**
 * Provide an API for time-related services, such as getting the current time and waiting for
 * a given period of time. Two implementations are provided:
 * <ul>
 * <li>{@link RealTimeServices} — delegates to actual JDK time services
 * ({@code System.nanoTime()}, {@code Thread.sleep()}, etc.)</li>
 * <li>{@link SimulatedTimeServices} — only advances time in response to
 * explicit calls such as {@link SimulatedTimeServices#moveTimeForward}, useful
 * for deterministic testing.</li>
 * </ul>
 */
public sealed interface TimeServices
        permits RealTimeServices,
        SimulatedTimeServices {

    long nanoTime();

    long currentTimeMillis();

    void sleepMsecs(long sleepTimeMsec);

    void sleepNanos(long sleepTimeNsec);

    void moveTimeForward(long timeDeltaNsec) throws InterruptedException;

    void moveTimeForwardMsec(long timeDeltaMsec) throws InterruptedException;

    void setCurrentTime(long newCurrentTime) throws InterruptedException;

    ScheduledExecutor newScheduledExecutor();

    interface ScheduledExecutor {
        void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

        void shutdown();
    }
}
