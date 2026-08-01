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
package io.roadrunner.reports;

/**
 * Latency percentiles per time slice, in milliseconds, aligned with
 * {@link ReportModel#throughputSeries()}. Slices with no responses hold zero.
 *
 * <p>These come from the raw samples even when the summary percentiles come from a
 * pause-corrected snapshot: the snapshot is a merge of interval histograms and carries no time
 * dimension, so it cannot be sliced.
 */
public record LatencyOverTime(long[] p50, long[] p90, long[] p99) {}
