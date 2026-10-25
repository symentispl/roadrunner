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
package io.roadrunner.core.internal;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class TimestampOutputDirectoryNamingStrategy implements OutputDirectoryNamingStrategy {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final Clock clock;

    public TimestampOutputDirectoryNamingStrategy() {
        this(Clock.systemDefaultZone());
    }

    TimestampOutputDirectoryNamingStrategy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public String getName() {
        return "roadrunner-" + LocalDateTime.now(clock).format(FORMATTER);
    }
}
