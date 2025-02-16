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
import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.api.Measurements;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.protocols.vm.VmProtocolOptions;
import io.roadrunner.protocols.vm.VmProtocolProvider;
import java.time.Duration;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

public class RoadrunnerTests {
    @Test
    void test() throws Exception {
        var roadrunner = new Bootstrap().withConcurrency(1).withRequests(10).build();
        var vmProtocol = new VmProtocolProvider();
        var request = vmProtocol.request(new VmProtocolOptions(Duration.ofMillis(100)));
        var measurements = roadrunner.execute(() -> request::execute);
        assertThat(measurements.totalCount()).isEqualTo(10);
        assertThat(measurements)
                .extracting(
                        Measurements::minValue,
                        Measurements::maxValue,
                        Measurements::mean,
                        Measurements::p50,
                        Measurements::p90,
                        Measurements::p99,
                        Measurements::p999)
                .asInstanceOf(InstanceOfAssertFactories.list(Double.class))
                .allSatisfy(n -> {
                    assertThat(n).isGreaterThanOrEqualTo(100);
                });
    }
}
