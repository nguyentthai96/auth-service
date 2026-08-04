---
name: agent-governor
description: "Central autonomous governor — replaces human-in-the-loop with intelligent self-governance. Manages pipeline mode selection, auto-phase-progression, self-validation, and minimal-intervention protocols. The 'brain' that makes the orchestration truly autonomous."
---

# Agent Governor (Autonomous Brain)

## Purpose

**Bộ não trung tâm** điều phối toàn bộ pipeline **tự động, ít cần sự can thiệp của con người**. Quản lý:
- Pipeline mode selection (greenfield vs feature-add)
- Phase progression (auto-gate)
- Self-validation loops
- Escalation rules (khi nào PHẢI hỏi người)

**Nguyên tắc**: Chạy tự động tối đa → chỉ dừng khi THỰC SỰ cần human.

---

## When to Use

- At the START of any development request
- As the top-level controller for `/wf_e2e` and `/wf_feature_add`
- When deciding pipeline routing

---

## Operating Modes

### Mode 1: 🆕 Greenfield (New Project)

**Trigger**: User mô tả ý tưởng mới, không có source code hiện tại.

```
/wf_e2e "{idea}" --project {name}
```

**Validation**: No existing `project_docs/{name}/` directory.

### Mode 2: ➕ Feature Add (Existing Project)

**Trigger**: User yêu cầu thêm tính năng cho sản phẩm đã có.

```
/wf_feature_add "{feature}" --project {name}
```

**Validation**: `project_docs/{name}/` exists with prior docs.

### Mode 3: 🐛 Patch/Fix (Quick Fix)

**Trigger**: Bug fix, config change, minor adjustment.

**Validation**: Scope < 3 files, no architecture change.

**Action**: Direct fix → test → commit. No pipeline needed.

### Auto-Detection Logic

```
User request arrives
       ↓
┌──────────────────────────────┐
│ 1. Check project_docs/{name} │
│    └─ Not exists → Mode 1    │
│    └─ Exists → Check scope   │
│         └─ New feature → 2   │
│         └─ Bug fix → 3       │
│         └─ Unclear → ASK     │
└──────────────────────────────┘
```

---

## Auto-Phase-Progression Protocol

### The Core Loop

```
WHILE phases remain:
    1. Execute current phase workflow
    2. Run phase-gate-controller → get score
    3. IF score ≥ 7.0:
         → Log [PASS], commit, proceed to next phase
    4. ELIF score ≥ 5.0:
         → Log [REVISE], auto-fix, re-score (max 2x)
         → IF improved → proceed
         → IF not improved → FAIL protocol
    5. ELIF score < 5.0:
         → FAIL protocol
    6. Track cost, update checkpoint
    7. NEXT phase

FAIL protocol:
    → Save checkpoint
    → Generate status report with:
        - What was accomplished
        - What failed and why
        - Suggested fixes
        - Cost so far
    → STOP and wait for human
```

### Phase Boundaries (E2E)

```
Phase 0 (Research)      🚪 Gate 0 → Phase 1 (BRD)
Phase 1 (BRD)           🚪 Gate 1 → Phase 2a (Tech)
Phase 2a (Tech)         🚪 Gate 2a → Phase 2b (UI)
Phase 2b (UI)           🚪 Gate 2b → Phase 3 (OpenSpec)
Phase 3 (OpenSpec)      🚪 Gate 3 → Phase 4 (Code)
Phase 4 (Code)          🚪 Gate 4 → Phase 5 (Deploy)
Phase 5 (Deploy)        🚪 Gate 5 → COMPLETE
```

### Phase Boundaries (Feature Add)

```
Phase 0 (Context)       🚪 Gate 0 → Phase 1 (Feature BRD)
Phase 1 (Feature BRD)   🚪 Gate 1 → Phase 2 (Design Delta)
Phase 2 (Design Delta)  🚪 Gate 2 → Phase 3 (Implement)
Phase 3 (Implement)     🚪 Gate 3 → Phase 4 (Test & Deploy)
Phase 4 (Test & Deploy) 🚪 Gate 4 → COMPLETE
```

---

## Minimal Intervention Rules

### When to PROCEED AUTONOMOUSLY (No human needed)

| Situation | Action |
|:---|:---|
| Phase gate PASS (≥ 7.0) | Auto-proceed |
| Tier 1 decision (reversible) | Auto-decide + log |
| Tier 2 decision (debate needed) | Multi-agent debate → apply consensus |
| Build compilation error | Auto-fix (max 3 attempts) |
| Test failure (< 5 tests) | Auto-fix → re-run |
| Budget < 80% used | Continue normally |
| Standard library choice | Use Tier 2 debate |

### When to PAUSE AND NOTIFY (Soft stop)

| Situation | Action |
|:---|:---|
| Phase gate REVISE 2nd time | Notify user but continue with best effort |
| Budget 80-95% used | Warn and downgrade remaining tasks |
| Non-critical test failures (> 5) | Continue but flag in report |
| Open question accumulates > 5 | Batch questions for next human check |

### When to STOP AND ESCALATE (Hard stop)

| Situation | Action |
|:---|:---|
| Phase gate FAIL (< 5.0) | STOP immediately |
| Tier 3 decision (irreversible) | STOP and present options |
| Security vulnerability detected | STOP immediately |
| Budget exhausted (> 95%) | STOP |
| Build fails 3x after fix attempts | STOP |
| Regression: existing tests broken | STOP |

---

## Self-Validation Loops

### Consistency Check (after each phase)

The governor runs cross-phase consistency verification:

```markdown
## Cross-Phase Validation

### FR Traceability
- Every FR in BRD → has tech design section → has API endpoint → has test
- Missing: FR-003 has no controller test → flag for Phase 4

### Entity-DB Alignment
- Every entity in domain model → has DB table → has migration script
- Missing: UserPreference entity has no migration → flag for Phase 3

### API Contract Check
- Every API endpoint in tech design → has controller code → has contract test
- Missing: PATCH /rooms/{id} not implemented → flag for Phase 4

### State Machine Completeness
- Every state in state diagram → has enum value → has transition test
- Missing: SUSPENDED state has no transition from ACTIVE → flag for Phase 2a
```

### Quality Trend Tracking

Track gate scores across phases:
```markdown
| Phase | Completeness | Consistency | Quality | Actionability | Composite |
|:---:|:---:|:---:|:---:|:---:|:---:|
| 0 | 8.0 | 7.5 | 7.0 | 8.0 | 7.6 ✅ |
| 1 | 9.0 | 8.0 | 8.5 | 7.5 | 8.3 ✅ |
| 2a | 7.0 | 6.5 | 7.5 | 8.0 | 7.2 ✅ |
| 2b | 7.5 | 7.0 | 6.0 | 7.0 | 6.9 🔄 |
```

If quality trend is declining (3 consecutive drops) → warn and add extra validation.

---

## Checkpoint & Resume

### Checkpoint Format

At every gate boundary:
```yaml
# .governor/checkpoint.yaml
project: "{project-name}"
mode: "e2e" | "feature_add"
feature: "{feature-slug}" # only for feature_add
started_at: "2026-07-02T10:00:00Z"
current_phase: 3
completed_phases: [0, 1, 2]
gate_scores: {0: 7.6, 1: 8.3, 2: 7.2}
total_tokens: 630000
total_cost: "$15.20"
last_commit: "abc1234"
decisions_made: 12
decisions_auto: 8
decisions_debate: 3
decisions_human: 1
open_questions: ["OQ-001", "OQ-003"]
revision_count: {2: 1}  # phase 2 had 1 revision
```

### Resume

```
/wf_e2e --resume {project-name}
/wf_feature_add --resume {project-name}/{feature-slug}
```

Governor reads checkpoint → resumes from `current_phase`.

---

## Status Report (on STOP)

When pipeline stops (FAIL or budget):

```markdown
# Pipeline Status Report

## Project: {name}
## Mode: {e2e / feature_add}
## Status: ⏸️ PAUSED — Requires Human Input

### Progress
| Phase | Status | Score | Duration |
|:---|:---:|:---:|:---:|
| 0 Research | ✅ | 7.6 | 15min |
| 1 BRD | ✅ | 8.3 | 45min |
| 2 Design | ❌ | 4.8 | 30min |

### Why Stopped
- Phase 2 gate scored 4.8 (below 5.0 threshold)
- Issue: Domain model missing 3 entities from FR list
- Issue: No state machine for payment workflow

### To Resume
Fix the issues above, then run:
```
/wf_e2e --resume {project-name}
```

### Cost Summary
- Used: 630K tokens (~$15.20)
- Remaining budget: 770K tokens (~$18.80)

### Decisions Made
- Auto: 8, Debate: 3, Human: 1
- Open Questions: 2 (see open_questions.md)
```

---

## Integration Points

| Skill | Role in Governor |
|:---|:---|
| `phase-gate-controller` | Executes gate validation + scoring |
| `autonomous-deliberation` | Classifies decisions (Tier 1/2/3) |
| `cost-controller` | Tracks budget + model routing |
| `multi-agent-brainstorming` | Runs Tier 2 debates |
| `dispatching-parallel-agents` | Parallelizes independent tasks |

---

## Guardrails

- **DO** save checkpoint at EVERY phase boundary
- **DO** log ALL decisions (auto, debate, human)
- **DO** track cost continuously
- **DO** run cross-phase validation after each gate
- **DO NOT** skip gates — even if prior phase "looks good"
- **DO NOT** continue after FAIL without human resolution
- **DO NOT** exceed budget without explicit approval
- **DO NOT** auto-decide Tier 3 items — always escalate
- **DO NOT** ignore declining quality trend

## Limitations
- Governor is only as good as its gate checklists — gaps in checklists mean gaps in validation.
- Complex domain logic may require human expertise that no amount of automated debate can replace.
- Stop and ask for clarification if the user's request doesn't clearly map to any operating mode.
