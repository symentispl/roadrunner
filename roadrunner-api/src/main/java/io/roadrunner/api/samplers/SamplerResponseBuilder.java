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
package io.roadrunner.api.samplers;

import io.roadrunner.api.events.SamplerResponse;
import java.util.function.Consumer;

public interface SamplerResponseBuilder {

    SamplerResponse.Response response(long start, long stop);

    SamplerResponse.Response response(long start, long stop, Consumer<SamplerSink> sink);

    SamplerResponse.Error error(long start, long stop, String message);

    SamplerResponse.Error error(long start, long stop, String message, Consumer<SamplerSink> sink);
}
