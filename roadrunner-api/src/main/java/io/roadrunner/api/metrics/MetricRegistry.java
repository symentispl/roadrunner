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
package io.roadrunner.api.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class MetricRegistry {

    private final List<MetricKey> keys = new ArrayList<>();

    public MetricKey register(String name, MetricUnit unit) {
        var key = new MetricKey(keys.size(), name, unit);
        keys.add(key);
        return key;
    }

    public Collection<MetricKey> registeredKeys() {
        return Collections.unmodifiableList(keys);
    }

    public int size() {
        return keys.size();
    }
}
