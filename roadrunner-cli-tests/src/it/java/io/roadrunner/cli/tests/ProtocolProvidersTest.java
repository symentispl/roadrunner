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
package io.roadrunner.cli.tests;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.cli.Preferences;
import io.roadrunner.cli.ProtocolProviders;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class ProtocolProvidersTest {

    @Test
    void loadProtocolProviders() throws IOException {
        var preferences = new Preferences(Paths.get("target/roadrunner-cli-tests-plugins"));
        try (var providers = ProtocolProviders.load(preferences)) {
            assertThat(providers.all()).satisfiesExactly(
                    provider -> assertThat(provider.name()).isEqualTo("ab"),
                    provider -> assertThat(provider.name()).isEqualTo("vm")
            );
        }
    }
}
