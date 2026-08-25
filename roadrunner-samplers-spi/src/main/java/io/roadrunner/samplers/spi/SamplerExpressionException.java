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
package io.roadrunner.samplers.spi;

/**
 * A sampler operation expression could not be parsed, or parsed but didn't match any operation on
 * the target sampler. Thrown only by {@link SamplerExtensionPoint#bind}, so a caller that wants to
 * enrich the message (e.g. with the sampler's available operations) can catch specifically this
 * rather than any {@link IllegalArgumentException}.
 */
public class SamplerExpressionException extends IllegalArgumentException {
    public SamplerExpressionException(String message) {
        super(message);
    }

    public SamplerExpressionException(Throwable cause) {
        // not super(cause): that sets the message to cause.toString(), prefixing the exception
        // class name onto otherwise-clean, user-facing text.
        super(cause.getMessage(), cause);
    }
}
