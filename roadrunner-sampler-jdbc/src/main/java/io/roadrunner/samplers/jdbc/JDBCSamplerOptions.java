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
package io.roadrunner.samplers.jdbc;

import io.roadrunner.samplers.spi.SamplerOptions;

import java.nio.file.Path;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(description = "JDBC protocol, executes a SQL query against a database", mixinStandardHelpOptions = true)
public class JDBCSamplerOptions implements SamplerOptions<JDBCSamplerProvider> {

    @Parameters(description = "SQL query to execute per request")
    public String query;

    @Option(names = "--url", description = "JDBC URL (e.g. jdbc:postgresql://localhost/mydb)", required = true)
    public String url;

    @Option(names = "--username", description = "Database username", required = true)
    public String username;

    @Option(names = "--password", description = "Database password", required = true)
    public String password;


    @Option(names = "--driver", description = "Path to JDBC driver JAR file", required = true)
    public Path driverPath;

    @Option(names = "--driver-class", description = "Fully-qualified JDBC driver class name (auto-detected if omitted)")
    public String driverClass;

    private final JDBCSamplerPlugin samplerPlugin;

    public JDBCSamplerOptions(JDBCSamplerPlugin samplerPlugin) {
        this.samplerPlugin = samplerPlugin;
    }

    @Override
    public JDBCSamplerProvider samplerProvider() {
        return samplerPlugin.newSamplerProvider(this);
    }
}
