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

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.split;
import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBefore;

import java.util.HashMap;
import java.util.Map;

public record ReportConfiguration(String reportFormat, Map<String, String> configuration) {

    public static ReportConfiguration parse(String opts) {
        var reportType = substringBefore(opts, ":");
        var configurationRaw = substringAfter(opts, ":");
        var configuration = new HashMap<String, String>();
        if (isNotEmpty(configurationRaw)) {
            var strings = split(configurationRaw, ",");
            for (var s : strings) {
                var keyValue = split(s, "=");
                configuration.put(keyValue[0], keyValue[1]);
            }
        }
        return new ReportConfiguration(reportType, configuration);
    }
}
