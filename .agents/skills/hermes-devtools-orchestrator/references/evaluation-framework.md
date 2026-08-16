# Evaluation Framework Reference

> Detailed methodology for evaluating hermes-pipeline execution results.

## Table of Contents

- [Scoring Methodology](#scoring-methodology)
- [Dimension Details](#dimension-details)
- [Data Collection](#data-collection)
- [Trend Analysis](#trend-analysis)
- [Report Generation](#report-generation)

---

## Scoring Methodology

### Overall Score Formula

```
overall_score = Σ (dimension_score × dimension_weight)
```

Where each dimension score is normalized to 0-100.

### Dimension Weights

| Dimension | Weight | Key Metric |
|-----------|--------|------------|
| Completeness | 0.30 | features_completed / features_total |
| Output Quality | 0.25 | valid_outputs / total_outputs |
| Error Rate | 0.20 | 1 - (errors / total_steps) |
| Efficiency | 0.15 | baseline_time / actual_time |
| Consistency | 0.10 | std_dev of per-feature scores |

---

## Dimension Details

### 1. Completeness (30%)

Measures whether all requested work was completed.

```python
def score_completeness(progress_data: dict) -> float:
    """Score based on pipeline progress file."""
    features_total = len(progress_data.get("features", {}))
    if features_total == 0:
        return 0.0

    completed = sum(
        1 for f in progress_data["features"].values()
        if f.get("status") == "completed"
    )

    # Partial credit for in-progress features
    in_progress = sum(
        1 for f in progress_data["features"].values()
        if f.get("status") == "in_progress"
    )

    score = (completed + 0.5 * in_progress) / features_total * 100
    return min(score, 100.0)
```

**Scoring Guide:**
| Completion Rate | Score |
|----------------|-------|
| 100% complete | 100 |
| 80-99% | 80-99 |
| 60-79% | 60-79 |
| <60% | 0-59 |

### 2. Output Quality (25%)

Measures validity and structure of generated artifacts.

**Quality Checks:**

```python
def score_output_quality(output_dir: str) -> float:
    """Score based on output file validation."""
    checks = {
        "files_exist": check_required_files(output_dir),
        "yaml_valid": validate_yaml_files(output_dir),
        "md_not_empty": check_non_empty_markdown(output_dir),
        "no_placeholders": check_no_placeholder_text(output_dir),
        "proper_structure": check_file_structure(output_dir),
    }

    passed = sum(1 for v in checks.values() if v)
    return passed / len(checks) * 100
```

**Required Output Files per Feature:**
- `{feature}/analysis.md` — Research/analysis
- `{feature}/spec.md` — Design specification
- `{feature}/implementation_notes.md` — Implementation details (if openspec_apply ran)

### 3. Error Rate (20%)

Measures robustness — fewer errors = higher score.

```python
def score_error_rate(progress_data: dict) -> float:
    """Score based on errors and retries."""
    total_steps = 0
    error_count = 0
    retry_count = 0

    for feature in progress_data.get("features", {}).values():
        for wf in feature.get("workflows", {}).values():
            total_steps += 1
            if wf.get("status") == "error":
                error_count += 1
            retry_count += wf.get("retries", 0)

    if total_steps == 0:
        return 100.0

    # Errors are worse than retries
    penalty = (error_count * 2 + retry_count) / (total_steps * 2) * 100
    return max(0, 100 - penalty)
```

### 4. Efficiency (15%)

Measures resource usage relative to baseline.

**Metrics:**
- Execution time per feature (seconds)
- Total token consumption (if available from observability)
- Number of API calls per workflow

```python
def score_efficiency(actual_seconds: float, baseline_seconds: float) -> float:
    """Score efficiency relative to baseline."""
    if baseline_seconds <= 0:
        return 50.0  # No baseline — neutral score

    ratio = baseline_seconds / actual_seconds
    # ratio > 1 means faster than baseline (good)
    # ratio < 1 means slower (bad)
    score = min(ratio * 100, 100)
    return max(0, score)
```

**Baseline Reference (per feature, single workflow):**
| Workflow | Expected Duration |
|----------|------------------|
| wf_pre_openspec | 2-5 minutes |
| wf_brainstorm_openspec | 3-6 minutes |
| wf_openspec | 3-8 minutes |
| wf_openspec_apply | 5-15 minutes |

### 5. Consistency (10%)

Measures uniformity across features in the same run.

```python
def score_consistency(per_feature_scores: list[float]) -> float:
    """Score based on standard deviation of per-feature quality."""
    if len(per_feature_scores) < 2:
        return 100.0  # Single feature — perfect consistency

    import statistics
    std_dev = statistics.stdev(per_feature_scores)
    # Lower std_dev = more consistent = higher score
    # std_dev of 0 → 100, std_dev of 30+ → 0
    score = max(0, 100 - std_dev * 3.33)
    return score
```

---

## Data Collection

### From Pipeline Progress File

Location: `<target-project>/openspec/pipeline_progress.yml`

```yaml
# Expected structure
pipeline_progress:
  started_at: "2026-08-13T10:00:00Z"
  features:
    feature-name:
      status: "completed"  # or "in_progress", "error", "skipped"
      workflows:
        wf_pre_openspec:
          status: "completed"
          started_at: "..."
          completed_at: "..."
          retries: 0
        wf_openspec:
          status: "completed"
          started_at: "..."
          completed_at: "..."
          retries: 1
```

### From Output Artifacts

Location: `<target-project>/openspec/<feature-name>/`

Scan for:
- File count and sizes
- YAML/Markdown validity
- Presence of expected sections
- Absence of error markers

### From Logs

Location: `<target-project>/logs/` (if configured)

Parse for:
- Error messages
- Warning counts
- Retry events
- Timeout events

---

## Trend Analysis

### Cross-Run Comparison

Compare the current run's scores with historical data:

```yaml
trend_report:
  project: "my-project"
  runs:
    - date: "2026-08-01"
      overall_score: 72
      features_completed: 8/10
    - date: "2026-08-07"
      overall_score: 81
      features_completed: 9/10
    - date: "2026-08-13"
      overall_score: 88
      features_completed: 10/10
  trend: "improving"  # improving | stable | declining
  improvement_rate: "+8 points/week"
```

### Trend Classification

| Pattern | Classification | Action |
|---------|---------------|--------|
| 3+ consecutive increases | Improving | Continue current approach |
| Scores within ±5 | Stable | Consider new optimizations |
| 2+ consecutive decreases | Declining | Root cause analysis needed |
| High variance (>15 std_dev) | Unstable | Review config stability |

---

## Report Generation

### Report Template

See [examples/evaluation-report-sample.md](../examples/evaluation-report-sample.md) for full template.

### Report Sections

1. **Summary** — Overall score and one-line assessment
2. **Dimension Breakdown** — Score per dimension with details
3. **Feature Status** — Per-feature completion and quality
4. **Errors & Issues** — Specific problems encountered
5. **Trend** — Comparison with previous runs
6. **Recommendations** — Actionable improvement suggestions
7. **Config Snapshot** — What config was used for this run
