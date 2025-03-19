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
open module io.roadrunner.protocol.ab.tests {
    requires io.roadrunner.reports.html;
    requires io.roadrunner.output.csv;
    requires io.roadrunner.api;
    requires org.apache.commons.lang3;
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires transitive org.junit.jupiter.engine;
    requires org.graalvm.polyglot;

    exports io.roadrunner.reports.html.tests;
}
