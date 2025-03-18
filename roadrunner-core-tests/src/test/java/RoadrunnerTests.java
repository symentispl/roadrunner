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

import io.roadrunner.api.measurments.Sample;
import io.roadrunner.core.Bootstrap;
import io.roadrunner.protocols.vm.VmProtocolProvider;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RoadrunnerTests {
    @Test
    void generateLoad(@TempDir Path tempDir) throws Exception {
        io.roadrunner.api.measurments.Measurements measurements;
        try (var roadrunner = new Bootstrap()
                .withConcurrency(1)
                .withRequests(10)
                .withOutputDir(tempDir)
                .build()) {
            var protocolProvider = VmProtocolProvider.from(Duration.ofMillis(100));
            var protocol = protocolProvider.newProtocol();
            measurements = roadrunner.execute(() -> protocol::execute);
        }
        var samplesReader = measurements.samplesReader();
        assertThat(samplesReader).hasSize(10).allSatisfy(m -> {
            assertThat(m.startTime()).isGreaterThan(0);
            assertThat(m.stopTime()).isGreaterThan(m.startTime());
            assertThat(m.status()).isEqualTo(Sample.Status.OK);
        });
    }
}
