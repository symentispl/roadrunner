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
package io.roadrunner;

import io.roadrunner.api.events.SamplerResponse;
import io.roadrunner.api.parameters.SamplerParameters;
import io.roadrunner.api.samplers.Sampler;
import io.roadrunner.samplers.spi.SamplerExtensionPoint;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Compares three ways of turning a resolved target + literal argument into a {@link Sampler},
 * at the frequency {@code SamplerProvider.newSampler()} is actually called (once per virtual
 * thread) — see the "Microbenchmarks" section of
 * docs/superpowers/specs/2026-07-13-sampler-method-extension-points-design.md. This does not
 * measure the per-request {@code execute()} hot path, which is unaffected by this design either
 * way.
 */
public class SamplerExtensionPointBenchmarks {

    public static class NoOpSamplerProvider {
        public Sampler query(String sql) {
            return parameters -> SamplerResponse.empty(0, 0);
        }
    }

    @State(Scope.Benchmark)
    public static class NoOpSamplerState {
        NoOpSamplerProvider fixture;
        private Sampler extensionPointSampler;
        private Sampler sampler;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            fixture = new NoOpSamplerProvider();
            sampler = fixture.query("SELECT 1");
            extensionPointSampler =
                    SamplerExtensionPoint.bind(fixture, "query(\"SELECT 1\")").get();
        }
    }

    @Benchmark
    @Fork(value = 1)
    public SamplerResponse<?> directDispatch(NoOpSamplerState state) {
        return state.sampler.execute(SamplerParameters.NONE);
    }

    @Benchmark
    @Fork(value = 1)
    public SamplerResponse<?> extensionPointDispatch(NoOpSamplerState state) throws Throwable {
        return state.extensionPointSampler.execute(SamplerParameters.NONE);
    }
}
