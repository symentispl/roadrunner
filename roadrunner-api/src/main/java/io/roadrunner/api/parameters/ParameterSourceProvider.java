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
package io.roadrunner.api.parameters;

import java.util.Map;

/**
 * SPI for pluggable parameter sources.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 * Each provider has a unique {@link #name()} (e.g. {@code "csv"}, {@code "json"})
 * and creates a {@link ParameterSource} from a configuration map.
 * <p>
 * The configuration map is parsed from the CLI {@code --parameters-source} option
 * value using {@code name:key=value,key2=value2} syntax.
 */
public interface ParameterSourceProvider {

    String name();

    ParameterSource create(Map<String, String> configuration);
}
