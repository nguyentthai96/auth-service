---
name: tech-design-generator
description: "Generate 10 structured Technical Design documents from approved BRD + debate artifacts. Covers system architecture (C4), RBAC, module breakdown, DDD domain model, database design, API contracts, sequence diagrams, state machines, validation rules, and error handling. Use after /wf_brd completes with architecture and technology decisions."
---

# Tech Design Generator

## Purpose

Transform **approved BRD documents + architecture/technology debate artifacts** into **10 Technical Design documents** that provide implementation-ready specifications.

---

## When to Use

- After `/wf_brd` completes with APPROVED status
- When `architecture-debate.md` and `technology-analysis.md` exist
- As the core step in the `/wf_tech_design` workflow

## When NOT to Use

- BRD documents don't exist → run `/wf_brd` first
- Architecture/tech not decided → run `/wf_brd` with debates first

---

## Input

Required files from BRD phase:
```
project_docs/{project-name}/
├── brds/
│   ├── 00-project-overview.md .. 08-acceptance-mvp-future.md
│   ├── architecture-debate.md       ← Architecture decision
│   ├── technology-analysis.md       ← Technology stack decision
│   └── flow-logic-review.md         ← Validated business flows
├── research_notes.md
└── decision_log.md
```

---

## Output

```
project_docs/{project-name}/technical/
├── 00-system-overview.md            ← C4 architecture diagrams
├── 01-roles-permissions.md          ← RBAC/authorization design
├── 02-module-breakdown.md           ← Module decomposition + dependencies
├── 03-domain-model.md               ← DDD aggregates, entities, value objects
├── 04-database-design.md            ← ERD, table design, indexes, migrations
├── 05-api-design.md                 ← REST/gRPC API contracts
├── 06-main-workflows.md             ← Sequence diagrams per use case
├── 07-state-machines.md             ← Detailed state machines with guards
├── 08-validation-rules.md           ← Input/output validation catalog
├── 09-error-handling.md             ← Error codes, exception hierarchy
└── tech-review-report.md            ← Multi-agent review results
```

For document templates, see [references/TECH_TEMPLATES.md](references/TECH_TEMPLATES.md).

---

## Process (4 Phases)

### Phase 1: Context Loading

1. Read all BRD documents (00-08)
2. Read `architecture-debate.md` → extract chosen pattern + constraints
3. Read `technology-analysis.md` → extract chosen stack per layer
4. Read `flow-logic-review.md` → extract validated flows + issues fixed
5. Read `decision_log.md` → understand prior decisions

### Phase 2: Generate Tech Docs (Sequential)

Generate **in order** — later docs reference earlier ones:

| # | Document | Key Input | Key Output |
|---|----------|-----------|------------|
| 00 | system-overview | architecture-debate | C4 Context, Container, Component diagrams |
| 01 | roles-permissions | BRD 01 (stakeholders) | RBAC matrix, permission model |
| 02 | module-breakdown | C4 + BRD 03 (FRs) | Module graph, dependency matrix |
| 03 | domain-model | BRD 06 + technology-analysis | DDD aggregates, bounded contexts |
| 04 | database-design | domain-model + tech stack | ERD, table DDL, index strategy |
| 05 | api-design | BRD 03 + module-breakdown | Endpoint catalog, request/response schemas |
| 06 | main-workflows | BRD 02 + api-design | Sequence diagrams per use case |
| 07 | state-machines | BRD 05 + domain-model | Implementation-level state machines |
| 08 | validation-rules | BRD 04 + api-design | Validation rule catalog |
| 09 | error-handling | api-design + validation | Error code enum, exception hierarchy |

### Phase 3: 🔥 Multi-Agent Technical Review

Invoke `multi-agent-brainstorming` with 5 agents:

| Agent | Focus |
|:---|:---|
| 🏗️ **Architect** | System coherence, C4 completeness, module coupling |
| 😈 **Skeptic** | Scalability bottlenecks, single points of failure, over-engineering |
| 🔒 **Security Auditor** | OWASP compliance, auth gaps, data exposure, injection points |
| ⚡ **Performance Engineer** | N+1 queries, missing indexes, cache strategy, hot paths |
| ⚖️ **Arbiter** | Resolve conflicts, verify BRD traceability, declare disposition |

**Review checklist**:
- [ ] C4 diagrams match module breakdown
- [ ] Every FR has a corresponding API endpoint
- [ ] Every API endpoint has validation rules
- [ ] Database indexes cover common query patterns
- [ ] State machines have complete transition coverage
- [ ] Error codes are unique and categorized
- [ ] RBAC covers all API endpoints
- [ ] Sequence diagrams handle error paths

**Output**: `technical/tech-review-report.md`

```markdown
# Technical Review Report

## Summary
| Dimension | Score (1-10) | Issues |
|:---|:---:|:---|
| Architecture coherence | X | ... |
| Security posture | X | ... |
| Performance readiness | X | ... |
| BRD traceability | X | ... |

## Issues Found
| # | Severity | Area | Issue | Agent | Fix |
|---|:---:|---|---|---|---|
| 1 | 🔴 | API | Missing auth on admin endpoints | Security | Add @PreAuthorize |

## Disposition: APPROVED / REVISE
```

### Phase 4: Revise & Finalize

1. Fix all 🔴 Critical issues from review
2. Fix 🟡 Major issues where feasible
3. Update `decision_log.md` with tech decisions
4. Cross-reference: every FR traceable → API → DB → validation

---

## Transition

```
═══════════════════════════════════════
TECH DESIGN COMPLETE
═══════════════════════════════════════
Project:         {project-name}
Documents:       10 tech docs + review report
Architecture:    {pattern} ({N} containers)
APIs:            {M} endpoints defined
Entities:        {K} domain entities
Database:        {L} tables designed
Review:          {APPROVED} (Score: X/10)
═══════════════════════════════════════
Ready: /wf_ui_design {project-name}
═══════════════════════════════════════
```

---

## Guardrails

- **DO** use C4 model for architecture diagrams (Context → Container → Component)
- **DO** trace every FR to API endpoint to DB table
- **DO** include error paths in sequence diagrams
- **DO** define indexes for every foreign key and common query
- **DO NOT** skip security review — it's mandatory
- **DO NOT** duplicate BRD content — reference it
- **DO NOT** choose tech stack here — use decisions from `technology-analysis.md`

## Limitations
- Use this skill only when the task clearly matches the scope described above.
- Do not treat the output as a substitute for environment-specific validation, testing, or expert review.
- Stop and ask for clarification if required inputs, permissions, safety boundaries, or success criteria are missing.
