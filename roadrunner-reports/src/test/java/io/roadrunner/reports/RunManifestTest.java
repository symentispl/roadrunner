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
package io.roadrunner.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunManifestTest {

    @Test
    void roundTripsAClosedWorldManifest(@TempDir Path dir) throws IOException {
        var manifest = new RunManifest(
                "0.3.1",
                "2026-07-31T13:44:22Z",
                "http",
                "http:expression=GET(\"http://localhost/\")",
                "closed",
                "10",
                "2000",
                "",
                "",
                "",
                "csv:file=ids.csv",
                "25.0.3",
                "-Xmx1g",
                "Linux",
                "6.17.0-41-generic",
                "amd64",
                "8",
                "16000000000");

        manifest.writeTo(dir);
        var read = RunManifest.readFrom(dir);

        assertThat(read).isEqualTo(manifest);
    }

    @Test
    void roundTripsAnOpenWorldManifest(@TempDir Path dir) throws IOException {
        var manifest = new RunManifest(
                "0.3.1",
                "2026-07-31T13:44:22Z",
                "http",
                "http:expression=GET(\"http://localhost/\")",
                "open",
                "",
                "",
                "50",
                "PT30S",
                "vt,jvm",
                "",
                "25.0.3",
                "-Xmx1g",
                "Linux",
                "6.17.0-41-generic",
                "amd64",
                "8",
                "16000000000");

        manifest.writeTo(dir);
        var read = RunManifest.readFrom(dir);

        assertThat(read).isEqualTo(manifest);
    }

    @Test
    void rendersUnknownWhenTheRunDirectoryHasNoManifest(@TempDir Path dir) throws IOException {
        var manifest = RunManifest.readFrom(dir);

        assertThat(manifest).isEqualTo(RunManifest.UNKNOWN);
        assertThat(manifest.roadrunnerVersion()).isEqualTo("unknown");
        assertThat(manifest.loadModel()).isEqualTo("unknown");
    }

    @Test
    void runDirectoryDelegatesToTheManifest(@TempDir Path dir) throws IOException {
        assertThat(RunDirectory.of(dir).manifest()).isEqualTo(RunManifest.UNKNOWN);
    }
}
