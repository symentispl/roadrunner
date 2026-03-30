/**
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 */
package io.roadrunner.latency.utils;

import java.util.Comparator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * {@link TimeServices} implementation that operates on a simulated clock.
 * Time only advances in response to explicit calls such as {@link #moveTimeForward} or
 * {@link #setCurrentTime}. Intended for deterministic, time-controlled testing.
 */
public final class SimulatedTimeServices implements TimeServices {

    private volatile long currentTime = 0;
    private final Object timeUpdateMonitor = new Object();

    @Override
    public long nanoTime() {
        return currentTime;
    }

    @Override
    public long currentTimeMillis() {
        return currentTime / 1_000_000L;
    }

    @Override
    public void sleepMsecs(long sleepTimeMsec) {
        try {
            waitUntilTime(currentTime + (sleepTimeMsec * 1_000_000L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void sleepNanos(long sleepTimeNsec) {
        try {
            waitUntilTime(currentTime + sleepTimeNsec);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void setCurrentTime(long newCurrentTime) throws InterruptedException {
        if (newCurrentTime < currentTime) {
            throw new IllegalStateException("Can't set current time to the past.");
        }
        while (currentTime < newCurrentTime) {
            long timeDelta = Math.min((newCurrentTime - currentTime), 5_000_000L);
            currentTime += timeDelta;
            synchronized (timeUpdateMonitor) {
                timeUpdateMonitor.notifyAll();
            }
            Thread.sleep(0, 1); // yield so notified threads can run before next advance
        }
    }

    @Override
    public void moveTimeForward(long timeDeltaNsec) throws InterruptedException {
        setCurrentTime(currentTime + timeDeltaNsec);
    }

    @Override
    public void moveTimeForwardMsec(long timeDeltaMsec) throws InterruptedException {
        moveTimeForward(timeDeltaMsec * 1_000_000L);
    }

    public void waitUntilTime(long timeToWakeAt) throws InterruptedException {
        synchronized (timeUpdateMonitor) {
            while (timeToWakeAt > currentTime) {
                timeUpdateMonitor.wait();
            }
        }
    }

    @Override
    public TimeServices.ScheduledExecutor newScheduledExecutor() {
        return new SimulatedScheduledExecutor();
    }

    private final class SimulatedScheduledExecutor implements TimeServices.ScheduledExecutor {
        private final PriorityBlockingQueue<RunnableTaskEntry> taskEntries =
                new PriorityBlockingQueue<>(10000, RunnableTaskEntry.BY_START_TIME);
        private final SimulatedExecutorThread executorThread = new SimulatedExecutorThread();

        SimulatedScheduledExecutor() {
            executorThread.setDaemon(true);
            executorThread.start();
        }

        @Override
        public void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            long startTimeNsec = currentTime + TimeUnit.NANOSECONDS.convert(initialDelay, unit);
            long periodNsec = TimeUnit.NANOSECONDS.convert(period, unit);
            synchronized (timeUpdateMonitor) {
                taskEntries.add(new RunnableTaskEntry(command, startTimeNsec, periodNsec, true));
                timeUpdateMonitor.notifyAll();
            }
        }

        @Override
        public void shutdown() {
            executorThread.terminate();
        }

        private final class SimulatedExecutorThread extends Thread {
            volatile boolean doRun = true;

            void terminate() {
                synchronized (timeUpdateMonitor) {
                    doRun = false;
                    timeUpdateMonitor.notifyAll();
                }
            }

            @Override
            public void run() {
                try {
                    while (doRun) {
                        synchronized (timeUpdateMonitor) {
                            for (RunnableTaskEntry entry = taskEntries.peek();
                                    entry != null && entry.startTime < currentTime;
                                    entry = taskEntries.peek()) {
                                taskEntries.poll().runAndReschedule(currentTime);
                            }
                            timeUpdateMonitor.wait();
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (CancellationException ex) {
                    // expected on shutdown
                }
            }
        }
    }

    private static final class RunnableTaskEntry {
        static final Comparator<RunnableTaskEntry> BY_START_TIME =
                Comparator.comparingLong(e -> e.startTime);

        long startTime;
        final Runnable command;
        final long period;
        final long initialStartTime;
        long executionCount;
        final boolean fixedRate;

        RunnableTaskEntry(Runnable command, long startTimeNsec, long periodNsec, boolean fixedRate) {
            this.command = command;
            this.startTime = startTimeNsec;
            this.initialStartTime = startTimeNsec;
            this.period = periodNsec;
            this.fixedRate = fixedRate;
        }

        void runAndReschedule(long timeNow) {
            command.run();
            if (period != 0) {
                if (fixedRate) {
                    executionCount++;
                    startTime = initialStartTime + (executionCount * period);
                } else {
                    startTime = timeNow + period;
                }
            }
        }
    }
}
