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
import io.roadrunner.reports.spi.ReportPlugin;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;

public final class ReportPlugins implements AutoCloseable {

    private static final Logger LOG = LoggingFacade.getLogger(ReportPlugins.class);
    private final Map<String, ReportPlugin> reportPlugins;

    private ReportPlugins(Map<String, ReportPlugin> reportPlugins) {
        this.reportPlugins = reportPlugins;
    }

    public static ReportPlugins load(Preferences preferences) {
        LOG.debug("loading report plugins");
        var plugins = ServiceLoader.load(ReportPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .peek(plugin -> LOG.info("found report plugin {}", plugin.name()))
                .collect(toMap(ReportPlugin::name, identity(), (first, second) -> {
                    throw new IllegalStateException(
                            "duplicate report plugin name '%s': found %s and %s"
                                    .formatted(
                                            first.name(),
                                            first.getClass().getName(),
                                            second.getClass().getName()));
                }));
        return new ReportPlugins(plugins);
    }

    public ReportPlugin get(String reportPluginName) {
        return reportPlugins.get(reportPluginName);
    }

    public Set<String> supportedReportFormats() {
        return reportPlugins.keySet();
    }

    @Override
    public void close() {
        for (ReportPlugin plugin : reportPlugins.values()) {
            try {
                plugin.close();
            } catch (Exception e) {
                LOG.error("cannot close report plugin {}", plugin.name(), e);
            }
        }
    }
}
