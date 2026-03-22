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

# Generate Benchmark Trend Charts
# Creates gnuplot charts from historical benchmark results

set -euo pipefail
export LC_NUMERIC=C

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Source library functions
# shellcheck source=lib/jmh-parser.sh
source "$SCRIPT_DIR/lib/jmh-parser.sh"

# Directories
RESULTS_DIR="$PROJECT_ROOT/benchmarks/results"
BASELINE_DIR="$PROJECT_ROOT/benchmarks/baseline"
CHARTS_DIR="$PROJECT_ROOT/benchmarks/charts"

# Chart format
CHART_FORMAT="png"

# Benchmark filter (regex)
BENCHMARK_FILTER=""

# Date filter
SINCE_DATE=""

# Help message
usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Generate trend charts from historical benchmark results using gnuplot.

OPTIONS:
    -b, --benchmark PATTERN   Generate chart for benchmarks matching pattern (regex)
    -s, --since DATE          Only include results since date (YYYY-MM-DD)
    -f, --format FORMAT       Output format: png, svg, pdf (default: png)
    -h, --help                Show this help message

EXAMPLES:
    # Generate charts for all benchmarks
    $(basename "$0")

    # Generate chart for specific benchmark
    $(basename "$0") --benchmark "executeRoadrunner.*"

    # Generate charts since specific date
    $(basename "$0") --since 2025-12-01

    # Generate SVG charts
    $(basename "$0") --format svg

REQUIREMENTS:
    - gnuplot must be installed
    - At least 2 result files in benchmarks/results/

EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        -b|--benchmark)
            BENCHMARK_FILTER="$2"
            shift 2
            ;;
        -s|--since)
            SINCE_DATE="$2"
            shift 2
            ;;
        -f|--format)
            CHART_FORMAT="$2"
            shift 2
            ;;
        *)
            echo "Error: Unknown option: $1" >&2
            usage
            exit 1
            ;;
    esac
done

# Validate gnuplot is installed
if ! command -v gnuplot &> /dev/null; then
    echo "Error: gnuplot is not installed" >&2
    echo "       Install with: sudo apt install gnuplot" >&2
    exit 1
fi

# Check if results directory exists and has files
if [[ ! -d "$RESULTS_DIR" ]]; then
    echo "Error: Results directory not found: $RESULTS_DIR" >&2
    exit 1
fi

RESULT_FILES=("$RESULTS_DIR"/*.json)
if [[ ! -f "${RESULT_FILES[0]}" ]]; then
    echo "Error: No result files found in $RESULTS_DIR" >&2
    echo "       Please run 'task benchmark:run' first" >&2
    exit 1
fi

if [[ ${#RESULT_FILES[@]} -lt 2 ]]; then
    echo "Warning: Only ${#RESULT_FILES[@]} result file(s) found" >&2
    echo "         At least 2 results are recommended for trend visualization" >&2
fi

# Create charts directory
mkdir -p "$CHARTS_DIR"

# Get list of all unique benchmarks across all result files
echo "Scanning result files..."
declare -A BENCHMARKS_MAP
for file in "${RESULT_FILES[@]}"; do
    if [[ ! -f "$file" ]]; then
        continue
    fi

    # Apply date filter if specified
    if [[ -n "$SINCE_DATE" ]]; then
        file_date=$(basename "$file" | grep -oP '^\d{4}-\d{2}-\d{2}')
        if [[ "$file_date" < "$SINCE_DATE" ]]; then
            continue
        fi
    fi

    mapfile -t file_benchmarks < <(jmh_get_benchmarks "$file" 2>/dev/null || echo "")
    for bench in "${file_benchmarks[@]}"; do
        if [[ -n "$bench" ]]; then
            BENCHMARKS_MAP["$bench"]=1
        fi
    done
done

BENCHMARKS=("${!BENCHMARKS_MAP[@]}")

if [[ ${#BENCHMARKS[@]} -eq 0 ]]; then
    echo "Error: No benchmarks found in result files" >&2
    exit 1
fi

echo "Found ${#BENCHMARKS[@]} unique benchmark(s)"

# Filter benchmarks if pattern specified
if [[ -n "$BENCHMARK_FILTER" ]]; then
    FILTERED_BENCHMARKS=()
    for bench in "${BENCHMARKS[@]}"; do
        if [[ "$bench" =~ $BENCHMARK_FILTER ]]; then
            FILTERED_BENCHMARKS+=("$bench")
        fi
    done
    BENCHMARKS=("${FILTERED_BENCHMARKS[@]}")
    echo "After filtering: ${#BENCHMARKS[@]} benchmark(s) match pattern"
fi

if [[ ${#BENCHMARKS[@]} -eq 0 ]]; then
    echo "Error: No benchmarks match the specified filter" >&2
    exit 1
fi

# Set gnuplot terminal based on format
case "$CHART_FORMAT" in
    png)
        TERMINAL="pngcairo size 1400,800 enhanced font 'sans,10'"
        FILE_EXT="png"
        ;;
    svg)
        TERMINAL="svg size 1400,800 enhanced font 'sans,10'"
        FILE_EXT="svg"
        ;;
    pdf)
        TERMINAL="pdf size 14,8 enhanced font 'sans,10'"
        FILE_EXT="pdf"
        ;;
    *)
        echo "Error: Unsupported format: $CHART_FORMAT" >&2
        echo "       Supported: png, svg, pdf" >&2
        exit 1
        ;;
esac

echo "Generating charts in $CHART_FORMAT format..."
echo ""

# Load baseline if exists
BASELINE_SCORE=""
BASELINE_FILE="$BASELINE_DIR/baseline.json"

# Generate chart for each benchmark
for benchmark in "${BENCHMARKS[@]}"; do
    echo "Processing: $benchmark"

    # Create temporary data file
    DATA_FILE=$(mktemp /tmp/benchmark-data.XXXXXX.txt)
    trap 'rm -f "$DATA_FILE"' EXIT

    # Extract data for this benchmark
    jmh_get_all_scores "$benchmark" "$RESULTS_DIR" | while read -r timestamp score error; do
        # Apply date filter if specified
        if [[ -n "$SINCE_DATE" ]]; then
            data_date=$(echo "$timestamp" | cut -d' ' -f1)
            if [[ "$data_date" < "$SINCE_DATE" ]]; then
                continue
            fi
        fi
        echo "$timestamp $score $error"
    done > "$DATA_FILE"

    # Check if we have data
    if [[ ! -s "$DATA_FILE" ]]; then
        echo "  Warning: No data found for $benchmark"
        rm -f "$DATA_FILE"
        continue
    fi

    DATA_POINTS=$(wc -l < "$DATA_FILE")
    echo "  Data points: $DATA_POINTS"

    # Get unit for this benchmark (from first file that has it)
    UNIT=""
    for file in "${RESULT_FILES[@]}"; do
        if [[ -f "$file" ]]; then
            UNIT=$(jmh_get_unit "$file" "$benchmark" 2>/dev/null || echo "")
            if [[ -n "$UNIT" ]]; then
                break
            fi
        fi
    done

    # Get baseline score if available
    BASELINE_SCORE=""
    if [[ -f "$BASELINE_FILE" ]]; then
        BASELINE_SCORE=$(jmh_get_score "$BASELINE_FILE" "$benchmark" 2>/dev/null || echo "")
    fi

    # Sanitize benchmark name for filename
    SAFE_NAME="${benchmark//[^a-zA-Z0-9_-]/_}"
    OUTPUT_FILE="$CHARTS_DIR/${SAFE_NAME}_trend.$FILE_EXT"

    # Create gnuplot script
    GNUPLOT_SCRIPT=$(mktemp /tmp/gnuplot.XXXXXX.gp)
    trap 'rm -f "$DATA_FILE" "$GNUPLOT_SCRIPT"' EXIT

    cat > "$GNUPLOT_SCRIPT" <<EOF
set terminal $TERMINAL
set output '$OUTPUT_FILE'

# Style
set title '$benchmark\nPerformance Trend' font 'sans,14'
set xlabel 'Date' font 'sans,11'
set ylabel 'Score ($UNIT)' font 'sans,11'
set grid ytics xtics mxtics
set key outside right top

# Time formatting
set xdata time
set timefmt '%Y-%m-%d %H:%M:%S'
set format x '%m/%d\n%H:%M'

# Auto-rotate x labels if many points
set xtics rotate by -45

# Plot with error bars
EOF

    # Add baseline line if available
    if [[ -n "$BASELINE_SCORE" && "$BASELINE_SCORE" != "null" ]]; then
        cat >> "$GNUPLOT_SCRIPT" <<EOF
set arrow from graph 0, first $BASELINE_SCORE to graph 1, first $BASELINE_SCORE nohead lc rgb "red" dt 2 lw 2
EOF
    fi

    cat >> "$GNUPLOT_SCRIPT" <<EOF

plot '$DATA_FILE' using 1:2:3 with errorbars pt 7 ps 0.5 lc rgb "blue" title 'Score ± Error', \\
     '$DATA_FILE' using 1:2 with lines lw 2 lc rgb "blue" notitle
EOF

    # Add baseline to legend if available
    if [[ -n "$BASELINE_SCORE" && "$BASELINE_SCORE" != "null" ]]; then
        echo ", $BASELINE_SCORE with lines lc rgb 'red' dt 2 lw 2 title 'Baseline'" >> "$GNUPLOT_SCRIPT"
    fi

    # Execute gnuplot
    if gnuplot "$GNUPLOT_SCRIPT" 2>&1; then
        echo "  Chart saved: $OUTPUT_FILE"
    else
        echo "  Error: Failed to generate chart" >&2
    fi

    # Cleanup
    rm -f "$DATA_FILE" "$GNUPLOT_SCRIPT"
done

echo ""
echo "======================================"
echo "Chart Generation Complete"
echo "======================================"
echo "Charts saved to: $CHARTS_DIR"
echo "Format: $CHART_FORMAT"
echo ""
echo "View charts:"
find "$CHARTS_DIR" -maxdepth 1 -name "*.$FILE_EXT" -type f -print0 2>/dev/null | while IFS= read -r -d '' file; do
    echo "  $(basename "$file")"
done
echo ""
