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
package io.roadrunner.api.events;

import io.roadrunner.api.attachments.AttachmentKey;
import io.roadrunner.api.attachments.AttachmentRegistry;
import io.roadrunner.api.metrics.MetricKey;
import java.lang.ref.WeakReference;
import java.util.Objects;

public abstract sealed class SamplerResponse<SELF extends SamplerResponse<SELF>> extends Event
        permits SamplerResponse.Response, SamplerResponse.Error {
    private static final double[] EMPTY_METRICS = new double[0];
    private static final String[] EMPTY_ATTACHMENTS = new String[0];

    private long scheduledStartTime;
    private long latency;
    private final long stopTime;
    private final double[] metricValues;
    private final String[] attachmentValues;
    private WeakReference<Object> blackhole;

    SamplerResponse(long timestamp, long stopTime, int metricCapacity, int attachmentCapacity) {
        super(timestamp);
        this.stopTime = stopTime;
        this.metricValues = metricCapacity == 0 ? EMPTY_METRICS : new double[metricCapacity];
        this.attachmentValues = attachmentCapacity == 0 ? EMPTY_ATTACHMENTS : new String[attachmentCapacity];
    }

    public double metricValueAt(MetricKey key) {
        return metricValues[key.id()];
    }

    public void setMetricValue(MetricKey key, double value) {
        metricValues[key.id()] = value;
    }

    public String attachmentValueAt(AttachmentKey key) {
        return key.id() < attachmentValues.length ? attachmentValues[key.id()] : null;
    }

    public void setAttachmentValue(AttachmentKey key, String value) {
        attachmentValues[key.id()] = value;
    }

    /**
     * Prevents the JIT from eliminating the computation that produced {@code value}.
     * Wraps it in a {@link WeakReference} so the GC can reclaim memory under pressure.
     * May be called at most once per response; throws {@link IllegalStateException} otherwise.
     *
     * @return
     */
    public SamplerResponse<SELF> consume(Object value) {
        if (blackhole != null) {
            throw new IllegalStateException("consume() already called on this response");
        }
        blackhole = new WeakReference<>(value);
        return this;
    }

    public long stopTime() {
        return stopTime;
    }

    public long scheduledStartTime() {
        return scheduledStartTime;
    }

    public long latency() {
        return latency;
    }

    public SELF withScheduledStartTime(long scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
        return self();
    }

    public SELF withLatency(long latency) {
        this.latency = latency;
        return self();
    }

    abstract SELF self();

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SamplerResponse<?> that)) return false;
        if (!super.equals(o)) return false;
        return scheduledStartTime == that.scheduledStartTime && latency == that.latency && stopTime == that.stopTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), scheduledStartTime, latency, stopTime);
    }

    public static final class Response extends SamplerResponse<Response> {

        public Response(long startTime, long stopTime, int metricCapacity, int attachmentCapacity) {
            super(startTime, stopTime, metricCapacity, attachmentCapacity);
        }

        public Response(long startTime, long stopTime) {
            this(startTime, stopTime, 0, 0);
        }

        @Override
        Response self() {
            return this;
        }
    }

    public static final class Error extends SamplerResponse<Error> {

        public Error(long startTime, long stopTime, int metricCapacity, int attachmentCapacity) {
            super(startTime, stopTime, metricCapacity, attachmentCapacity);
        }

        public Error(long startTime, long stopTime) {
            this(startTime, stopTime, 0, AttachmentRegistry.ERROR_MESSAGE.id() + 1);
        }

        /**
         * Convenience accessor for the pre-registered MESSAGE attachment.
         */
        public String message() {
            return attachmentValueAt(AttachmentRegistry.ERROR_MESSAGE);
        }

        @Override
        Error self() {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Error that)) return false;
            return super.equals(o);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }
}
