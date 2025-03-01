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
module io.roadrunner.core {
    requires io.roadrunner.options;
    requires io.roadrunner.protocols.spi;
    requires io.roadrunner.hdrhistogram;
    requires io.roadrunner.output.csv;
    requires io.roadrunner.api;
    requires org.slf4j;

    exports io.roadrunner.core;
}
