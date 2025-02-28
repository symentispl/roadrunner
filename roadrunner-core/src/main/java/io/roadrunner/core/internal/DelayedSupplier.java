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
package io.roadrunner.core.internal;

import java.util.function.Supplier;

final class DelayedSupplier<T> implements Supplier<T> {
    private final Supplier<T> delegate;
    private final Supplier<Long> delayStrategy;

    public DelayedSupplier(Supplier<T> delegate, Supplier<Long> delayStrategy) {
        this.delegate = delegate;
        this.delayStrategy = delayStrategy;
    }

    @Override
    public T get() {
        try {
            Thread.sleep(delayStrategy.get());
            return delegate.get();
        } catch (InterruptedException e) {
            // Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for delayed supply", e);
        }
    }
}
