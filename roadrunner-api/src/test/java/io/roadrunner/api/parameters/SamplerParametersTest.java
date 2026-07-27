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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SamplerParametersTest {

    private record Bound(int index, Class<?> type, Object value) {}

    @Test
    void typedParametersLookUpConverterByKeyNotByPosition() throws Exception {
        // parameters and columns are built in different orders on purpose: a positional
        // pairing between the two would apply "name"'s converter to "age"'s value and vice versa.
        SequencedMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("name", "alice");
        parameters.put("age", "30");

        SequencedMap<String, Function<String, Object>> columns = new LinkedHashMap<>();
        columns.put("age", Integer::valueOf);
        columns.put("name", s -> s);

        var parameters1 = SamplerParameters.of(parameters, columns);

        assertThat(parameters1.valueOf("name")).isEqualTo("alice");
        assertThat(parameters1.valueOf("age")).isEqualTo(30);
        assertThat(parameters1.asMap()).hasSize(2);
        assertThat(parameters1.asMap().get("name")).isEqualTo("alice");
        assertThat(parameters1.asMap().get("age")).isEqualTo(30);

        List<Bound> bound = new ArrayList<>();
        parameters1.forEach((index, type, value) -> bound.add(new Bound(index, type, value)));

        assertThat(bound).containsExactly(new Bound(0, String.class, "alice"), new Bound(1, Integer.class, 30));
    }

    @Test
    void typedParametersPreserveBlankValuesWithoutConversion() throws Exception {
        SequencedMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("age", "");

        SequencedMap<String, Function<String, Object>> columns = new LinkedHashMap<>();
        columns.put("age", Integer::valueOf);

        var parameters1 = SamplerParameters.of(parameters, columns);

        assertThat(parameters1.valueOf("age")).isEqualTo("");
        assertThat(parameters1.asMap()).hasSize(1);
        assertThat(parameters1.asMap().get("age")).isEqualTo("");

        List<Bound> bound = new ArrayList<>();
        parameters1.forEach((index, type, value) -> bound.add(new Bound(index, type, value)));

        assertThat(bound).containsExactly(new Bound(0, String.class, ""));
    }
}
