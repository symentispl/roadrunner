# Roadrunner Benchmark Tracking System

A comprehensive framework for tracking JMH benchmark results over time, detecting performance regressions, and visualizing performance trends.

## Quick Start

```bash
# Run benchmarks and collect results with metadata
task benchmark:run

# Set baseline for future comparisons
task benchmark:baseline:update

# Run benchmarks and check for regressions
task benchmark:check

# Generate trend charts
task benchmark:chart

# Full report (run + regression check + charts)
task benchmark:report
```

## Table of Contents

- [Overview](#overview)
- [Directory Structure](#directory-structure)
- [Installation](#installation)
- [Usage](#usage)
  - [Running Benchmarks](#running-benchmarks)
  - [Baseline Management](#baseline-management)
  - [Regression Detection](#regression-detection)
  - [Chart Generation](#chart-generation)
- [File Format](#file-format)
- [Configuration](#configuration)
- [Interpreting Results](#interpreting-results)
- [Troubleshooting](#troubleshooting)

## Overview

This system provides:

- **Automated result collection**: Run JMH benchmarks and automatically save results with rich metadata (git commit, system info, timestamp)
- **Historical tracking**: Store all benchmark results as JSON files in git for long-term trend analysis
- **Regression detection**: Compare results against a baseline with configurable thresholds and statistical significance testing
- **Trend visualization**: Generate charts showing performance over time using gnuplot
- **Easy integration**: Simple Task commands for all operations

## Directory Structure

```
benchmarks/
├── results/                         # Benchmark results (git-tracked)
│   ├── 2025-12-12_16-30-45_6a4f233.json
│   ├── 2025-12-12_17-15-22_6a4f233.json
│   └── ...
├── baseline/                        # Reference baseline (git-tracked)
│   ├── baseline.json                # Current baseline for comparison
│   └── baseline.previous.json      # Previous baseline (backup)
├── charts/                          # Generated charts (git-ignored)
│   ├── RoadrunnerBenchmarks_executeRoadrunnerVmProtocol_trend.png
│   └── ...
├── scripts/                         # Automation scripts
│   ├── collect-benchmark.sh         # Run JMH + collect metadata
│   ├── detect-regressions.sh        # Compare & detect regressions
│   ├── generate-charts.sh           # Generate gnuplot charts
│   ├── update-baseline.sh           # Update baseline reference
│   └── lib/
│       ├── jmh-parser.sh            # JMH JSON parsing utilities
│       └── stats.sh                 # Statistical calculations
└── README.md                        # This file
```

## Installation

### Prerequisites

- **Java 25**: Already installed in this project
- **Maven/mvnd**: Already installed
- **jq**: JSON processor for bash scripts
  ```bash
  sudo apt install jq  # Ubuntu/Debian
  brew install jq      # macOS
  ```
- **gnuplot**: Chart generation
  ```bash
  sudo apt install gnuplot  # Ubuntu/Debian
  brew install gnuplot      # macOS
  ```

### Verification

```bash
# Check prerequisites
jq --version
gnuplot --version

# Verify Task commands
task --list | grep benchmark
```

## Usage

### Running Benchmarks

#### Quick Run (No Metadata)

For quick testing without metadata collection:

```bash
task benchmarks
```

This runs JMH directly without saving results.

#### Full Run with Metadata

For production benchmark runs with full metadata:

```bash
task benchmark:run
```

This will:
1. Build the project (`mvnd package`)
2. Run JMH benchmarks
3. Collect metadata (git commit, system info, timestamp)
4. Save results to `benchmarks/results/` with filename `{timestamp}_{commit}.json`

**Custom JMH Arguments:**

```bash
# Run specific benchmark
./benchmarks/scripts/collect-benchmark.sh --jmh-args ".*executeRoadrunner.*"

# Custom iterations
./benchmarks/scripts/collect-benchmark.sh --jmh-args "-i 5 -wi 2"
```

### Baseline Management

A **baseline** is a reference benchmark result used for regression detection. You should update the baseline when:
- Starting fresh tracking
- After verifying a performance improvement
- When establishing a new stable reference point

#### Set Baseline from Latest Result

```bash
task benchmark:baseline:update
```

#### Set Baseline from Specific Result

```bash
task benchmark:baseline:set benchmarks/results/2025-12-12_16-30-45_6a4f233.json
```

#### View Current Baseline

```bash
cat benchmarks/baseline/baseline.json | jq '.metadata'
```

### Regression Detection

Compare benchmark results to detect performance regressions.

#### Compare Latest Run Against Baseline

```bash
task benchmark:check
```

This runs benchmarks and automatically compares against the baseline.

#### Compare Latest Result (Without Running)

```bash
./benchmarks/scripts/detect-regressions.sh --latest
```

#### Compare Two Specific Results

```bash
task benchmark:compare "results/2025-12-10_*.json results/2025-12-12_*.json"
```

Or directly:

```bash
./benchmarks/scripts/detect-regressions.sh --compare \
    benchmarks/results/2025-12-10_10-30-00_abc123.json \
    benchmarks/results/2025-12-12_16-30-45_6a4f233.json
```

#### Custom Thresholds

```bash
./benchmarks/scripts/detect-regressions.sh --latest \
    --warning 3 \
    --critical 8
```

- `--warning`: Percentage threshold for warning (default: 5%)
- `--critical`: Percentage threshold for critical regression (default: 10%)

#### JSON Output (for Automation)

```bash
./benchmarks/scripts/detect-regressions.sh --latest --json | jq
```

### Chart Generation

Generate visual trend charts from historical results.

#### Generate All Charts

```bash
task benchmark:chart
```

#### Generate Chart for Specific Benchmark

```bash
task benchmark:chart:single "executeRoadrunner.*"
```

Or directly:

```bash
./benchmarks/scripts/generate-charts.sh --benchmark "executeRoadrunner.*"
```

#### Filter by Date

```bash
./benchmarks/scripts/generate-charts.sh --since 2025-12-01
```

#### Different Output Formats

```bash
./benchmarks/scripts/generate-charts.sh --format svg   # SVG (vector)
./benchmarks/scripts/generate-charts.sh --format pdf   # PDF
./benchmarks/scripts/generate-charts.sh --format png   # PNG (default)
```

## File Format

### Result File Naming

```
{YYYY-MM-DD}_{HH-MM-SS}_{git-commit-hash}.json
```

Example: `2025-12-12_16-30-45_6a4f233.json`

This provides:
- Chronological sorting by default
- Quick git traceability
- Human-readable timestamps

### JSON Structure

```json
{
  "metadata": {
    "timestamp": "2025-12-12T16:30:45Z",
    "git_commit": "6a4f233",
    "git_branch": "add-support-for-custom-metrics",
    "git_dirty": false,
    "hostname": "developer-machine",
    "os": "Linux 6.14.0-37-generic",
    "arch": "x86_64",
    "cpu_model": "AMD Ryzen 7 4700U",
    "java_version": "25",
    "jmh_version": "1.37",
    "project_version": "0.0.2-SNAPSHOT"
  },
  "jmh_results": [
    {
      "benchmark": "io.roadrunner.RoadrunnerBenchmarks.executeRoadrunnerVmProtocol",
      "mode": "ss",
      "threads": 1,
      "forks": 10,
      "primaryMetric": {
        "score": 1250.5,
        "scoreError": 15.2,
        "scoreUnit": "ms/op"
      }
    }
  ]
}
```

## Configuration

### Regression Detection Thresholds

Default thresholds in `detect-regressions.sh`:

- **Warning**: 5% slower
- **Critical**: 10% slower
- **Improvement**: 5% faster

Override via CLI:

```bash
./benchmarks/scripts/detect-regressions.sh --latest \
    --warning 3 \
    --critical 8 \
    --improvement 3
```

### Chart Settings

Default chart settings in `generate-charts.sh`:

- **Format**: PNG
- **Size**: 1400x800 pixels
- **Features**: Error bars, baseline reference line, time series

Customize via CLI options (see [Chart Generation](#chart-generation)).

## Interpreting Results

### Regression Report

```
executeRoadrunnerVmProtocol:
  Baseline: 1250.5 ± 15.2 ms/op
  Current:  1320.8 ± 18.4 ms/op
  Change:   +5.6% SLOWER
  Status:   ⚠️  REGRESSION (exceeds 5% threshold)
```

- **Baseline**: Reference performance (mean ± error margin)
- **Current**: Latest benchmark performance
- **Change**: Percentage difference (positive = slower, negative = faster)
- **Status**: Regression classification

### Status Indicators

- **✓ OK**: No significant change or within threshold
- **✅ IMPROVEMENT**: Significant performance improvement
- **⚠️ REGRESSION**: Performance degradation exceeds warning threshold
- **🔴 CRITICAL REGRESSION**: Performance degradation exceeds critical threshold

### Statistical Significance

Changes are only flagged if they:
1. Exceed the percentage threshold
2. Are statistically significant (exceed error margin)

This reduces false positives from normal JMH variance.

### Exit Codes

Regression detection script exits with:

- `0`: No regressions detected
- `1`: Warning-level regression detected
- `2`: Critical regression detected

Use in CI/CD:

```bash
if ! ./benchmarks/scripts/detect-regressions.sh --latest; then
    echo "Performance regression detected!"
    exit 1
fi
```

## Troubleshooting

### Problem: "Benchmarks JAR not found"

**Solution**: Build the project first:

```bash
mvnd package
# or
task benchmark:run  # This includes the build step
```

### Problem: "No result files found"

**Solution**: Run benchmarks first:

```bash
task benchmark:run
```

### Problem: "Baseline file not found"

**Solution**: Set a baseline:

```bash
task benchmark:baseline:update
```

### Problem: "gnuplot is not installed"

**Solution**: Install gnuplot:

```bash
sudo apt install gnuplot  # Ubuntu/Debian
brew install gnuplot      # macOS
```

### Problem: Charts show only one data point

**Solution**: Run benchmarks multiple times to build history:

```bash
task benchmark:run
# Wait or make changes
task benchmark:run
# Repeat...
```

At least 2 data points are needed for trend visualization.

### Problem: Git repository has uncommitted changes

**Behavior**: Warning is displayed but execution continues

**Solution**: Commit changes before running benchmarks for reproducibility:

```bash
git add .
git commit -m "Your changes"
task benchmark:run
```

### Problem: Want to archive old results

**Solution**: Move old results to archive directory:

```bash
mkdir -p benchmarks/archive
mv benchmarks/results/2024-* benchmarks/archive/
```

Charts and regression detection only use files in `benchmarks/results/`.

## Advanced Usage

### Batch Benchmark Runs

Run multiple benchmarks and generate report:

```bash
for i in {1..5}; do
    echo "Run $i of 5"
    task benchmark:run
    sleep 60  # Wait between runs
done
task benchmark:chart
```

### Integration with CI/CD

Example GitHub Actions workflow snippet:

```yaml
- name: Run Benchmarks
  run: task benchmark:run

- name: Check for Regressions
  run: ./benchmarks/scripts/detect-regressions.sh --latest

- name: Upload Results
  uses: actions/upload-artifact@v3
  with:
    name: benchmark-results
    path: benchmarks/results/
```

### Comparing Branches

```bash
# On main branch
git checkout main
task benchmark:run
task benchmark:baseline:update

# On feature branch
git checkout feature-branch
task benchmark:check  # Compare against main baseline
```

## Tips

- **Run benchmarks on consistent hardware** for reliable comparisons
- **Avoid running benchmarks while system is under load** (close other applications)
- **Commit benchmark results regularly** to build historical data
- **Update baseline after verifying improvements** to track against new standard
- **Use the full `benchmark:report` task** for comprehensive analysis
- **Check error margins** - large error margins indicate unstable benchmarks

## Support

For issues or questions:
- Check the [main project README](../README.md)
- Review script help: `./benchmarks/scripts/<script-name>.sh --help`
- Check JMH documentation: https://github.com/openjdk/jmh
