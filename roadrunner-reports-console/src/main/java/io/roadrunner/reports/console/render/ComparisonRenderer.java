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

import static java.util.Objects.requireNonNull;

import io.roadrunner.console.ConsoleTheme;
import io.roadrunner.reports.ComparisonMetric;
import io.roadrunner.reports.ComparisonModel;
import io.roadrunner.reports.MetricComparison;
import io.roadrunner.reports.ReportModel;
import io.roadrunner.reports.Tolerance;
import io.roadrunner.reports.Verdict;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.apache.commons.text.StringSubstitutor;

/**
 * Renders a {@link ComparisonModel} as plain text: the two runs side by side, a warning when their manifests
 * differ, and a {@code metric | baseline | current | change | tolerance | verdict} table. The layout lives in
 * {@code comparison.txt.tmpl}; this class only computes the values and the row blocks. Fixed width, so the
 * output is deterministic whatever the terminal size. CI logs are text, and this table is what gets read there.
 */
public final class ComparisonRenderer {

    private static final String TEMPLATE = "/reports/console/comparison.txt.tmpl";

    private ComparisonRenderer() {}

    public static String render(ComparisonModel model, ConsoleTheme theme) {
        var width = theme.width();
        var runs = List.of(
                String.format(
                        Locale.ROOT, "%-10s %10s %10s %14s %12s", "run", "requests", "duration", "throughput", "error rate"),
                runRow("baseline", model.baseline()),
                runRow("current", model.current()));

        var rows = new ArrayList<String>();
        rows.add(String.format(
                Locale.ROOT, "%-11s %12s %12s %10s %10s  %s", "metric", "baseline", "current", "change", "tolerance", "verdict"));
        Arrays.stream(ComparisonMetric.values())
                .map(metric -> metricRow(model.metric(metric), theme))
                .forEach(rows::add);
        rows.add("");
        rows.add("overall     " + verdict(model.verdict(), theme));

        var values = new HashMap<String, String>();
        values.put("runs", theme.box("Roadrunner comparison", runs, width));
        values.put("warning", warning(model, theme));
        values.put("metrics", theme.box("Metrics", rows, width));

        // No substitution inside values: mismatch descriptions may contain "${...}".
        var substitutor = new StringSubstitutor(values);
        substitutor.setDisableSubstitutionInValues(true);
        return substitutor.replace(template());
    }

    private static String warning(ComparisonModel model, ConsoleTheme theme) {
        if (model.comparability().isComparable()) {
            return "";
        }
        var lines = new ArrayList<String>();
        lines.add(theme.coloredIcon(ConsoleTheme.Icon.WARN)
                + theme.paint(" these runs may not be comparable", ConsoleTheme.YELLOW));
        model.comparability().mismatches().forEach(mismatch -> lines.add("  - " + mismatch));
        return theme.box("Warning", lines, theme.width()) + "\n";
    }

    private static String template() {
        try (var in = requireNonNull(ComparisonRenderer.class.getResourceAsStream(TEMPLATE), TEMPLATE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String runRow(String name, ReportModel m) {
        return String.format(
                Locale.ROOT,
                "%-10s %,10d %9.1fs %10.1f r/s %11.2f%%",
                name,
                m.total(),
                m.durationSeconds(),
                m.throughput(),
                m.errorPercentage());
    }

    private static String metricRow(MetricComparison c, ConsoleTheme theme) {
        var errors = c.metric() == ComparisonMetric.ERRORS;
        return String.format(
                Locale.ROOT,
                "%-11s %12s %12s %s %10s  %s",
                c.metric().label(),
                value(c.metric(), c.baselineValue()),
                value(c.metric(), c.currentValue()),
                coloredChange(c, errors, theme),
                c.tolerance().map(t -> limit(t, errors)).orElse("-"),
                verdict(c.verdict(), theme));
    }

    /** Padded first, coloured second: escape codes must not count towards the column width. */
    private static String coloredChange(MetricComparison c, boolean errors, ConsoleTheme theme) {
        var text = String.format(Locale.ROOT, "%10s", change(c.delta(), errors));
        if (Double.isInfinite(c.delta()) || c.delta() == 0) {
            return text;
        }
        var better = c.metric() == ComparisonMetric.THROUGHPUT ? c.delta() > 0 : c.delta() < 0;
        return theme.paint(text, better ? ConsoleTheme.GREEN : ConsoleTheme.RED);
    }

    private static String value(ComparisonMetric metric, double value) {
        return switch (metric) {
            case THROUGHPUT -> String.format(Locale.ROOT, "%.1f req/s", value);
            case ERRORS -> String.format(Locale.ROOT, "%.2f%%", value);
            default -> String.format(Locale.ROOT, "%.0f ms", value);
        };
    }

    // errors are percentage points of error rate, everything else is relative change
    private static String change(double delta, boolean errors) {
        if (Double.isInfinite(delta)) {
            return "n/a";
        }
        return errors
                ? String.format(Locale.ROOT, "%+.2f pp", delta)
                : String.format(Locale.ROOT, "%+.1f%%", delta);
    }

    private static String limit(Tolerance tolerance, boolean errors) {
        return errors
                ? String.format(Locale.ROOT, "%+.2f pp", tolerance.limit())
                : String.format(Locale.ROOT, "%+.1f%%", tolerance.limit());
    }

    private static String verdict(Verdict verdict, ConsoleTheme theme) {
        return switch (verdict) {
            case PASSED -> theme.paint("PASSED " + theme.icon(ConsoleTheme.Icon.OK), ConsoleTheme.GREEN);
            case FAILED -> theme.paint("FAILED " + theme.icon(ConsoleTheme.Icon.FAIL), ConsoleTheme.RED);
            case UNCHECKED -> "-";
        };
    }
}
