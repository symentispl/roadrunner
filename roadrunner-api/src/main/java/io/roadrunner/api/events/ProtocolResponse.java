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

    public static final class Response<T> extends ProtocolResponse<Response<T>> {
        private final T body;

        public Response(long startTime, long stopTime, T body) {
            super(startTime, stopTime);
            this.body = body;
        }

        @Override
        Response<T> self() {
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
            return null;
        }
    }
}
