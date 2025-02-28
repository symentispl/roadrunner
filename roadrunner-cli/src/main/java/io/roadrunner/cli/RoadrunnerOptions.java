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

import io.roadrunner.options.CliOption;
import java.nio.file.Path;

public record RoadrunnerOptions(
        @CliOption(
                        opt = "c",
                        description = "Number of multiple requests to make at a time",
                        required = true,
                        hasArg = true)
                Integer concurrency,
        @CliOption(opt = "n", description = "Total number of multiple requests to make", required = true, hasArg = true)
                Integer numberOfRequests,
        @CliOption(opt = "s", description = "Charts", hasArg = true) Path chartsDir) {}
