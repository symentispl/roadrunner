#!/usr/bin/env bash
#
# Copyright 2024 Symentis.pl
# <p>
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# <p>
# http://www.apache.org/licenses/LICENSE-2.0
# <p>
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Collect JMH Benchmark Results with Metadata
# Runs JMH benchmarks and wraps results with system/git metadata

set -euo pipefail
export LC_NUMERIC=C

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Source library functions
# shellcheck source=lib/stats.sh
source "$SCRIPT_DIR/lib/stats.sh"

# Output directories
RESULTS_DIR="$PROJECT_ROOT/benchmarks/results"
BENCHMARKS_JAR="$PROJECT_ROOT/roadrunner-microbenchmarks/target/benchmarks.jar"

# Temporary files
TMP_JMH_OUTPUT=$(mktemp /tmp/jmh-output.XXXXXX.json)

# Cleanup on exit
cleanup() {
    rm -f "$TMP_JMH_OUTPUT"
}
trap cleanup EXIT

# Help message
usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Run JMH benchmarks and collect results with metadata.

OPTIONS:
    -h, --help              Show this help message
    -o, --output DIR        Output directory (default: benchmarks/results)
    -j, --jmh-args ARGS     Additional arguments to pass to JMH

EXAMPLES:
    # Run benchmarks with default settings
    $(basename "$0")

    # Run specific benchmark
    $(basename "$0") --jmh-args ".*executeRoadrunner.*"

    # Run with custom iterations
    $(basename "$0") --jmh-args "-i 5 -wi 2"

EOF
}

# Parse command line arguments
OUTPUT_DIR="$RESULTS_DIR"
JMH_ARGS=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -j|--jmh-args)
            JMH_ARGS="$2"
            shift 2
            ;;
        *)
            echo "Error: Unknown option: $1" >&2
            usage
            exit 1
            ;;
    esac
done

# Validate environment
if [[ ! -d "$PROJECT_ROOT" ]]; then
    echo "Error: Cannot find project root directory" >&2
    exit 1
fi

if [[ ! -f "$BENCHMARKS_JAR" ]]; then
    echo "Error: Benchmarks JAR not found at: $BENCHMARKS_JAR" >&2
    echo "       Please run 'mvnd package' or 'task benchmark:run' first" >&2
    exit 1
fi

# Create output directory if needed
mkdir -p "$OUTPUT_DIR"

# Change to project root
cd "$PROJECT_ROOT"

# Warn if repository is dirty
warn_if_dirty

echo "======================================"
echo "Running JMH Benchmarks"
echo "======================================"
echo "Project: $PROJECT_ROOT"
echo "JAR: $BENCHMARKS_JAR"
echo "Output: $OUTPUT_DIR"
echo ""

# Collect metadata BEFORE running benchmark
echo "Collecting metadata..."
GIT_META=$(collect_git_metadata)
SYS_META=$(collect_system_info)
TIMESTAMP=$(get_timestamp)
FILENAME_TS=$(get_filename_timestamp)
PROJECT_VERSION=$(get_project_version)
GIT_COMMIT=$(echo "$GIT_META" | jq -r '.git_commit')

echo "  Git commit: $GIT_COMMIT"
echo "  Branch: $(echo "$GIT_META" | jq -r '.git_branch')"
echo "  Timestamp: $TIMESTAMP"
echo ""

# Run JMH benchmark with JSON output
echo "Running benchmarks (this may take several minutes)..."
echo ""

# Build JMH command
JMH_CMD="java -jar $BENCHMARKS_JAR -rf json -rff $TMP_JMH_OUTPUT"
if [[ -n "$JMH_ARGS" ]]; then
    JMH_CMD="$JMH_CMD $JMH_ARGS"
fi

# Execute JMH
if ! eval "$JMH_CMD"; then
    echo "Error: JMH benchmark execution failed" >&2
    exit 1
fi

echo ""
echo "======================================"
echo "Processing Results"
echo "======================================"

# Check if JMH output was created
if [[ ! -f "$TMP_JMH_OUTPUT" ]]; then
    echo "Error: JMH did not produce output file" >&2
    exit 1
fi

# Validate JMH output is valid JSON
if ! jq empty "$TMP_JMH_OUTPUT" > /dev/null 2>&1; then
    echo "Error: JMH output is not valid JSON" >&2
    exit 1
fi

# Create output filename
OUTPUT_FILE="$OUTPUT_DIR/${FILENAME_TS}_${GIT_COMMIT}.json"

# Build complete metadata object
METADATA=$(jq -n \
    --arg timestamp "$TIMESTAMP" \
    --arg jmh_version "1.37" \
    --arg project_version "$PROJECT_VERSION" \
    --argjson git "$GIT_META" \
    --argjson sys "$SYS_META" \
    '{
        timestamp: $timestamp,
        jmh_version: $jmh_version,
        project_version: $project_version,
        git_commit: $git.git_commit,
        git_branch: $git.git_branch,
        git_dirty: $git.git_dirty,
        hostname: $sys.hostname,
        os: $sys.os,
        arch: $sys.arch,
        cpu_model: $sys.cpu_model,
        java_version: $sys.java_version
    }')

# Merge metadata with JMH results
jq -n \
    --argjson metadata "$METADATA" \
    --slurpfile jmh_results "$TMP_JMH_OUTPUT" \
    '{
        metadata: $metadata,
        jmh_results: $jmh_results[0]
    }' > "$OUTPUT_FILE"

echo "Results saved to: $OUTPUT_FILE"
echo ""

# Display summary
BENCHMARK_COUNT=$(jq '[.jmh_results[].benchmark] | length' "$OUTPUT_FILE")
echo "Summary:"
echo "  Benchmarks run: $BENCHMARK_COUNT"
echo "  Timestamp: $TIMESTAMP"
echo "  Commit: $GIT_COMMIT"
echo ""

# Display benchmark results
echo "Benchmark Results:"
echo "------------------"
jq -r '.jmh_results[] |
    "\(.benchmark):\n  Score: \(.primaryMetric.score) ± \(.primaryMetric.scoreError) \(.primaryMetric.scoreUnit)"' \
    "$OUTPUT_FILE"

echo ""
echo "======================================"
echo "Done!"
echo "======================================"
echo ""
echo "Next steps:"
echo "  - Compare with baseline: ./benchmarks/scripts/detect-regressions.sh --latest"
echo "  - Update baseline: ./benchmarks/scripts/update-baseline.sh"
echo "  - Generate charts: ./benchmarks/scripts/generate-charts.sh"
echo ""

# Output file path for downstream processing
echo "$OUTPUT_FILE"
