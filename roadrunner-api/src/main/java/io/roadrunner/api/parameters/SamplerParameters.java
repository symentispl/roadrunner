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

import java.util.LinkedHashMap;
import java.util.Map;

public final class SamplerParameters {

    public static final SamplerParameters NONE = new SamplerParameters(new LinkedHashMap<>());

    public static SamplerParameters of(String key, Object value) {
        return new SamplerParameters(new LinkedHashMap<>(Map.of(key, value)));
    }

    public static SamplerParameters of(Map<String, ?> map) {
        return new SamplerParameters(new LinkedHashMap<>(map));
    }

    private final LinkedHashMap<String, ?> parameters;

    private SamplerParameters(LinkedHashMap<String, ?> parameters) {
        this.parameters = parameters;
    }

    public Object valueOf(String key) {
        return parameters.get(key);
    }

    public Map<String, ?> asMap() {
        return parameters;
    }

    public void forEach(IndexedParameterSink sink) throws Exception {
        int i = 0;
        for (var value : parameters.values()) {
            sink.accept(i, value.getClass(), value);
            i++;
        }
    }
}
