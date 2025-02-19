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
package io.roadrunner.protocols.ab;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class CustomBodyHandlers {

    public record HttpResponseData(long bytesConsumed, double speedMbps) {
    }

    public static HttpResponse.BodyHandler<HttpResponseData> ofByteCounting() {
        return responseInfo -> new ByteCountingSubscriber();
    }

    private static class ByteCountingSubscriber implements HttpResponse.BodySubscriber<HttpResponseData> {
        private long bytesConsumed = 0;
        private Instant startTime;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            startTime = Instant.now();
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            for (ByteBuffer buffer : item) {
                bytesConsumed += buffer.remaining();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            completion.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);
            double seconds = duration.toMillis() / 1000.0;
            double speedMbps = (bytesConsumed * 8) / (seconds * 1_000_000);
            HttpResponseData responseData = new HttpResponseData(bytesConsumed, speedMbps);
            // Complete the subscriber with the response data
            this.completion.complete(responseData);
        }

        @Override
        public CompletionStage<HttpResponseData> getBody() {
            return completion;
        }

        private final CompletableFuture<HttpResponseData> completion = new CompletableFuture<>();
    }
}
