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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class YearlyOutputDirectoryNamingStrategyTest {

    @ParameterizedTest(name = "{0} in {1} produces {2}")
    @CsvSource({
        "2026-10-23T12:34:56Z, Europe/Warsaw, roadrunner-20261023-143456",
        "2026-01-02T03:04:05Z, UTC,           roadrunner-20260102-030405",
        "2024-02-29T00:00:00Z, UTC,           roadrunner-20240229-000000",
        "2026-12-31T23:00:00Z, Europe/Warsaw, roadrunner-20270101-000000",
        "2026-03-29T00:59:59Z, Europe/Warsaw, roadrunner-20260329-015959",
        "2026-03-29T01:00:00Z, Europe/Warsaw, roadrunner-20260329-030000",
        "2026-10-25T00:59:59Z, Europe/Warsaw, roadrunner-20261025-025959",
        "2026-10-25T01:00:00Z, Europe/Warsaw, roadrunner-20261025-020000"
    })
    void includesDateAndTimeFromClockInName(String instant, String zone, String expectedName) {
        var clock = Clock.fixed(Instant.parse(instant), ZoneId.of(zone));
        var strategy = new YearlyOutputDirectoryNamingStrategy(clock);

        var name = strategy.getName();

        assertThat(name).isEqualTo(expectedName);
    }
}
