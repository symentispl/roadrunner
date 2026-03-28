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
package io.roadrunner.app.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.LINUX)
public class RoadrunnerAppIT {

    @Test
    void cliVersion() throws Exception {
        Files.setPosixFilePermissions(Path.of("target/roadrunner-app/bin/roadrunner"), PosixFilePermissions.fromString("rwxr-xr-x"));
        var process = new ProcessBuilder("target/roadrunner-app/bin/roadrunner", "-V")
                .redirectErrorStream(true)
                .start();
        var exitCode = process.waitFor();
        assertThat(exitCode).isEqualTo(0);
        assertThat(process.inputReader().lines()).containsExactly("Roadrunner, a simplistic load generator");
    }
}
