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
import io.roadrunner.samplers.spi.cli.SamplerOption;
import io.roadrunner.samplers.spi.cli.SamplerParameter;
import java.nio.file.Path;

public class JDBCSamplerOptions implements SamplerOptions<JDBCSamplerProvider> {

    @SamplerParameter(
            paramLabel = "expression",
            description = "The operation to run on each request, e.g. query(\"SELECT 1\")")
    public String expression;

    @SamplerOption(names = "--url", description = "JDBC URL (e.g. jdbc:postgresql://localhost/mydb)", required = true)
    public String url;

    @SamplerOption(names = "--username", description = "Database username", required = true)
    public String username;

    @SamplerOption(names = "--password", description = "Database password", required = true)
    public String password;

    @SamplerOption(names = "--driver", description = "Path to JDBC driver JAR file", required = true)
    public Path driverPath;

    @SamplerOption(names = "--driver-class", description = "Fully-qualified JDBC driver class name (auto-detected if omitted)")
    public String driverClass;

    @SamplerOption(names = "--pool-size", description = "Maximum connection pool size (default: ${DEFAULT-VALUE})")
    public int poolSize = 10;

    private final JDBCSamplerPlugin samplerPlugin;

    public JDBCSamplerOptions(JDBCSamplerPlugin samplerPlugin) {
        this.samplerPlugin = samplerPlugin;
    }

    @Override
    public JDBCSamplerProvider samplerProvider() {
        return samplerPlugin.newSamplerProvider(this);
    }

    @Override
    public String commandDescription() {
        return "JDBC protocol, executes a SQL query against a database";
    }
}
