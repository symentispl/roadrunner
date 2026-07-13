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
import io.roadrunner.api.samplers.Sampler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
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
public class SamplerFactoryDispatchBenchmarks {

    public static class DispatchFixture {
        public Sampler query(String sql) {
            return parameters -> SamplerResponse.empty(0, 0);
        }
    }

    @State(Scope.Benchmark)
    public static class DispatchState {
        DispatchFixture fixture;
        String sql;
        MethodHandle boundHandle;
        Method reflectMethod;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            fixture = new DispatchFixture();
            sql = "SELECT 1";
            reflectMethod = DispatchFixture.class.getMethod("query", String.class);
            MethodHandle handle = MethodHandles.publicLookup().unreflect(reflectMethod);
            boundHandle = MethodHandles.insertArguments(handle, 0, fixture, sql);
        }
    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    public Sampler directDispatch(DispatchState state) {
        return state.fixture.query(state.sql);
    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    public Sampler methodHandleDispatch(DispatchState state) throws Throwable {
        return (Sampler) state.boundHandle.invoke();
    }

    @Benchmark
    @Fork(value = 1, warmups = 1)
    public Sampler reflectionDispatch(DispatchState state) throws Exception {
        return (Sampler) state.reflectMethod.invoke(state.fixture, state.sql);
    }
}
