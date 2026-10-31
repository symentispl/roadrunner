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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutputDirectoryCreatorTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsNullBasePath() {
        assertThatNullPointerException()
                .isThrownBy(() -> OutputDirectoryCreator.create(null))
                .withMessage("basePath cannot be null");
    }

    @Test
    void rejectsRootPathWithoutDirectoryName() {
        var root = tempDir.toAbsolutePath().getRoot();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> OutputDirectoryCreator.create(root))
                .withMessage("basePath must contain a directory name");
    }

    @Test
    void rejectsEmptyDirectoryName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OutputDirectoryCreator.create(Path.of("")))
                .withMessage("basePath must contain a directory name");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 9, 99})
    void appendsNextSuffixWhenCreatingRepeatedRuns(int occupiedCount) throws IOException {
        var basePath = tempDir.resolve("roadrunner-20261023-120000");
        for (int run = 0; run < occupiedCount; run++) {
            OutputDirectoryCreator.create(basePath);
        }

        var created = OutputDirectoryCreator.create(basePath);

        assertThat(created)
                .isEqualTo(tempDir.resolve("roadrunner-20261023-120000-" + (occupiedCount + 1)))
                .isDirectory();
        assertThat(basePath).isDirectory();
        for (int suffix = 2; suffix <= occupiedCount; suffix++) {
            assertThat(tempDir.resolve("roadrunner-20261023-120000-" + suffix)).isDirectory();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 5})
    void usesFirstGapBeforeHigherOccupiedSuffix(int missingSuffix) throws IOException {
        var basePath = Files.createDirectory(tempDir.resolve("roadrunner-20261023-120000"));
        for (int suffix = 2; suffix < missingSuffix; suffix++) {
            Files.createDirectory(tempDir.resolve("roadrunner-20261023-120000-" + suffix));
        }
        var higherSuffix = Files.createDirectory(
                tempDir.resolve("roadrunner-20261023-120000-" + (missingSuffix + 1)));

        var created = OutputDirectoryCreator.create(basePath);

        assertThat(created)
                .isEqualTo(tempDir.resolve("roadrunner-20261023-120000-" + missingSuffix))
                .isDirectory();
        assertThat(higherSuffix).isDirectory();
    }
}
