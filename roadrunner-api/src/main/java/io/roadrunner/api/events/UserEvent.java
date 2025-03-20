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
package io.roadrunner.api.events;

/**
 * Events related to virtual users entering and leaving the system.
 */
public sealed class UserEvent extends Event permits UserEvent.Enter, UserEvent.Exit {

    private UserEvent(long timestamp) {
        super(timestamp);
    }

    /**
     * Event indicating a user has entered the system.
     */
    public static final class Enter extends UserEvent {
        public Enter(long timestamp) {
            super(timestamp);
        }
    }

    /**
     * Event indicating a user has left the system.
     */
    public static final class Exit extends UserEvent {

        public Exit(long timestamp) {
            super(timestamp);
        }
    }

    public static Enter enter() {
        return new Enter(System.nanoTime());
    }

    public static Exit exit() {
        return new Exit(System.nanoTime());
    }
}
