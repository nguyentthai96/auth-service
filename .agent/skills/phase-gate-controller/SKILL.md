---
name: phase-gate-controller
description: "Autonomous phase gate controller — validates phase outputs, scores quality, and auto-decides go/no-go without human intervention. Supports both greenfield and feature-add pipelines. Use at every phase boundary in /wf_e2e or /wf_feature_add."
---

# Phase Gate Controller

## Purpose

**Tự động validate output của mỗi phase** và quyết định go/no-go mà **không cần human approval**. Thay thế cơ chế "chờ user feedback" bằng automated quality scoring + autonomous-deliberation protocol.

**Nguyên tắc**: Agent tự kiểm tra → tự đánh giá → tự quyết định → tự chuyển phase.

---

## When to Use

- At EVERY phase boundary in `/wf_e2e` or `/wf_feature_add`
- After any workflow completes, before starting the next
- When quality gate disposition is needed

## When NOT to Use

- User explicitly requests manual review
- Tier 3 (human-required) decision arises

---

## Gate Protocol

### Flow

```
Phase N completes
       ↓
┌──────────────────────────────┐
│  PHASE GATE CONTROLLER       │
│                              │
│  1. Collect output artifacts │
│  2. Run validation checklist │
│  3. Score per dimension      │
│  4. Calculate composite      │
│  5. Auto-decide:             │
│     ≥ 7.0 → ✅ PASS → Next  │
│     5.0-6.9 → 🔄 REVISE     │
│     < 5.0 → ❌ FAIL → Stop  │
│                              │
│  6. Log to phase_gates.md    │
│  7. Commit gate report       │
└──────────────────────────────┘
       ↓
Phase N+1 starts (or revision)
```

### Scoring System

Each phase has dimensions scored 1-10:

| Dimension | Weight | What It Measures |
|:---|:---:|:---|
| **Completeness** | 30% | All required artifacts exist? |
| **Consistency** | 25% | Cross-references valid? No contradictions? |
| **Quality** | 25% | Depth of analysis, detail level, edge cases covered? |
| **Actionability** | 20% | Next phase can consume outputs without questions? |

**Composite Score** = weighted average of all dimensions.

### Thresholds

| Score | Disposition | Action |
|:---:|:---|:---|
| ≥ 7.0 | ✅ **PASS** | Auto-proceed to next phase |
| 5.0 - 6.9 | 🔄 **REVISE** | Auto-fix issues, re-score (max 2 cycles) |
| < 5.0 | ❌ **FAIL** | STOP pipeline, generate detailed report, escalate |

### Revision Protocol

When REVISE (5.0-6.9):
1. Identify dimensions scoring < 7.0
2. Generate fix instructions per dimension
3. Re-run the phase's quality-relevant steps ONLY (not entire phase)
4. Re-score
5. If improved ≥ 7.0 → PASS
6. If still REVISE after 2 cycles → treat as FAIL

---

## Per-Phase Validation Checklists

### Phase 0 Gate: Research & Ideation → BRD

**Required artifacts**:
- [ ] `research_notes.md` — not empty, has sections
- [ ] `competitor_analysis.md` — ≥ 3 competitors analyzed
- [ ] `prompt.md` — project description exists

**Scoring**:
| Dimension | Check |
|:---|:---|
| Completeness | 3 files exist, each > 500 words |
| Consistency | Competitors referenced consistently across files |
| Quality | Top-10 GitHub repos listed with stars, top-3 deep analyzed |
| Actionability | Feature inventory clear enough for BRD generation |

---

### Phase 1 Gate: BRD → Tech Design

**Required artifacts**:
- [ ] 9 BRD docs (00-08) — all exist, non-empty
- [ ] 3 debate artifacts — architecture, technology, flow-logic
- [ ] `decision_log.md` — has entries

**Scoring**:
| Dimension | Check |
|:---|:---|
| Completeness | 12 files (9 BRD + 3 debate) all exist and > 200 words each |
| Consistency | FR→BR references valid, SM→Entity match domain model |
| Quality | Architecture debate has ≥ 2 patterns scored, technology per-layer |
| Actionability | Tech stack decided, architecture chosen, flows validated |

---

### Phase 2a Gate: Tech Design → UI/UX

**Required artifacts**:
- [ ] 10 tech docs (00-09) — all exist
- [ ] `tech-review-report.md` — no 🔴 Critical issues

**Scoring**:
| Dimension | Check |
|:---|:---|
| Completeness | 11 files, C4 diagrams present, ERD present |
| Consistency | Every FR → API endpoint → DB table traceable |
| Quality | DDD aggregates defined, state machines complete |
| Actionability | API contracts detailed enough for frontend dev |

---

### Phase 2b Gate: UI/UX → OpenSpec

**Required artifacts**:
- [ ] 7 UI/UX docs (00-06) — all exist
- [ ] `mockups/` — ≥ 4 PNG files
- [ ] `ui-design-review.md`

**Scoring**:
| Dimension | Check |
|:---|:---|
| Completeness | 7 docs + mockups exist |
| Consistency | Components used in mockups match catalog |
| Quality | Design tokens cover light+dark, responsive breakpoints |
| Actionability | Component specs detailed enough for code gen |

---

### Phase 3 Gate: OpenSpec → Code Gen

**Required artifacts**:
- [ ] OpenSpec proposal, design, tasks artifacts exist
- [ ] `tasks.md` has implementable task list

**Scoring**:
| Dimension | Check |
|:---|:---|
| Completeness | All OpenSpec artifacts generated |
| Consistency | Tasks traceable to FRs and tech design |
| Quality | Tasks are self-contained with acceptance criteria |
| Actionability | Each task specifies files to create/modify |

---

### Phase 4 Gate: Code Gen → Test & Deploy

**Required artifacts**:
- [ ] Source code compiles: `./gradlew build -x test`
- [ ] Test files exist in `src/test/`
- [ ] Tests pass: `./gradlew test`

**Scoring**:
| Dimension | Check |
|:---|:---|
| Completeness | All modules have entity/repo/service/controller |
| Consistency | Code matches tech design APIs |
| Quality | Tests exist, coverage > 60% |
| Actionability | Build succeeds, ready for integration test |

---

### Phase 5 Gate: Deploy → Complete

**Required artifacts**:
- [ ] Docker Compose services all healthy
- [ ] Smoke test passes
- [ ] `deployment-report.md` generated

**Scoring**:
| Dimension | Check |
|:---|:---|
| Completeness | All services running |
| Consistency | API responds correctly |
| Quality | Health checks passing |
| Actionability | User can access the application |

---

## Gate Report Format

After each gate evaluation, append to `phase_gates.md`:

```markdown
## Gate: Phase {N} → Phase {N+1}
- **Date**: YYYY-MM-DD HH:MM
- **Pipeline**: {wf_e2e / wf_feature_add}
- **Project**: {project-name}

### Scores
| Dimension | Score | Weight | Weighted |
|:---|:---:|:---:|:---:|
| Completeness | X.X | 30% | X.XX |
| Consistency | X.X | 25% | X.XX |
| Quality | X.X | 25% | X.XX |
| Actionability | X.X | 20% | X.XX |
| **Composite** | | | **X.XX** |

### Disposition: ✅ PASS / 🔄 REVISE / ❌ FAIL

### Issues (if REVISE/FAIL)
| # | Dimension | Issue | Fix Action |
|---|:---:|---|---|

### Decision: Proceed to Phase {N+1} [AUTO-GATE]
```

---

## Integration with Autonomous Deliberation

Gate decisions follow `autonomous-deliberation` protocol:

| Gate Result | Deliberation Tier |
|:---|:---|
| ✅ PASS (≥ 7.0) | Tier 1 [AUTO] — proceed immediately |
| 🔄 REVISE (5.0-6.9) | Tier 2 [DEBATE] — auto-fix + re-score |
| ❌ FAIL (< 5.0) | Tier 3 [HUMAN] — stop + escalate |

All gate decisions logged to `decision_log.md`.

---

## Guardrails

- **DO** run gate checks at every phase boundary — no exceptions
- **DO** log all gate evaluations (even PASS) for traceability
- **DO** commit gate report before starting next phase
- **DO NOT** skip gate for "small" phases — consistency matters
- **DO NOT** override FAIL without human approval
- **MAX 2 revision cycles** per gate — after that, it's a FAIL

## Limitations
- Automated scoring is heuristic-based — may miss nuanced quality issues.
- FAIL disposition always requires human review before pipeline can resume.
