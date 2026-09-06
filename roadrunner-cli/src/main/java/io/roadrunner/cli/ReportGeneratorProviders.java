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

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import io.roadrunner.logging.LoggingFacade;
import io.roadrunner.reports.ReportGeneratorProvider;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;

final class ReportGeneratorProviders {

    private static final Logger LOG = LoggingFacade.getLogger(ReportGeneratorProviders.class);
    private final Map<String, ReportGeneratorProvider> reportGeneratorProviders;

    ReportGeneratorProviders(Map<String, ReportGeneratorProvider> reportGeneratorProviders) {
        this.reportGeneratorProviders = reportGeneratorProviders;
    }

    static ReportGeneratorProviders load() {
        var reportGenerators = ServiceLoader.load(ReportGeneratorProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .peek(reportGenerator -> LOG.info("found report generator {}", reportGenerator.name()))
                .collect(toMap(ReportGeneratorProvider::name, identity()));
        return new ReportGeneratorProviders(reportGenerators);
    }

    public ReportGeneratorProvider get(String reportGeneratorName) {
        return reportGeneratorProviders.get(reportGeneratorName);
    }

    public Set<String> supportedReportFormats() {
        return reportGeneratorProviders.keySet();
    }
}
