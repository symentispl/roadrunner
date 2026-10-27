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
package io.roadrunner;

import io.roadrunner.core.Bootstrap;
import java.io.IOException;
import java.nio.file.Files;

final class BenchmarkSupport {
    private BenchmarkSupport() {}

    static Bootstrap bootstrap() throws IOException {
        return new Bootstrap().withOutputDir(Files.createTempDirectory("roadrunner-benchmarks-"));
    }
}
