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
import io.roadrunner.samplers.ab.AbSamplerPlugin;
import io.roadrunner.samplers.spi.SamplerPlugin;

module io.roadrunner.samplers.ab {
    requires java.net.http;
    requires io.roadrunner.api;
    requires io.roadrunner.samplers.spi;
    requires info.picocli;

    exports io.roadrunner.samplers.ab;

    provides SamplerPlugin with
            AbSamplerPlugin;

    opens io.roadrunner.samplers.ab to
            info.picocli;
}
