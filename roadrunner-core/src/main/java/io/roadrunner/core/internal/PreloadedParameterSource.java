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

import io.roadrunner.api.parameters.ParameterFeed;
import io.roadrunner.api.parameters.ParameterSource;
import io.roadrunner.api.parameters.SamplerParameters;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PreloadedParameterSource implements ParameterSource {
    private static final Logger LOG = LoggerFactory.getLogger(PreloadedParameterSource.class);

    public static ParameterSource from(ParameterSource parameterSource) throws Exception {
        LOG.info("Pre-loading parameters from {} source", parameterSource);
        try (ParameterFeed feed = parameterSource.load()) {
            var parameters = StreamSupport.stream(feed.spliterator(), false).toArray(SamplerParameters[]::new);
            return new PreloadedParameterSource(parameters);
        }
    }

    private final SamplerParameters[] parameters;

    PreloadedParameterSource(SamplerParameters[] parameters) {
        this.parameters = parameters;
    }

    @Override
    public ParameterFeed load() {
        return new PreloadedParameterFeed(parameters);
    }
}
