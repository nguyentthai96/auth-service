---
description: "Phase 1: Generate 9 structured BRD documents from ideation research output. Includes multi-agent quality gate review."
---

# Workflow: BRD Generation (`/wf_brd`)

## Overview

Tạo 9 tài liệu BRD + 3 debate artifacts từ output của `/wf_ideate`. Bao gồm **3 vòng multi-agent debate** phân tích architecture, technology, và business flow logic.

---

## Prerequisites

- `/wf_ideate` đã hoàn tất
- Files exist:
  - `project_docs/{project-name}/brds/initial-idea.md`
  - `project_docs/{project-name}/brds/competitor_analysis.md`
  - `project_docs/{project-name}/brds/prompt.md`
  - `project_docs/{project-name}/research_notes.md`

## Trigger

```
/wf_brd {project-name}
```

---

## Steps

### Step 1: Validate Prerequisites

1. Check `project_docs/{project-name}/` exists
2. Check all required input files exist
3. If missing → suggest running `/wf_ideate` first
4. Read all input files to build context

### Step 2: Load Skills

Read skills:
- `.agent/skills/brd-generator/SKILL.md` — main process
- `.agent/skills/brd-generator/references/DOCUMENT_TEMPLATES.md` — templates
- `.agent/skills/multi-agent-brainstorming/SKILL.md` — debate protocol
- `.agent/skills/autonomous-deliberation/SKILL.md` — decision protocol

### Step 3: Draft BRD Documents (Phase 1-2 of skill)

Generate 9 documents **in order** using templates from references:

| # | Document | Key Content |
|---|----------|-------------|
| 00 | project-overview | Vision, goals, success criteria |
| 01 | stakeholders-scope | Personas, scope boundaries |
| 02 | business-workflow | Workflow diagrams (Mermaid) |
| 03 | functional-requirements | FR-001..N (Given/When/Then) |
| 04 | business-rules | BR-001..N, decision tables |
| 05 | state-machine | State diagrams (Mermaid) |
| 06 | domain-model | Entity relationships (Mermaid) |
| 07 | non-functional-risk | NFR + Risk matrix |
| 08 | acceptance-mvp-future | MVP definition, roadmap |

### Step 4: 🔥 Multi-Agent Architecture Debate (Phase 3a of skill)

Follow `brd-generator` skill → Phase 3 → **Debate 1**.

Invoke `multi-agent-brainstorming` with:
- **Topic**: "Evaluate architecture patterns for {project-name}"
- **Input**: docs 02, 06, 07 (workflows, domain model, NFRs)
- **Agents**: Architect, Skeptic, Constraint Guardian, User Advocate, Arbiter

**Output**: `brds/architecture-debate.md`
- 2-3 architecture patterns evaluated
- Scoring matrix per agent
- Recommendation with trade-offs
- Revisit triggers documented
- All decisions → `decision_log.md` (via `autonomous-deliberation`)

### Step 5: 🔥 Multi-Agent Technology Stack Analysis (Phase 3b of skill)

Follow `brd-generator` skill → Phase 3 → **Debate 2**.

Invoke `multi-agent-brainstorming` with:
- **Topic**: "Evaluate technology stack for {project-name}"
- **Input**: architecture recommendation + docs 03, 07 + competitor tech insights
- **Agents**: Architect, Skeptic, Pragmatist, Constraint Guardian, Arbiter

**Evaluate per layer**:
| Layer | Example Options |
|:---|:---|
| Backend | Spring Boot / Go / Rust |
| Database | PostgreSQL / MySQL / MongoDB |
| Cache | Redis / Memcached |
| Frontend | React / Vue / Angular |
| Mobile | Flutter / React Native / Native |
| Queue/Event | Kafka / RabbitMQ / Redis Streams |
| Auth | Keycloak / Auth0 / Custom |

**Output**: `brds/technology-analysis.md`
- Per-layer evaluation matrix
- Agent perspectives documented
- Consensus decisions with reasoning
- Competitor technology insights integrated
- All decisions → `decision_log.md`

### Step 6: 🔥 Multi-Agent Flow Logic Validation (Phase 3c of skill)

Follow `brd-generator` skill → Phase 3 → **Debate 3**.

Invoke `multi-agent-brainstorming` with:
- **Topic**: "Validate business flows and state machines for {project-name}"
- **Input**: docs 02, 05 (workflows, state machines)
- **Agents**: Architect, Skeptic, User Advocate, Constraint Guardian, Arbiter

**Review checklist**:
- [ ] Edge cases: concurrent access, partial failures
- [ ] Rollback/compensation flows for critical operations
- [ ] Idempotency of key operations
- [ ] Dead-end states in state machines
- [ ] Missing transitions (e.g., EXPIRED, SUSPENDED)
- [ ] User-facing error recovery paths
- [ ] Transaction boundaries and data consistency

**Output**: `brds/flow-logic-review.md`
- Issues found with severity (🔴 Critical / 🟡 Major / 🟢 Minor)
- Fixes required and proposed solutions
- Approved flows listed
- All decisions → `decision_log.md`

### Step 7: Revise & Cross-Reference (Phase 4 of skill)

1. **Apply debate fixes** to affected BRD docs
2. **Cross-reference validation**:
   - FR → BR references consistent
   - FR → WF references consistent
   - SM → Entity references match domain model
   - MVP features in `08` match 🔴 priorities in `03`
   - Architecture/tech decisions reflected in `07` NFRs
3. **Update** `decision_log.md` with all architecture/technology decisions

### Step 8: Quality Gate (Phase 5 of skill)

Arbiter agent final pass:
- [ ] 9 BRD documents complete
- [ ] 3 debate artifacts generated
- [ ] Cross-references consistent
- [ ] No 🔴 Critical issues remain unfixed
- [ ] `decision_log.md` updated

**Disposition**: `APPROVED` | `REVISE` (max 2 cycles) | `REJECT`

### Step 9: Transition Summary

```
═══════════════════════════════════════
BRD GENERATION COMPLETE
═══════════════════════════════════════
Project:           {project-name}
Documents:         9 BRD + 3 debate artifacts
Architecture:      {pattern} (Score: X/10)
Tech Stack:        {backend} + {db} + {mobile}
Flow Issues Fixed: {N} critical, {M} major
Decisions Logged:  {D} entries
Quality Gate:      {APPROVED / REVISED}
═══════════════════════════════════════
Next: /wf_tech_design {project-name}
═══════════════════════════════════════
```

---

## Output Validation

Before marking complete, verify ALL:
- [ ] `brds/00-project-overview.md` through `brds/08-acceptance-mvp-future.md`
- [ ] `brds/architecture-debate.md` — architecture trade-off analysis
- [ ] `brds/technology-analysis.md` — technology stack evaluation
- [ ] `brds/flow-logic-review.md` — flow validation report
- [ ] Cross-references are consistent
- [ ] Quality gate: APPROVED
- [ ] `decision_log.md` updated

---

## Error Handling

| Error | Action |
|:---|:---|
| Missing input files | Stop → suggest `/wf_ideate` |
| Quality gate REJECT (2x) | Stop → escalate to human |
| Debate produces no consensus | Use safe default → log to `open_questions.md` |
| Budget exceeded | Pause → summarize progress → resume later |

---

## Next Step

```
/wf_tech_design {project-name}
```
