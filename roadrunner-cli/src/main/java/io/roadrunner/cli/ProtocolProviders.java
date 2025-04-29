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

import io.roadrunner.protocols.spi.ProtocolProvider;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProtocolProviders implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ProtocolProviders.class);
    private final Map<String, ProtocolProvider> protocolProviders;
    private final List<ModuleLayer> pluginLayers;

    ProtocolProviders(Map<String, ProtocolProvider> protocolProviders, List<ModuleLayer> pluginLayers) {
        this.protocolProviders = protocolProviders;
        this.pluginLayers = pluginLayers;
    }

    public static ProtocolProviders load(Preferences preferences) {
        LOG.debug("loading protocol providers");
        var protocols = new HashMap<String, ProtocolProvider>();

        // Load providers from classpath
        ServiceLoader.load(ProtocolProvider.class).stream()
                .map(ServiceLoader.Provider::get)
                .peek(protocolProvider -> LOG.debug("found protocol {} from classpath", protocolProvider.name()))
                .forEach(provider -> protocols.put(provider.name(), provider));

        // Load providers from plugins directory
        var pluginLayers = loadPluginProviders(protocols, preferences);

        return new ProtocolProviders(protocols, pluginLayers);
    }

    private static List<ModuleLayer> loadPluginProviders(Map<String, ProtocolProvider> protocols, Preferences preferences) {
        var pluginLayers = new java.util.ArrayList<ModuleLayer>();
        Path pluginsDir = preferences.pluginsDir();

        if (!Files.exists(pluginsDir)) {
            LOG.debug("Plugins directory {} does not exist, skipping plugin loading", pluginsDir);
            return pluginLayers;
        }

        LOG.debug("Scanning for protocol plugins in {}", pluginsDir);

        try (Stream<Path> jarFiles =
                Files.list(pluginsDir).filter(path -> path.toString().endsWith(".jar"))) {
            // Collect all jar paths
            List<Path> jarPaths = jarFiles.collect(Collectors.toList());

            // Process each jar individually to create separate module layers
            for (Path jarPath : jarPaths) {
                try {
                    LOG.debug("Loading plugin from {}", jarPath);

                    // Create a ModuleFinder that finds modules in this jar
                    ModuleFinder pluginFinder = ModuleFinder.of(jarPath);

                    // Get all module names from this jar
                    Set<String> moduleNames = pluginFinder.findAll().stream()
                            .map(ModuleReference::descriptor)
                            .map(descriptor -> descriptor.name())
                            .collect(Collectors.toSet());

                    if (moduleNames.isEmpty()) {
                        LOG.warn("No modules found in {}", jarPath);
                        continue;
                    }

                    // Configure a layer for the plugin
                    ModuleLayer parentLayer = ModuleLayer.boot();
                    Configuration pluginConfiguration =
                            parentLayer.configuration().resolve(pluginFinder, ModuleFinder.of(), moduleNames);

                    // Create a new layer with our plugin module
                    ClassLoader scl = ClassLoader.getSystemClassLoader();
                    ModuleLayer pluginLayer = parentLayer.defineModulesWithOneLoader(pluginConfiguration, scl);
                    pluginLayers.add(pluginLayer);

                    // Load services from this layer
                    for (String moduleName : moduleNames) {
                        LOG.debug("Loading services from module: {}", moduleName);
                        ServiceLoader.load(pluginLayer, ProtocolProvider.class).stream()
                                .map(ServiceLoader.Provider::get)
                                .peek(provider -> LOG.debug(
                                        "found protocol {} from plugin module {}", provider.name(), moduleName))
                                .forEach(provider -> protocols.put(provider.name(), provider));
                    }
                } catch (Exception e) {
                    LOG.error("Failed to load plugin from {}: {}", jarPath, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to scan plugins directory: {}", e.getMessage(), e);
        }

        return pluginLayers;
    }

    @Override
    public void close() {
        LOG.debug("closing protocol providers");
        for (ProtocolProvider protocolProvider : protocolProviders.values()) {
            try {
                protocolProvider.close();
            } catch (Exception e) {
                LOG.error("cannot close protocol provider {}", protocolProvider.name(), e);
            }
        }

        // Note: ModuleLayers don't need explicit closing like ClassLoaders
        // The resources will be garbage collected when no longer referenced
    }

    public Collection<ProtocolProvider> all() {
        return protocolProviders.values();
    }
}
