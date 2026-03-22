package io.roadrunner.api.events;

public final class MeasurementError extends Event {
    public MeasurementError(long timestamp, Exception e) {
        super(timestamp);
    }
}
