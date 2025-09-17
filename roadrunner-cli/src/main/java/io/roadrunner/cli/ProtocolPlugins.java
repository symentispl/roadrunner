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

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.roadrunner.protocols.spi.ProtocolPlugin;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProtocolPlugins implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ProtocolPlugins.class);
    private final Map<String, ProtocolPlugin> protocolPlugins;

    private ProtocolPlugins(Map<String, ProtocolPlugin> protocolPlugins) {
        this.protocolPlugins = protocolPlugins;
    }

    public static ProtocolPlugins load(Preferences preferences) {
        LOG.debug("loading protocol providers");
        var protocols = Stream.of(
                        loadRuntimeProviders(),
                        loadPluginProviders(preferences),
                        loadPluginSubdirectoryProviders(preferences))
                .flatMap(Function.identity())
                .collect(toMap(ProtocolPlugin::name, Function.identity(), (first, second) -> {
                    // When duplicate protocol providers are found, the first provider is prioritized.
                    // This ensures that runtime providers take precedence over plugin-based providers,
                    // as runtime providers are considered more stable and reliable.
                    LOG.debug("found duplicate protocol plugin {} in runtime and plugin directories", second.name());
                    return first;
                }));
        return new ProtocolPlugins(protocols);
    }

    private static Stream<ProtocolPlugin> loadPluginSubdirectoryProviders(Preferences preferences) {
        var pluginsDir = preferences.pluginsDir();

        if (!Files.exists(pluginsDir)) {
            LOG.debug("Plugins directory {} does not exist, skipping plugin loading", pluginsDir);
            return Stream.empty();
        }

        LOG.debug("Scanning for protocol plugins in subdirectories of {}", pluginsDir);

        try {
            return Files.list(pluginsDir).filter(Files::isDirectory).flatMap(subdir -> {
                try (var jarFiles =
                        Files.list(subdir).filter(path -> path.toString().endsWith(".jar"))) {
                    var jarPaths = jarFiles.toList();
                    if (jarPaths.isEmpty()) {
                        LOG.debug("No jar files found in {}", subdir);
                        return Stream.empty();
                    }

                    var urls = jarPaths.stream()
                            .map(path -> {
                                try {
                                    return path.toUri().toURL();
                                } catch (Exception e) {
                                    LOG.error("Failed to convert path to URL: {}", path, e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .toArray(URL[]::new);

                    var classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
                    return ServiceLoader.load(ProtocolPlugin.class, classLoader).stream()
                            .map(ServiceLoader.Provider::get)
                            .peek(plugin -> LOG.debug("Found protocol {} in directory {}", plugin.name(), subdir));

                } catch (IOException e) {
                    LOG.error("Failed to scan directory {}: {}", subdir, e.getMessage(), e);
                    return Stream.empty();
                }
            });
        } catch (IOException e) {
            LOG.error("Failed to scan plugins directory: {}", e.getMessage(), e);
        }
        return Stream.empty();
    }

    private static Stream<ProtocolPlugin> loadRuntimeProviders() {
        LOG.debug("Scanning for protocol plugins in runtime");
        return ServiceLoader.load(ProtocolPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .peek(protocolProvider -> LOG.debug("found protocol {} at runtime", protocolProvider.name()));
    }

    private static Stream<ProtocolPlugin> loadPluginProviders(Preferences preferences) {
        var pluginsDir = preferences.pluginsDir();

        if (!Files.exists(pluginsDir)) {
            LOG.debug("Plugins directory {} does not exist, skipping plugin loading", pluginsDir);
            return Stream.empty();
        }

        LOG.debug("Scanning for protocol plugins in {}", pluginsDir);

        try {
            return Files.list(pluginsDir)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .flatMap(ProtocolPlugins::loadProtocolProviderFromModule);
        } catch (IOException e) {
            LOG.error("Failed to scan plugins directory: {}", e.getMessage(), e);
        }
        return Stream.empty();
    }

    private static Stream<ProtocolPlugin> loadProtocolProviderFromModule(Path jarPath) {
        try {
            LOG.debug("Loading plugins from {}", jarPath);

            // Create a ModuleFinder that finds modules in this jar
            var pluginFinder = ModuleFinder.of(jarPath);

            // Get all module names from this jar
            var moduleNames = pluginFinder.findAll().stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .collect(toSet());

            if (moduleNames.isEmpty()) {
                LOG.warn("No modules found in {}", jarPath);
                return Stream.empty();
            }
            LOG.debug("Found modules {} in {}", moduleNames, jarPath);

            // Configure a layer for the plugin
            var parentLayer = ModuleLayer.boot();
            var pluginConfiguration = parentLayer.configuration().resolve(pluginFinder, ModuleFinder.of(), moduleNames);

            // Create a new layer with our plugin module
            var scl = ClassLoader.getSystemClassLoader();
            var pluginLayer = parentLayer.defineModulesWithOneLoader(pluginConfiguration, scl);

            return ServiceLoader.load(pluginLayer, ProtocolPlugin.class).stream()
                    .map(ServiceLoader.Provider::get)
                    .peek(plugin -> LOG.debug("found protocol {} from plugin module", plugin.name()));

        } catch (Exception e) {
            LOG.error("Failed to load plugin from {}: {}", jarPath, e.getMessage(), e);
        }
        return Stream.empty();
    }

    public Collection<ProtocolPlugin> all() {
        return protocolPlugins.values();
    }

    @Override
    public void close() {
        LOG.debug("closing protocol plugins");
        for (ProtocolPlugin protocolPlugin : protocolPlugins.values()) {
            try {
                protocolPlugin.close();
            } catch (Exception e) {
                LOG.error("cannot close protocol plugin {}", protocolPlugin.name(), e);
            }
        }
    }
}
