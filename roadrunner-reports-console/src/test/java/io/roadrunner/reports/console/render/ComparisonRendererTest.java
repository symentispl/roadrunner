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
package io.roadrunner.reports.console.render;

import static org.assertj.core.api.Assertions.assertThat;

import io.roadrunner.console.ConsoleTheme;
import io.roadrunner.reports.Comparability;
import io.roadrunner.reports.ComparisonModel;
import io.roadrunner.reports.LatencyOverTime;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.Tolerances;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComparisonRendererTest {

    private static ConsoleTheme dumbTheme() {
        return ConsoleTheme.of(false, false, 78);
    }

    private static ReportModel run(long p99, double throughput, double errorPercentage) {
        return new ReportModel(
                1000,
                1000,
                0,
                10.0,
                throughput,
                errorPercentage,
                1,
                8,
                120,
                5,
                15,
                p99,
                118,
                new long[] {50, 30, 15, 5},
                new long[] {8, 10, 12, 9},
                new long[] {1, 2, 2, 1},
                Map.of("200", 1000L),
                new LatencyOverTime(new long[] {5}, new long[] {15}, new long[] {p99}));
    }

    @Test
    void rendersEveryMetricWithBaselineCurrentAndChange() throws Exception {
        var model = ComparisonModel.of(run(100, 100.0, 0.0), run(105, 100.0, 0.0));

        var out = ComparisonRenderer.render(model, dumbTheme());

        assertThat(out).contains("Roadrunner comparison");
        assertThat(out).containsPattern("p99 +100 ms +105 ms +\\+5\\.0% +- +-");
        assertThat(out).containsPattern("throughput +100\\.0 req/s +100\\.0 req/s +\\+0\\.0%");
        assertThat(out).containsPattern("errors +0\\.00% +0\\.00% +\\+0\\.00 pp");
        assertThat(out).doesNotContain("["); // no ANSI escapes in a dumb terminal
    }

    @Test
    void marksTolerancesAndOverallVerdict() throws Exception {
        var tolerances = Tolerances.parse("p99:+10%,throughput:-5%");
        var model = ComparisonModel.of(run(100, 100.0, 0.0), run(125, 100.0, 0.0), tolerances);

        var out = ComparisonRenderer.render(model, dumbTheme());

        assertThat(out).containsPattern("p99 +100 ms +125 ms +\\+25\\.0% +\\+10\\.0% +FAILED x");
        assertThat(out).containsPattern("throughput .* -5\\.0% +PASSED \\+");
        assertThat(out).containsPattern("overall +FAILED x");
    }

    @Test
    void passedWhenWithinToleranceAndWhenNothingIsChecked() throws Exception {
        var model = ComparisonModel.of(run(100, 100.0, 0.0), run(100, 100.0, 0.0));

        assertThat(ComparisonRenderer.render(model, dumbTheme())).containsPattern("overall +PASSED \\+");
    }

    @Test
    void warnsWhenRunsAreNotComparable() throws Exception {
        var model = ComparisonModel.of(
                run(100, 100.0, 0.0),
                run(100, 100.0, 0.0),
                Tolerances.empty(),
                Comparability.incompatible(List.of("load model differs: closed vs open")));

        var out = ComparisonRenderer.render(model, dumbTheme());

        assertThat(out).contains("these runs may not be comparable");
        assertThat(out).contains("- load model differs: closed vs open");
    }

    @Test
    void showsNotApplicableForChangeFromZeroBaseline() throws Exception {
        var model = ComparisonModel.of(run(0, 100.0, 0.0), run(50, 100.0, 0.0));

        assertThat(ComparisonRenderer.render(model, dumbTheme())).containsPattern("p99 +0 ms +50 ms +n/a");
    }

    @Test
    void colourAndUnicodeAreOptIn() {
        var model = ComparisonModel.of(run(100, 100.0, 0.0), run(125, 100.0, 0.0), Tolerances.parse("p99:+10%"));

        var fancy = ComparisonRenderer.render(model, ConsoleTheme.of(true, true, 78));
        var plain = ComparisonRenderer.render(model, ConsoleTheme.of(false, false, 78));

        assertThat(fancy).contains("\u001b[31m").contains("╔").contains("✘");
        assertThat(plain).doesNotContain("\u001b").doesNotContain("╔").contains("+=");
        assertThat(plain.lines().map(String::length).distinct()).containsExactly(78);
    }
}
