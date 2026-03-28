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
package io.roadrunner.samplers.neo4j;

import io.roadrunner.samplers.spi.SamplerOptions;
import java.net.URI;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(description = "Neo4j sampler")
public class Neo4jSamplerOptions implements SamplerOptions<Neo4jSamplerProvider> {
    @Parameters(paramLabel = "query", description = "Neo4j query")
    public String query;

    @Option(names = "--uri", description = "Neo4j database uri", required = true)
    public URI uri;

    @Option(names = "--username", description = "Neo4j database username", required = true)
    public String username;

    @Option(names = "--password", description = "Neo4j database password", required = true)
    public String password;

    private final Neo4jSamplerPlugin neo4jSamplerPlugin;

    public Neo4jSamplerOptions(Neo4jSamplerPlugin neo4jSamplerPlugin) {
        this.neo4jSamplerPlugin = neo4jSamplerPlugin;
    }

    @Override
    public Neo4jSamplerProvider samplerProvider() {
        return neo4jSamplerPlugin.newSamplerProvider(this);
    }
}
