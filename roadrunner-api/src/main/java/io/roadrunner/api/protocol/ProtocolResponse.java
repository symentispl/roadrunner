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
package io.roadrunner.api.protocol;

public abstract sealed class ProtocolResponse<SELF extends ProtocolResponse<SELF>> permits Response, Error {
    private final long starTime;
    private final long stopTime;
    private long scheduledStartTime;
    private long latency;

    ProtocolResponse(long starTime, long stopTime) {
        this.starTime = starTime;
        this.stopTime = stopTime;
    }

    public static <T> Response<T> response(long startTime, long stopTime, T body) {
        return new Response<>(startTime, stopTime, body);
    }

    public static Error error(long startTime, long stopTime, String message) {
        return new Error(startTime, stopTime, message);
    }

    public long startTime() {
        return starTime;
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
}
