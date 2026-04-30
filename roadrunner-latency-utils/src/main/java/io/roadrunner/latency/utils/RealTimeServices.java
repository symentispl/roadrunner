/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */
package io.roadrunner.latency.utils;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * {@link TimeServices} implementation that delegates to actual JDK time services.
 * Use {@link #INSTANCE} to obtain the singleton.
 */
public final class RealTimeServices implements TimeServices {

    public static final RealTimeServices INSTANCE = new RealTimeServices();

    private RealTimeServices() {}

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public void sleepMsecs(long sleepTimeMsec) {
        try {
            Thread.sleep(sleepTimeMsec);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void sleepNanos(long sleepTimeNsec) {
        try {
            TimeUnit.NANOSECONDS.sleep(sleepTimeNsec);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void setCurrentTime(long newCurrentTime) throws InterruptedException {
        while (newCurrentTime > System.nanoTime()) {
            TimeUnit.NANOSECONDS.sleep(newCurrentTime - System.nanoTime());
        }
    }

    @Override
    public void moveTimeForward(long timeDeltaNsec) throws InterruptedException {
        setCurrentTime(System.nanoTime() + timeDeltaNsec);
    }

    @Override
    public void moveTimeForwardMsec(long timeDeltaMsec) throws InterruptedException {
        moveTimeForward(timeDeltaMsec * 1_000_000L);
    }

    @Override
    public TimeServices.ScheduledExecutor newScheduledExecutor() {
        return new RealScheduledExecutor();
    }

    private static final class RealScheduledExecutor implements TimeServices.ScheduledExecutor {
        private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1, Thread.ofPlatform().daemon(true).name("latency-stats-scheduler").factory());

        @Override
        public void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            executor.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        @Override
        public void shutdown() {
            executor.shutdownNow();
        }
    }
}
