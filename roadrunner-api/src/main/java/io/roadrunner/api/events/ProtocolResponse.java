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

import io.roadrunner.api.metrics.Metric;
import io.roadrunner.api.metrics.MetricUnit;
import io.roadrunner.api.metrics.Metrics;
import java.util.Objects;

public abstract sealed class ProtocolResponse<SELF extends ProtocolResponse<SELF>> extends Event
        permits ProtocolResponse.Response, ProtocolResponse.Error {
    private long scheduledStartTime;
    private long latency;

    public static <T> Response<T> response(long timestamp, long stopTime, T body) {
        return new Response<>(timestamp, stopTime, body);
    }

    public static Error error(long timestamp, long stopTime, String message) {
        return new Error(timestamp, stopTime, message);
    }

    public static Response<Object> empty(long startTime, long stopTime) {
        return new Response<>(startTime, stopTime, null);
    }

    private final long stopTime;

    ProtocolResponse(long timestamp, long stopTime) {
        super(timestamp);
        this.stopTime = stopTime;
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
        if (!(o instanceof ProtocolResponse<?> that)) return false;
        if (!super.equals(o)) return false;
        return scheduledStartTime == that.scheduledStartTime && latency == that.latency && stopTime == that.stopTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), scheduledStartTime, latency, stopTime);
    }

    public static final class Response<T> extends ProtocolResponse<Response<T>> {
        private final T body;
        private final Metrics metrics = Metrics.empty();

        public Response(long startTime, long stopTime, T body) {
            super(startTime, stopTime);
            this.body = body;
        }

        @Override
        Response<T> self() {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Response<?> response)) return false;
            if (!super.equals(o)) return false;
            return Objects.equals(body, response.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), body);
        }

        public Metrics metrics() {
            return metrics;
        }

        public ProtocolResponse addMetric(String metric, double value) {
            metrics.put(metric, new Metric(metric, MetricUnit.BYTES, value));
            return this;
        }
    }

    public static final class Error extends ProtocolResponse<Error> {

        private final String message;

        public Error(long startTime, long stopTime, String message) {
            super(startTime, stopTime);
            this.message = message;
        }

        @Override
        Error self() {
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Error that)) return false;
            if (!super.equals(o)) return false;
            return Objects.equals(message, that.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), message);
        }
    }
}
