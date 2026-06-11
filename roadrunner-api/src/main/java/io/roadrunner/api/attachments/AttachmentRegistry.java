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
package io.roadrunner.api.attachments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class AttachmentRegistry {

    /**
     * Framework-reserved key for error descriptions. Always id=0 in every registry instance.
     * {@code Error.message()} reads from this slot; {@code error(start, stop, String message)}
     * writes to it.
     */
    public static final AttachmentKey ERROR_MESSAGE = new AttachmentKey(0, "errorMessage");

    private final List<AttachmentKey> keys = new ArrayList<>(List.of(ERROR_MESSAGE));

    public AttachmentKey register(String name) {
        var key = new AttachmentKey(keys.size(), name);
        keys.add(key);
        return key;
    }

    public Collection<AttachmentKey> registeredKeys() {
        return Collections.unmodifiableList(keys);
    }

    public int size() {
        return keys.size();
    }
}
