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

import static java.util.stream.Collectors.toMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Function;

public interface SamplerParameters {

    SamplerParameters NONE = new DefaultSamplerParameters(new LinkedHashMap<>());

    static SamplerParameters of(String key, Object value) {
        return new DefaultSamplerParameters(new LinkedHashMap<>(Map.of(key, value)));
    }

    /**
     * Builds parameters from an ordered map. Iteration order of the map determines positional
     * binding order in samplers (e.g. JDBC placeholders), so the API requires a
     * {@link SequencedMap} — passing a {@link java.util.HashMap} won't compile.
     */
    static SamplerParameters of(SequencedMap<String, ?> map) {
        return new DefaultSamplerParameters(map);
    }

    static SamplerParameters of(
            SequencedMap<String, String> map, SequencedMap<String, Function<String, Object>> columns) {
        return new TypedSamplerParameters(map, columns);
    }

    Object valueOf(String key);

    SequencedMap<String, ?> asMap();

    void forEach(IndexedParameterSink sink) throws Exception;

    class DefaultSamplerParameters implements SamplerParameters {
        private final SequencedMap<String, ?> parameters;

        private DefaultSamplerParameters(SequencedMap<String, ?> parameters) {
            this.parameters = parameters;
        }

        @Override
        public Object valueOf(String key) {
            return parameters.get(key);
        }

        @Override
        public SequencedMap<String, ?> asMap() {
            return parameters;
        }

        @Override
        public void forEach(IndexedParameterSink sink) throws Exception {
            int i = 0;
            for (var value : parameters.values()) {
                sink.accept(i, value.getClass(), value);
                i++;
            }
        }
    }

    class TypedSamplerParameters implements SamplerParameters {
        private final SequencedMap<String, String> parameters;
        private final SequencedMap<String, Function<String, Object>> columns;

        private TypedSamplerParameters(
                SequencedMap<String, String> parameters, SequencedMap<String, Function<String, Object>> columns) {
            this.parameters = parameters;
            this.columns = columns;
        }

        // blank cells are kept as-is rather than run through the column's converter, since e.g.
        // Integer.valueOf("") throws instead of yielding a sensible "no value" result.
        private Object convert(String key, String value) {
            if (value.isEmpty()) {
                return value;
            }
            var converter = columns.get(key);
            if (converter == null) {
                throw new IllegalArgumentException(
                        "No converter registered for parameter '%s' — columns must have an entry for every key in parameters"
                                .formatted(key));
            }
            return converter.apply(value);
        }

        @Override
        public Object valueOf(String key) {
            String value = parameters.get(key);
            if (value != null) {
                return convert(key, value);
            }
            return null;
        }

        @Override
        public SequencedMap<String, ?> asMap() {
            return parameters.entrySet().stream()
                    .map(entry -> Map.entry(entry.getKey(), convert(entry.getKey(), entry.getValue())))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new));
        }

        @Override
        public void forEach(IndexedParameterSink sink) throws Exception {
            int i = 0;
            for (var entry : parameters.entrySet()) {
                Object applied = convert(entry.getKey(), entry.getValue());
                sink.accept(i, applied.getClass(), applied);
                i++;
            }
        }
    }
}
