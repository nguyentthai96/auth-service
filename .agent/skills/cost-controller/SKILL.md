---
name: cost-controller
description: "Control and optimize AI token costs across pipeline stages. Task-level budget tagging, model routing by complexity, token tracking per pipeline run, and cost reporting. Use when running multi-step pipelines to prevent runaway token costs."
---

# Cost Controller

## Purpose

Prevent runaway AI costs by enforcing **task-level token budgets**, routing tasks to **cost-appropriate models**, and tracking **actual vs budgeted** token usage across pipeline runs.

---

## When to Use

- At the START of any pipeline (`/wf_ideate`, `/wf_brd`, etc.)
- When budget awareness is critical (large projects, multiple pipelines)
- When deciding which model to use for a task
- For post-pipeline cost reporting

## When NOT to Use

- Single-shot questions (overhead > benefit)
- When user explicitly says "don't worry about cost"

---

## Core Concepts

### 1. Task Budget Tags

Embed cost metadata in workflow files using HTML comments:

```markdown
<!-- task_budget: {
  "task": "generate-brd-docs",
  "max_tokens": 50000,
  "model_tier": "mid",
  "priority": "quality"
} -->
```

**Fields**:
| Field | Type | Values | Description |
|:---|:---|:---|:---|
| `task` | string | kebab-case | Task identifier |
| `max_tokens` | number | 1000-200000 | Token ceiling for this task |
| `model_tier` | string | `high` / `mid` / `low` | Model quality tier |
| `priority` | string | `quality` / `balanced` / `speed` | Optimization target |

### 2. Model Tiers

| Tier | Models | Use For | Token Cost |
|:---|:---|:---|:---|
| 🔴 `high` | Opus, GPT-4o | Architecture debate, critical decisions, complex analysis | $$$ |
| 🟡 `mid` | Sonnet, GPT-4o-mini | Code generation, BRD writing, tech design | $$ |
| 🟢 `low` | Local (Qwen/DeepSeek/Llama) | Formatting, documentation, templates, simple edits | Free |

### 3. Pipeline Budget

Each pipeline run has a total budget:

```markdown
<!-- pipeline_budget: {
  "pipeline": "wf_brd",
  "project": "rentivo",
  "total_budget_tokens": 200000,
  "started_at": "2026-07-02T10:00:00Z"
} -->
```

---

## Model Routing Rules

### Decision Matrix

| Task Type | Model Tier | Rationale |
|:---|:---|:---|
| Architecture debate (5 agents) | 🔴 high | Complex reasoning, critical decisions |
| Technology analysis | 🔴 high | Multi-factor comparison, trade-offs |
| Flow logic validation | 🟡 mid | Pattern matching, edge case detection |
| BRD document generation | 🟡 mid | Structured writing, template filling |
| Tech design docs | 🟡 mid | Technical writing, diagram generation |
| UI/UX docs | 🟡 mid | Design system, component catalog |
| Cross-reference validation | 🟢 low | Mechanical checking, regex-like |
| Template formatting | 🟢 low | Fill-in-the-blanks, no reasoning needed |
| Decision log entries | 🟢 low | Structured logging, no analysis |
| Mockup prompt generation | 🟡 mid | Creative description from wireframe |
| Research synthesis | 🔴 high | Complex analysis of multiple sources |
| Simple Q&A formatting | 🟢 low | Formatting, restructuring |

### Routing Algorithm

```
Input: task_type, priority

1. Lookup model_tier from Decision Matrix
2. If priority == "quality": upgrade tier (low→mid, mid→high)
3. If priority == "speed": downgrade tier (high→mid, mid→low)
4. If remaining_budget < 20%: downgrade tier (save tokens)
5. Select model from tier (see agent-config profiles)
6. Log: task, model, estimated_tokens
```

---

## Budget Tracking

### Per-Task Tracking

After each task completes, log actual usage:

```markdown
<!-- task_complete: {
  "task": "generate-brd-docs",
  "model": "sonnet",
  "input_tokens": 15000,
  "output_tokens": 8000,
  "total_tokens": 23000,
  "budget": 50000,
  "utilization": "46%"
} -->
```

### Pipeline Summary

At pipeline end, generate cost report:

```markdown
## Pipeline Cost Report: /wf_brd — rentivo

| Task | Model | Budget | Actual | Util. |
|:---|:---|:---:|:---:|:---:|
| research-ingest | low | 5,000 | 3,200 | 64% |
| generate-brd-00-08 | mid | 80,000 | 62,000 | 78% |
| architecture-debate | high | 40,000 | 35,000 | 88% |
| technology-debate | high | 40,000 | 32,000 | 80% |
| flow-logic-review | mid | 20,000 | 15,000 | 75% |
| cross-reference | low | 5,000 | 2,800 | 56% |
| quality-gate | mid | 10,000 | 8,000 | 80% |
| **TOTAL** | | **200,000** | **158,000** | **79%** |

### Cost Optimization Applied
- 3 tasks routed to local model (saved ~$X)
- 2 tasks used mid instead of high (saved ~$Y)
- Total estimated savings: ~$Z vs all-high
```

---

## Process

### At Pipeline Start

1. Read pipeline config from `agent-config/profiles/{pipeline}.yaml`
2. Set pipeline budget
3. Initialize token counter

### During Pipeline

1. Before each task → check remaining budget
2. Route to appropriate model tier
3. If budget < 20% remaining:
   - Downgrade remaining tasks to lower tier
   - Log warning: "Budget running low"
4. If budget exhausted:
   - PAUSE pipeline
   - Report: tasks completed vs remaining
   - Ask: continue with additional budget or stop?

### At Pipeline End

1. Generate cost report (format above)
2. Save to `project_docs/{project}/cost_report_{pipeline}_{date}.md`
3. Append summary to `decision_log.md`

---

## Integration with Workflows

Every workflow should include cost controller hooks:

```markdown
### Step 0: Initialize Cost Controller
<!-- pipeline_budget: {...} -->
Load model routing from `agent-config/profiles/{pipeline}.yaml`

### Step N: {Task}
<!-- task_budget: {...} -->
{task instructions}
<!-- task_complete: {...} -->

### Step FINAL: Cost Report
Generate and save pipeline cost report
```

---

## Guardrails

- **DO** set budget BEFORE starting any pipeline
- **DO** route formatting/template tasks to local/low-tier models
- **DO** generate cost report at pipeline end
- **DO NOT** use high-tier for mechanical tasks (waste)
- **DO NOT** ignore budget warnings — they prevent cost overruns
- **DO NOT** skip tracking — untracked tasks defeat the purpose
- **ALERT** when single task exceeds 50% of pipeline budget

## Limitations
- Use this skill only when the task clearly matches the scope described above.
- Do not treat the output as a substitute for environment-specific validation, testing, or expert review.
- Stop and ask for clarification if required inputs, permissions, safety boundaries, or success criteria are missing.
