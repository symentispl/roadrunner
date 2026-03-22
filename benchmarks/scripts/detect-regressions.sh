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

# Detect Performance Regressions
# Compares benchmark results and identifies performance degradations

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

# Default thresholds (percentage)
WARNING_THRESHOLD=5
CRITICAL_THRESHOLD=10

# Output mode
JSON_OUTPUT=false

# Comparison mode
MODE=""
BASELINE=""
CURRENT=""

# Help message
usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Detect performance regressions by comparing benchmark results.

MODES:
    --latest                    Compare latest result against baseline
    --compare FILE1 FILE2       Compare two specific result files

OPTIONS:
    -w, --warning PCT          Warning threshold percentage (default: 5)
    -c, --critical PCT         Critical threshold percentage (default: 10)
    --json                     Output results in JSON format
    -h, --help                 Show this help message

EXAMPLES:
    # Compare latest result with baseline
    $(basename "$0") --latest

    # Compare two specific files
    $(basename "$0") --compare results/2025-12-10_*.json results/2025-12-12_*.json

    # Use custom thresholds
    $(basename "$0") --latest --warning 3 --critical 8

    # Output as JSON for automation
    $(basename "$0") --latest --json

EXIT CODES:
    0 = No regressions detected
    1 = Warning-level regression detected
    2 = Critical regression detected

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --latest)
            MODE="latest"
            shift
            ;;
        --compare)
            MODE="compare"
            BASELINE="$2"
            CURRENT="$3"
            shift 3
            ;;
        -w|--warning)
            WARNING_THRESHOLD="$2"
            shift 2
            ;;
        -c|--critical)
            CRITICAL_THRESHOLD="$2"
            shift 2
            ;;
        --json)
            JSON_OUTPUT=true
            shift
            ;;
        *)
            echo "Error: Unknown option: $1" >&2
            usage
            exit 1
            ;;
    esac
done

# Validate mode
if [[ -z "$MODE" ]]; then
    echo "Error: Must specify --latest or --compare" >&2
    usage
    exit 1
fi

# Determine baseline and current files
if [[ "$MODE" == "latest" ]]; then
    if [[ ! -f "$BASELINE_FILE" ]]; then
        echo "Error: Baseline file not found: $BASELINE_FILE" >&2
        echo "       Please run 'task benchmark:baseline:update' to set a baseline" >&2
        exit 1
    fi

    # Find most recent result file
    CURRENT=$(find "$RESULTS_DIR" -maxdepth 1 -name "*.json" -type f -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -n 1 | cut -d' ' -f2- || echo "")
    if [[ -z "$CURRENT" || ! -f "$CURRENT" ]]; then
        echo "Error: No result files found in $RESULTS_DIR" >&2
        echo "       Please run 'task benchmark:run' first" >&2
        exit 1
    fi

    BASELINE="$BASELINE_FILE"
fi

# Validate files exist
if [[ ! -f "$BASELINE" ]]; then
    echo "Error: Baseline file not found: $BASELINE" >&2
    exit 1
fi

if [[ ! -f "$CURRENT" ]]; then
    echo "Error: Current file not found: $CURRENT" >&2
    exit 1
fi

# Validate files are valid JMH JSON
if ! jmh_validate "$BASELINE" 2>/dev/null; then
    echo "Error: Baseline file is not valid JMH JSON" >&2
    exit 1
fi

if ! jmh_validate "$CURRENT" 2>/dev/null; then
    echo "Error: Current file is not valid JMH JSON" >&2
    exit 1
fi

# Get benchmark lists from both files
mapfile -t BASELINE_BENCHMARKS < <(jmh_get_benchmarks "$BASELINE")
mapfile -t CURRENT_BENCHMARKS < <(jmh_get_benchmarks "$CURRENT")

# Find common benchmarks
COMMON_BENCHMARKS=()
for bench in "${BASELINE_BENCHMARKS[@]}"; do
    for curr_bench in "${CURRENT_BENCHMARKS[@]}"; do
        if [[ "$bench" == "$curr_bench" ]]; then
            COMMON_BENCHMARKS+=("$bench")
            break
        fi
    done
done

if [[ ${#COMMON_BENCHMARKS[@]} -eq 0 ]]; then
    echo "Error: No common benchmarks found between files" >&2
    exit 1
fi

# Counters for summary
REGRESSION_COUNT=0
CRITICAL_COUNT=0
IMPROVEMENT_COUNT=0
OK_COUNT=0
EXIT_CODE=0

# Results array for JSON output
declare -a RESULTS_JSON=()

# Process each benchmark
for benchmark in "${COMMON_BENCHMARKS[@]}"; do
    # Get scores
    baseline_score=$(jmh_get_score "$BASELINE" "$benchmark")
    current_score=$(jmh_get_score "$CURRENT" "$benchmark")

    # Get errors
    baseline_error=$(jmh_get_score_error "$BASELINE" "$benchmark")
    current_error=$(jmh_get_score_error "$CURRENT" "$benchmark")

    # Get unit
    unit=$(jmh_get_unit "$BASELINE" "$benchmark")

    # Calculate percentage change
    pct_change=$(calc_pct_change "$baseline_score" "$current_score")

    # Check if change is statistically significant
    if is_significant "$pct_change" "$baseline_error" "$baseline_score"; then
        is_sig="true"
    else
        is_sig="false"
    fi

    # Determine status
    status=$(get_status "$pct_change" "$WARNING_THRESHOLD" "$CRITICAL_THRESHOLD" "$is_sig")

    # Update counters and exit code
    case "$status" in
        CRITICAL_REGRESSION)
            ((CRITICAL_COUNT++))
            EXIT_CODE=2
            ;;
        REGRESSION)
            ((REGRESSION_COUNT++))
            if [[ $EXIT_CODE -eq 0 ]]; then
                EXIT_CODE=1
            fi
            ;;
        IMPROVEMENT)
            ((IMPROVEMENT_COUNT++))
            ;;
        OK)
            ((OK_COUNT++))
            ;;
    esac

    # Store result for JSON output
    if [[ "$JSON_OUTPUT" == "true" ]]; then
        RESULT_JSON=$(jq -n \
            --arg bench "$benchmark" \
            --arg baseline "$baseline_score" \
            --arg current "$current_score" \
            --arg baseline_error "$baseline_error" \
            --arg current_error "$current_error" \
            --arg unit "$unit" \
            --arg pct_change "$pct_change" \
            --arg status "$status" \
            --argjson significant "$is_sig" \
            '{
                benchmark: $bench,
                baseline: {
                    score: ($baseline | tonumber),
                    error: ($baseline_error | tonumber),
                    unit: $unit
                },
                current: {
                    score: ($current | tonumber),
                    error: ($current_error | tonumber),
                    unit: $unit
                },
                change_pct: ($pct_change | tonumber),
                status: $status,
                significant: $significant
            }')
        RESULTS_JSON+=("$RESULT_JSON")
    fi
done

# Output results
if [[ "$JSON_OUTPUT" == "true" ]]; then
    # JSON output
    BASELINE_META=$(jq -n \
        --arg file "$BASELINE" \
        --arg timestamp "$(jmh_get_metadata "$BASELINE" "timestamp")" \
        --arg commit "$(jmh_get_metadata "$BASELINE" "git_commit")" \
        '{
            file: $file,
            timestamp: $timestamp,
            commit: $commit
        }')

    CURRENT_META=$(jq -n \
        --arg file "$CURRENT" \
        --arg timestamp "$(jmh_get_metadata "$CURRENT" "timestamp")" \
        --arg commit "$(jmh_get_metadata "$CURRENT" "git_commit")" \
        '{
            file: $file,
            timestamp: $timestamp,
            commit: $commit
        }')

    SUMMARY=$(jq -n \
        --argjson regressions "$REGRESSION_COUNT" \
        --argjson critical "$CRITICAL_COUNT" \
        --argjson improvements "$IMPROVEMENT_COUNT" \
        --argjson ok "$OK_COUNT" \
        --argjson exit_code "$EXIT_CODE" \
        '{
            regressions: $regressions,
            critical_regressions: $critical,
            improvements: $improvements,
            ok: $ok,
            exit_code: $exit_code
        }')

    # Combine all results
    printf '%s\n' "${RESULTS_JSON[@]}" | jq -s \
        --argjson baseline "$BASELINE_META" \
        --argjson current "$CURRENT_META" \
        --argjson summary "$SUMMARY" \
        '{
            baseline: $baseline,
            current: $current,
            results: .,
            summary: $summary
        }'
else
    # Human-readable output
    echo "======================================"
    echo "Benchmark Regression Analysis"
    echo "======================================"
    echo "Baseline: $BASELINE"
    echo "  Timestamp: $(jmh_get_metadata "$BASELINE" "timestamp")"
    echo "  Commit: $(jmh_get_metadata "$BASELINE" "git_commit")"
    echo ""
    echo "Current: $CURRENT"
    echo "  Timestamp: $(jmh_get_metadata "$CURRENT" "timestamp")"
    echo "  Commit: $(jmh_get_metadata "$CURRENT" "git_commit")"
    echo ""
    echo "Thresholds:"
    echo "  Warning: ${WARNING_THRESHOLD}%"
    echo "  Critical: ${CRITICAL_THRESHOLD}%"
    echo ""
    echo "======================================"
    echo "Results"
    echo "======================================"

    for benchmark in "${COMMON_BENCHMARKS[@]}"; do
        baseline_score=$(jmh_get_score "$BASELINE" "$benchmark")
        current_score=$(jmh_get_score "$CURRENT" "$benchmark")
        baseline_error=$(jmh_get_score_error "$BASELINE" "$benchmark")
        current_error=$(jmh_get_score_error "$CURRENT" "$benchmark")
        unit=$(jmh_get_unit "$BASELINE" "$benchmark")
        pct_change=$(calc_pct_change "$baseline_score" "$current_score")

        if is_significant "$pct_change" "$baseline_error" "$baseline_score"; then
            is_sig="true"
        else
            is_sig="false"
        fi

        status=$(get_status "$pct_change" "$WARNING_THRESHOLD" "$CRITICAL_THRESHOLD" "$is_sig")

        echo ""
        echo "$benchmark:"
        echo "  Baseline: $(format_score "$baseline_score" "$baseline_error" "$unit")"
        echo "  Current:  $(format_score "$current_score" "$current_error" "$unit")"
        echo "  Change:   $(format_pct_change "$pct_change")"

        case "$status" in
            CRITICAL_REGRESSION)
                echo "  Status:   🔴 CRITICAL REGRESSION (exceeds ${CRITICAL_THRESHOLD}% threshold)"
                ;;
            REGRESSION)
                echo "  Status:   ⚠️  REGRESSION (exceeds ${WARNING_THRESHOLD}% threshold)"
                ;;
            IMPROVEMENT)
                echo "  Status:   ✅ IMPROVEMENT"
                ;;
            OK)
                if [[ "$is_sig" == "true" ]]; then
                    echo "  Status:   ✓ OK (within threshold)"
                else
                    echo "  Status:   ✓ OK (not significant)"
                fi
                ;;
        esac
    done

    echo ""
    echo "======================================"
    echo "Summary"
    echo "======================================"
    echo "Critical regressions: $CRITICAL_COUNT"
    echo "Regressions: $REGRESSION_COUNT"
    echo "Improvements: $IMPROVEMENT_COUNT"
    echo "OK: $OK_COUNT"
    echo ""

    if [[ $CRITICAL_COUNT -gt 0 ]]; then
        echo "🔴 CRITICAL: Performance regression detected!"
    elif [[ $REGRESSION_COUNT -gt 0 ]]; then
        echo "⚠️  WARNING: Performance regression detected"
    elif [[ $IMPROVEMENT_COUNT -gt 0 ]]; then
        echo "✅ Performance improved!"
    else
        echo "✓ No significant changes detected"
    fi
fi

exit $EXIT_CODE
