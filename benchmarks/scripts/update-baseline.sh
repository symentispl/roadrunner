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

# Update Baseline Reference
# Sets a new baseline for regression detection

set -euo pipefail
export LC_NUMERIC=C

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Source library functions
# shellcheck source=lib/jmh-parser.sh
source "$SCRIPT_DIR/lib/jmh-parser.sh"
# shellcheck source=lib/stats.sh
source "$SCRIPT_DIR/lib/stats.sh"

# Directories
RESULTS_DIR="$PROJECT_ROOT/benchmarks/results"
BASELINE_DIR="$PROJECT_ROOT/benchmarks/baseline"
BASELINE_FILE="$BASELINE_DIR/baseline.json"
BASELINE_PREVIOUS="$BASELINE_DIR/baseline.previous.json"

# Help message
usage() {
    cat <<EOF
Usage: $(basename "$0") [FILE]

Update the baseline reference for regression detection.

ARGUMENTS:
    FILE    Path to result file to use as baseline (optional)
            If not provided, uses the most recent result file

OPTIONS:
    -h, --help    Show this help message

EXAMPLES:
    # Use most recent result as baseline
    $(basename "$0")

    # Use specific result file as baseline
    $(basename "$0") benchmarks/results/2025-12-12_16-30-45_6a4f233.json

NOTES:
    - Previous baseline is backed up to baseline.previous.json
    - Only valid JMH JSON files can be set as baseline
    - Baseline should represent stable, acceptable performance

EOF
}

# Parse command line arguments
SOURCE_FILE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        *)
            if [[ -z "$SOURCE_FILE" ]]; then
                SOURCE_FILE="$1"
                shift
            else
                echo "Error: Unexpected argument: $1" >&2
                usage
                exit 1
            fi
            ;;
    esac
done

# If no source file specified, use most recent result
if [[ -z "$SOURCE_FILE" ]]; then
    SOURCE_FILE=$(find "$RESULTS_DIR" -maxdepth 1 -name "*.json" -type f -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -n 1 | cut -d' ' -f2- || echo "")
    if [[ -z "$SOURCE_FILE" || ! -f "$SOURCE_FILE" ]]; then
        echo "Error: No result files found in $RESULTS_DIR" >&2
        echo "       Please run 'task benchmark:run' first" >&2
        exit 1
    fi
    echo "Using most recent result: $SOURCE_FILE"
fi

# Validate source file exists
if [[ ! -f "$SOURCE_FILE" ]]; then
    echo "Error: Source file not found: $SOURCE_FILE" >&2
    exit 1
fi

# Validate source file is valid JMH JSON
if ! jmh_validate "$SOURCE_FILE" 2>/dev/null; then
    echo "Error: Source file is not valid JMH JSON" >&2
    exit 1
fi

# Create baseline directory if needed
mkdir -p "$BASELINE_DIR"

# Backup existing baseline if it exists
if [[ -f "$BASELINE_FILE" ]]; then
    echo "Backing up previous baseline to: $BASELINE_PREVIOUS"
    cp "$BASELINE_FILE" "$BASELINE_PREVIOUS"
fi

# Copy source file to baseline
echo "Setting new baseline from: $SOURCE_FILE"
cp "$SOURCE_FILE" "$BASELINE_FILE"

echo ""
echo "======================================"
echo "Baseline Updated Successfully"
echo "======================================"
echo "Baseline file: $BASELINE_FILE"
echo ""

# Display baseline metadata and benchmarks
echo "Baseline Metadata:"
echo "  Timestamp: $(jmh_get_metadata "$BASELINE_FILE" "timestamp")"
echo "  Git Commit: $(jmh_get_metadata "$BASELINE_FILE" "git_commit")"
echo "  Git Branch: $(jmh_get_metadata "$BASELINE_FILE" "git_branch")"
echo "  Hostname: $(jmh_get_metadata "$BASELINE_FILE" "hostname")"
echo ""

echo "Benchmarks in baseline:"
mapfile -t BENCHMARKS < <(jmh_get_benchmarks "$BASELINE_FILE")
for bench in "${BENCHMARKS[@]}"; do
    score=$(jmh_get_score "$BASELINE_FILE" "$bench")
    error=$(jmh_get_score_error "$BASELINE_FILE" "$bench")
    unit=$(jmh_get_unit "$BASELINE_FILE" "$bench")
    echo "  $bench: $(format_score "$score" "$error" "$unit")"
done

echo ""
echo "Future benchmark runs will be compared against this baseline."
echo "Run 'task benchmark:check' to compare new results against this baseline."
echo ""
