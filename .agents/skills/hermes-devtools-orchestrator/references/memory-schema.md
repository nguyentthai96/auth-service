# Memory Schema Reference

> Data structures for storing execution history, lessons, and improvement patterns.

## Table of Contents

- [Execution Record Schema](#execution-record-schema)
- [Lesson Schema](#lesson-schema)
- [Config Snapshot Schema](#config-snapshot-schema)
- [Query Patterns](#query-patterns)
- [Retention Policies](#retention-policies)

---

## Execution Record Schema

Stores the result of each pipeline execution for trend analysis.

### Key Format

```
hermes-exec-{project}-{YYYYMMDD}-{HHmmss}
```

### Schema

```yaml
# Type: execution_record
# Tags: [hermes, pipeline, <project>, <rating>]

execution_record:
  # Identity
  id: "hermes-exec-taskqueue-20260813-100000"
  project: "taskqueue"
  workspace: "/path/to/taskqueue"
  timestamp: "2026-08-13T10:00:00+07:00"

  # Build info
  build:
    binary_version: "hermes-pipeline"
    binary_size_mb: 18.5
    source_commit: "abc1234"
    platform: "darwin-arm64"

  # Configuration used
  config:
    model: "gemini-2.5-pro"
    sdk_mode: "google"
    features: ["circuit-breaker", "cqrs"]
    workflows: ["wf_pre_openspec", "wf_openspec", "wf_openspec_apply"]
    max_retries: 3
    context_max_tokens: 8000

  # Results
  results:
    overall_score: 85
    rating: "good"          # excellent|good|fair|poor
    features_completed: 2
    features_total: 2
    workflows_completed: 6
    workflows_total: 6
    total_duration_seconds: 1200
    avg_duration_per_feature_seconds: 600
    error_count: 0
    retry_count: 1
    dimension_scores:
      completeness: 100
      output_quality: 80
      error_rate: 95
      efficiency: 70
      consistency: 90

  # Per-feature breakdown
  feature_details:
    circuit-breaker:
      status: "completed"
      duration_seconds: 550
      score: 88
      workflows:
        wf_pre_openspec: { status: "completed", retries: 0 }
        wf_openspec: { status: "completed", retries: 1 }
        wf_openspec_apply: { status: "completed", retries: 0 }
    cqrs:
      status: "completed"
      duration_seconds: 650
      score: 82
      workflows:
        wf_pre_openspec: { status: "completed", retries: 0 }
        wf_openspec: { status: "completed", retries: 0 }
        wf_openspec_apply: { status: "completed", retries: 0 }
```

---

## Lesson Schema

Stores actionable insights extracted from execution analysis.

### Key Format

```
hermes-lesson-{category}-{YYYYMMDD}-{short-id}
```

### Schema

```yaml
# Type: lesson
# Tags: [hermes, lesson, <category>, <project>]

lesson:
  id: "hermes-lesson-config-20260813-001"
  category: "config"         # config|workflow|error|pattern|performance
  project: "taskqueue"       # or "global" for cross-project lessons
  timestamp: "2026-08-13T10:30:00+07:00"

  # The lesson content
  title: "Increase context_max_tokens for complex features"
  description: |
    Features with >5 dependencies consistently failed with default
    context_max_tokens=8000. Increasing to 12000 resolved timeouts.
  confidence: "high"         # high|medium|low
  impact: "performance"      # performance|quality|reliability|cost

  # Evidence
  source_execution: "hermes-exec-taskqueue-20260813-100000"
  evidence:
    - "Feature circuit-breaker: 2 retries on wf_openspec with 8000 tokens"
    - "Same feature: 0 retries with 12000 tokens"

  # Recommended action
  action: "Set context_max_tokens >= 12000 for projects with complex features"
  applicable_to: ["taskqueue", "microservices"]  # or ["*"] for all

  # Lifecycle
  verified: true
  times_applied: 3
  success_rate: 1.0          # 1.0 = worked every time
  expires_at: null            # null = never expires
```

### Lesson Categories

| Category | Description | Example |
|----------|-------------|---------|
| `config` | Configuration optimization | Token limits, retry counts |
| `workflow` | Workflow selection/ordering | Skip brainstorm for simple features |
| `error` | Error pattern and fix | Retry on rate limit errors |
| `pattern` | Successful code/design pattern | Feature grouping strategies |
| `performance` | Speed/cost optimization | Model selection per task type |

---

## Config Snapshot Schema

Stores optimal configs per project type for quick reuse.

### Key Format

```
hermes-config-{project-type}-optimal
```

### Schema

```yaml
# Type: config_snapshot
# Tags: [hermes, config, <project-type>]

config_snapshot:
  id: "hermes-config-spring-boot-optimal"
  project_type: "spring-boot"
  created_from: "hermes-exec-taskqueue-20260813-100000"
  last_updated: "2026-08-13T10:30:00+07:00"

  # The optimal config values
  config:
    model: "gemini-2.5-pro"
    sdk_mode: "google"
    settings:
      max_retries: 3
      context_max_tokens: 12000
      memory_max_items: 20
    workflows: ["wf_pre_openspec", "wf_openspec", "wf_openspec_apply"]

  # Performance with this config
  metrics:
    avg_score: 87
    runs_count: 5
    best_score: 95
    worst_score: 78
```

---

## Query Patterns

### Common Queries

```python
# 1. Get latest execution for a project
memory_search({
    query: "hermes pipeline taskqueue",
    type: "execution_record"
})

# 2. Find all lessons for a specific category
memory_search({
    query: "hermes lesson config",
    type: "lesson",
    tags: ["config"]
})

# 3. Get optimal config for a project type
memory_read({
    key: "hermes-config-spring-boot-optimal"
})

# 4. Find all high-confidence lessons
memory_search({
    query: "hermes lesson high confidence",
    type: "lesson"
})

# 5. Get execution trend for a project
memory_search({
    query: "hermes exec taskqueue",
    type: "execution_record"
})
# Then sort by timestamp and extract scores for trend analysis

# 6. Find proven patterns across all projects
memory_search({
    query: "hermes successful pattern global",
    type: "lesson",
    tags: ["pattern"]
})
```

### Writing Records

```python
# After execution completes
memory_write({
    key: "hermes-exec-taskqueue-20260813-100000",
    type: "execution_record",
    content: yaml.dump(execution_record),
    tags: ["hermes", "pipeline", "taskqueue", "good"]
})

# After extracting a lesson
memory_write({
    key: "hermes-lesson-config-20260813-001",
    type: "lesson",
    content: yaml.dump(lesson),
    tags: ["hermes", "lesson", "config", "taskqueue"]
})

# After identifying optimal config
memory_write({
    key: "hermes-config-spring-boot-optimal",
    type: "config_snapshot",
    content: yaml.dump(config_snapshot),
    tags: ["hermes", "config", "spring-boot"]
})
```

---

## Retention Policies

### Execution Records

| Retention Rule | Policy |
|----------------|--------|
| Keep latest N per project | 20 records |
| Keep all with score < 50 | For debugging |
| Archive after 90 days | Move to cold storage |
| Delete after 180 days | Unless flagged |

### Lessons

| Retention Rule | Policy |
|----------------|--------|
| Keep all high-confidence | Indefinitely |
| Review medium after 60 days | Confirm or delete |
| Auto-delete low after 30 days | Unless verified |
| Merge duplicates | Weekly cleanup |

### Config Snapshots

| Retention Rule | Policy |
|----------------|--------|
| Keep latest per project type | 1 active snapshot |
| Archive previous versions | 5 versions max |
| Update after 5+ successful runs | Auto-update metrics |

### Pruning Process

```python
def prune_memory(memory, max_records_per_project=20):
    """Prune old execution records beyond retention limit."""
    # 1. Get all execution records
    records = memory_search({
        query: "hermes exec",
        type: "execution_record"
    })

    # 2. Group by project
    by_project = group_by(records, "project")

    # 3. For each project, keep only latest N
    for project, project_records in by_project.items():
        sorted_records = sorted(
            project_records,
            key=lambda r: r["timestamp"],
            reverse=True
        )
        for old_record in sorted_records[max_records_per_project:]:
            memory_delete({ key: old_record["key"] })
```
