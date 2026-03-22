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

# JMH JSON Parser Library
# Utility functions for parsing JMH benchmark results using jq

set -euo pipefail
export LC_NUMERIC=C

# Get list of all benchmark names from result file
# Usage: jmh_get_benchmarks <file>
# Returns: Array of benchmark names
jmh_get_benchmarks() {
    local file="$1"

    if [[ ! -f "$file" ]]; then
        echo "Error: File not found: $file" >&2
        return 1
    fi

    # Check if file has metadata wrapper or raw JMH output
    if jq -e '.jmh_results' "$file" > /dev/null 2>&1; then
        # Has metadata wrapper
        jq -r '.jmh_results[].benchmark' "$file"
    elif jq -e '.[0].benchmark' "$file" > /dev/null 2>&1; then
        # Raw JMH output
        jq -r '.[].benchmark' "$file"
    else
        echo "Error: Invalid JMH JSON format in $file" >&2
        return 1
    fi
}

# Get primary metric score (mean) for a specific benchmark
# Usage: jmh_get_score <file> <benchmark>
# Returns: Score value (float)
jmh_get_score() {
    local file="$1"
    local benchmark="$2"

    if [[ ! -f "$file" ]]; then
        echo "Error: File not found: $file" >&2
        return 1
    fi

    # Check if file has metadata wrapper or raw JMH output
    if jq -e '.jmh_results' "$file" > /dev/null 2>&1; then
        # Has metadata wrapper
        jq -r --arg bench "$benchmark" \
            '.jmh_results[] | select(.benchmark == $bench) | .primaryMetric.score' \
            "$file"
    elif jq -e '.[0].benchmark' "$file" > /dev/null 2>&1; then
        # Raw JMH output
        jq -r --arg bench "$benchmark" \
            '.[] | select(.benchmark == $bench) | .primaryMetric.score' \
            "$file"
    else
        echo "Error: Invalid JMH JSON format in $file" >&2
        return 1
    fi
}

# Get primary metric score error (confidence interval)
# Usage: jmh_get_score_error <file> <benchmark>
# Returns: Score error value (float)
jmh_get_score_error() {
    local file="$1"
    local benchmark="$2"

    if [[ ! -f "$file" ]]; then
        echo "Error: File not found: $file" >&2
        return 1
    fi

    # Check if file has metadata wrapper or raw JMH output
    if jq -e '.jmh_results' "$file" > /dev/null 2>&1; then
        # Has metadata wrapper
        jq -r --arg bench "$benchmark" \
            '.jmh_results[] | select(.benchmark == $bench) | .primaryMetric.scoreError' \
            "$file"
    elif jq -e '.[0].benchmark' "$file" > /dev/null 2>&1; then
        # Raw JMH output
        jq -r --arg bench "$benchmark" \
            '.[] | select(.benchmark == $bench) | .primaryMetric.scoreError' \
            "$file"
    else
        echo "Error: Invalid JMH JSON format in $file" >&2
        return 1
    fi
}

# Get primary metric unit
# Usage: jmh_get_unit <file> <benchmark>
# Returns: Unit string (e.g., "ms/op")
jmh_get_unit() {
    local file="$1"
    local benchmark="$2"

    if [[ ! -f "$file" ]]; then
        echo "Error: File not found: $file" >&2
        return 1
    fi

    # Check if file has metadata wrapper or raw JMH output
    if jq -e '.jmh_results' "$file" > /dev/null 2>&1; then
        # Has metadata wrapper
        jq -r --arg bench "$benchmark" \
            '.jmh_results[] | select(.benchmark == $bench) | .primaryMetric.scoreUnit' \
            "$file"
    elif jq -e '.[0].benchmark' "$file" > /dev/null 2>&1; then
        # Raw JMH output
        jq -r --arg bench "$benchmark" \
            '.[] | select(.benchmark == $bench) | .primaryMetric.scoreUnit' \
            "$file"
    else
        echo "Error: Invalid JMH JSON format in $file" >&2
        return 1
    fi
}

# Get benchmark mode
# Usage: jmh_get_mode <file> <benchmark>
# Returns: Mode string (e.g., "ss" for single-shot)
jmh_get_mode() {
    local file="$1"
    local benchmark="$2"

    if [[ ! -f "$file" ]]; then
        echo "Error: File not found: $file" >&2
        return 1
    fi

    # Check if file has metadata wrapper or raw JMH output
    if jq -e '.jmh_results' "$file" > /dev/null 2>&1; then
        # Has metadata wrapper
        jq -r --arg bench "$benchmark" \
            '.jmh_results[] | select(.benchmark == $bench) | .mode' \
            "$file"
    elif jq -e '.[0].benchmark' "$file" > /dev/null 2>&1; then
        # Raw JMH output
        jq -r --arg bench "$benchmark" \
            '.[] | select(.benchmark == $bench) | .mode' \
            "$file"
    else
        echo "Error: Invalid JMH JSON format in $file" >&2
        return 1
    fi
}

# Get metadata field from result file (only works with wrapped format)
# Usage: jmh_get_metadata <file> <key>
# Returns: Metadata value
jmh_get_metadata() {
    local file="$1"
    local key="$2"

    if [[ ! -f "$file" ]]; then
        echo "Error: File not found: $file" >&2
        return 1
    fi

    if jq -e '.metadata' "$file" > /dev/null 2>&1; then
        jq -r --arg key "$key" '.metadata[$key] // "N/A"' "$file"
    else
        echo "N/A"
    fi
}

# Get all scores for a specific benchmark across multiple result files
# Usage: jmh_get_all_scores <benchmark> <directory>
# Returns: Lines of "timestamp score error" (space-separated)
jmh_get_all_scores() {
    local benchmark="$1"
    local directory="$2"

    if [[ ! -d "$directory" ]]; then
        echo "Error: Directory not found: $directory" >&2
        return 1
    fi

    # Find all JSON files, sort by name (which includes timestamp)
    for file in "$directory"/*.json; do
        if [[ ! -f "$file" ]]; then
            continue
        fi

        # Extract timestamp from filename (YYYY-MM-DD_HH-MM-SS format)
        local filename
        local timestamp
        local score
        local error
        local plot_timestamp

        filename=$(basename "$file")
        timestamp=$(echo "$filename" | grep -oP '^\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}' || echo "unknown")

        # Get score and error for this benchmark
        score=$(jmh_get_score "$file" "$benchmark" 2>/dev/null || echo "")
        error=$(jmh_get_score_error "$file" "$benchmark" 2>/dev/null || echo "0")

        if [[ -n "$score" && "$score" != "null" ]]; then
            # Convert timestamp to plottable format (YYYY-MM-DD HH:MM:SS)
            plot_timestamp=$(echo "$timestamp" | sed 's/_/ /' | sed 's/-/:/3' | sed 's/-/:/4')
            echo "$plot_timestamp $score $error"
        fi
    done
}

# Validate that a file is valid JMH JSON
# Usage: jmh_validate <file>
# Returns: 0 if valid, 1 if invalid
jmh_validate() {
    local file="$1"

    if [[ ! -f "$file" ]]; then
        echo "Error: File not found: $file" >&2
        return 1
    fi

    # Check if it's valid JSON
    if ! jq empty "$file" > /dev/null 2>&1; then
        echo "Error: Invalid JSON in $file" >&2
        return 1
    fi

    # Check if it has JMH structure (either wrapped or raw)
    if jq -e '.jmh_results[0].benchmark' "$file" > /dev/null 2>&1; then
        return 0
    elif jq -e '.[0].benchmark' "$file" > /dev/null 2>&1; then
        return 0
    else
        echo "Error: File does not contain valid JMH benchmark results" >&2
        return 1
    fi
}
