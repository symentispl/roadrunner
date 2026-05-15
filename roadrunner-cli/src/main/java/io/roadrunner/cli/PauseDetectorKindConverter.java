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
package io.roadrunner.cli;

import io.roadrunner.latency.recording.PauseDetectorKind;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import picocli.CommandLine;

public class PauseDetectorKindConverter implements CommandLine.ITypeConverter<EnumSet<PauseDetectorKind>> {
    @Override
    public EnumSet<PauseDetectorKind> convert(String value) {
        if (value == null || value.isBlank() || "none".equalsIgnoreCase(value.trim())) {
            return EnumSet.noneOf(PauseDetectorKind.class);
        }
        var kinds = EnumSet.noneOf(PauseDetectorKind.class);
        for (String token : Arrays.stream(value.split(",")).map(String::trim).toList()) {
            switch (token.toLowerCase(Locale.ROOT)) {
                case "vt" -> kinds.add(PauseDetectorKind.VT_SCHEDULING);
                case "jvm" -> kinds.add(PauseDetectorKind.JVM_PAUSE);
                default ->
                    throw new IllegalArgumentException(
                            "unknown pause detector '%s', expected one of: vt, jvm, none".formatted(token));
            }
        }
        return kinds;
    }
}
