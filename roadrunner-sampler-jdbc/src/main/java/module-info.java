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
import io.roadrunner.samplers.jdbc.JDBCSamplerPlugin;
import io.roadrunner.samplers.spi.SamplerPlugin;

module io.roadrunner.sampler.jdbc {
    requires java.sql;
    requires io.roadrunner.api;
    requires io.roadrunner.samplers.spi;
    requires info.picocli;
    requires com.zaxxer.hikari;

    uses java.sql.Driver;

    exports io.roadrunner.samplers.jdbc;

    provides SamplerPlugin with
            JDBCSamplerPlugin;

    opens io.roadrunner.samplers.jdbc to
            info.picocli;
}
