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

# Statistical Calculations and Metadata Collection Library
# Functions for collecting system/git metadata and performing calculations

set -euo pipefail
export LC_NUMERIC=C

# Collect git metadata
# Usage: collect_git_metadata
# Returns: JSON object with git information
collect_git_metadata() {
    local git_commit
    local git_branch
    local git_dirty=false

    git_commit=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
    git_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")

    # Check if there are uncommitted changes
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        git_dirty=true
    fi

    # Output as JSON
    jq -n \
        --arg commit "$git_commit" \
        --arg branch "$git_branch" \
        --argjson dirty "$git_dirty" \
        '{
            git_commit: $commit,
            git_branch: $branch,
            git_dirty: $dirty
        }'
}

# Collect system information
# Usage: collect_system_info
# Returns: JSON object with system information
collect_system_info() {
    local hostname
    local os
    local os_version
    local arch
    local java_version
    local cpu_model="unknown"

    hostname=$(hostname 2>/dev/null || echo "unknown")
    os=$(uname -s 2>/dev/null || echo "unknown")
    os_version=$(uname -r 2>/dev/null || echo "unknown")
    arch=$(uname -m 2>/dev/null || echo "unknown")
    java_version=$(java -version 2>&1 | head -n 1 | awk -F'"' '{print $2}' | awk -F'.' '{print $1}' || echo "unknown")

    # Try to get CPU model
    if [[ -f /proc/cpuinfo ]]; then
        cpu_model=$(grep -m 1 "model name" /proc/cpuinfo | cut -d: -f2 | xargs || echo "unknown")
    fi

    # Output as JSON
    jq -n \
        --arg hostname "$hostname" \
        --arg os "$os $os_version" \
        --arg arch "$arch" \
        --arg cpu "$cpu_model" \
        --arg java "$java_version" \
        '{
            hostname: $hostname,
            os: $os,
            arch: $arch,
            cpu_model: $cpu,
            java_version: $java
        }'
}

# Calculate percentage change between two values
# Usage: calc_pct_change <baseline> <current>
# Returns: Percentage change (positive = increase, negative = decrease)
calc_pct_change() {
    local baseline="$1"
    local current="$2"

    if [[ "$baseline" == "0" || "$baseline" == "0.0" ]]; then
        echo "N/A"
        return 1
    fi

    # Use awk for floating point calculation
    awk -v baseline="$baseline" -v current="$current" \
        'BEGIN { printf "%.2f", ((current - baseline) / baseline) * 100 }'
}

# Format score for display
# Usage: format_score <value> <error> <unit>
# Returns: Formatted string "value ± error unit"
format_score() {
    local value="$1"
    local error="${2:-0}"
    local unit="${3:-}"
    local formatted_value
    local formatted_error

    # Format value and error to 2 decimal places
    formatted_value=$(printf "%.2f" "$value")
    formatted_error=$(printf "%.2f" "$error")

    if [[ "$error" == "0" || "$error" == "0.0" || "$error" == "0.00" ]]; then
        echo "$formatted_value $unit"
    else
        echo "$formatted_value ± $formatted_error $unit"
    fi
}

# Check if percentage change is statistically significant
# Usage: is_significant <pct_change> <error_margin> <baseline>
# Returns: 0 if significant, 1 if not
is_significant() {
    local pct_change="$1"
    local error_margin="$2"
    local baseline="$3"
    local error_pct
    local abs_pct_change

    # Calculate error as percentage of baseline
    error_pct=$(awk -v error="$error_margin" -v baseline="$baseline" \
        'BEGIN { if (baseline != 0) printf "%.2f", (error / baseline) * 100; else print "0" }')

    # Change is significant if absolute percentage change exceeds error percentage
    abs_pct_change=$(awk -v pct="$pct_change" \
        'BEGIN { if (pct < 0) print -pct; else print pct }')

    # Compare: is abs(pct_change) > error_pct?
    awk -v change="$abs_pct_change" -v error="$error_pct" \
        'BEGIN { exit (change > error) ? 0 : 1 }'
}

# Determine regression status based on thresholds
# Usage: get_status <pct_change> <warning_threshold> <critical_threshold> <is_significant>
# Returns: OK, IMPROVEMENT, REGRESSION, or CRITICAL_REGRESSION
get_status() {
    local pct_change="$1"
    local warning_threshold="$2"
    local critical_threshold="$3"
    local is_significant="$4"

    if [[ "$is_significant" == "false" ]]; then
        echo "OK"
        return 0
    fi

    # Use awk for floating point comparisons
    if awk -v pct="$pct_change" -v crit="$critical_threshold" \
        'BEGIN { exit (pct > crit) ? 0 : 1 }'; then
        echo "CRITICAL_REGRESSION"
    elif awk -v pct="$pct_change" -v warn="$warning_threshold" \
        'BEGIN { exit (pct > warn) ? 0 : 1 }'; then
        echo "REGRESSION"
    elif awk -v pct="$pct_change" -v warn="$warning_threshold" \
        'BEGIN { exit (pct < -warn) ? 0 : 1 }'; then
        echo "IMPROVEMENT"
    else
        echo "OK"
    fi
}

# Format percentage change for display
# Usage: format_pct_change <pct_change>
# Returns: Formatted string with +/- and color indicators
format_pct_change() {
    local pct_change="$1"
    local abs_pct

    # Check if positive or negative
    if awk -v pct="$pct_change" 'BEGIN { exit (pct > 0) ? 0 : 1 }'; then
        printf "+%.2f%% SLOWER" "$pct_change"
    elif awk -v pct="$pct_change" 'BEGIN { exit (pct < 0) ? 0 : 1 }'; then
        # Make it positive for display
        abs_pct=$(awk -v pct="$pct_change" 'BEGIN { print -pct }')
        printf -- "-%.2f%% FASTER" "$abs_pct"
    else
        echo "0.00% (no change)"
    fi
}

# Get timestamp in ISO 8601 format
# Usage: get_timestamp
# Returns: Current timestamp in ISO 8601 format
get_timestamp() {
    date -u +"%Y-%m-%dT%H:%M:%SZ"
}

# Get timestamp for filename
# Usage: get_filename_timestamp
# Returns: Current timestamp in filename format (YYYY-MM-DD_HH-MM-SS)
get_filename_timestamp() {
    date +"%Y-%m-%d_%H-%M-%S"
}

# Get project version from Maven POM
# Usage: get_project_version
# Returns: Project version string
get_project_version() {
    if [[ -f pom.xml ]]; then
        # Extract version from POM using grep and sed
        grep -m 1 "<version>" pom.xml | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | xargs || echo "unknown"
    else
        echo "unknown"
    fi
}

# Warn if git repository has uncommitted changes
# Usage: warn_if_dirty
# Returns: Prints warning to stderr if dirty
warn_if_dirty() {
    if ! git diff-index --quiet HEAD -- 2>/dev/null; then
        echo "Warning: Git repository has uncommitted changes" >&2
        echo "         Benchmark results may not be reproducible" >&2
    fi
}

# Validate numeric value
# Usage: is_numeric <value>
# Returns: 0 if numeric, 1 if not
is_numeric() {
    local value="$1"
    # Check if value matches float pattern
    [[ "$value" =~ ^-?[0-9]+\.?[0-9]*$ ]]
}
