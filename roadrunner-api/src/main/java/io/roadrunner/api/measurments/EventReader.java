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
package io.roadrunner.api.measurments;

import io.roadrunner.api.attachments.AttachmentKey;
import io.roadrunner.api.events.Event;
import io.roadrunner.api.metrics.MetricKey;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a reader for events that can be iterated over.
 * Provides functionality to read and deserialize event objects.
 */
public interface EventReader extends Iterable<Event> {
    Iterator<Event> iterator();

    default Collection<MetricKey> metricKeys() {
        return List.of();
    }

    default Collection<AttachmentKey> attachmentKeys() {
        return List.of();
    }
}
