---
name: brd-generator
description: "Generate 9 structured BRD documents with multi-agent architecture debate, technology trade-off analysis, and flow logic review. Use when transforming ideation research into business requirements with deep quality validation."
---

# BRD Generator

## Purpose

Transform ideation research into **9 Business Requirements Documents** with deep multi-agent analysis of architecture patterns, technology choices, and business flow logic — producing a foundation ready for technical implementation.

---

## When to Use

- After `/wf_ideate` completes successfully
- When `initial-idea.md` and `competitor_analysis.md` exist
- As the core step in the `/wf_brd` workflow

## When NOT to Use

- BRD documents already exist → review/update instead
- Requirements are purely technical → use tech-design-generator
- Without completing ideation → run `/wf_ideate` first

---

## Input

Required files from ideation:
```
project_docs/{project-name}/
├── brds/
│   ├── initial-idea.md
│   ├── competitor_analysis.md
│   └── prompt.md
└── research_notes.md
```

---

## Output

### 9 BRD Documents

```
brds/
├── 00-project-overview.md
├── 01-stakeholders-scope.md
├── 02-business-workflow.md
├── 03-functional-requirements.md
├── 04-business-rules.md
├── 05-state-machine.md
├── 06-domain-model.md
├── 07-non-functional-risk.md
└── 08-acceptance-mvp-future.md
```

### Debate Artifacts

```
brds/
├── architecture-debate.md      ← Architecture trade-off analysis
├── technology-analysis.md      ← Technology stack evaluation
└── flow-logic-review.md        ← Business flow validation report
```

For document templates, see [references/DOCUMENT_TEMPLATES.md](references/DOCUMENT_TEMPLATES.md).

---

## Process (5 Phases)

### Phase 1: Research Ingestion (Solo)

1. Read all ideation output files
2. Extract: MVP features, constraints, personas, competitor insights
3. Build internal context map

### Phase 2: Draft BRD Documents (Solo — Sequential)

Generate documents **in order** (00 → 08). Later docs reference earlier ones.

| # | Document | Key Content |
|---|----------|-------------|
| 00 | project-overview | Vision, goals, success criteria |
| 01 | stakeholders-scope | Personas, scope boundaries |
| 02 | business-workflow | Workflow diagrams (Mermaid flowchart) |
| 03 | functional-requirements | FR-001..N (Given/When/Then) |
| 04 | business-rules | BR-001..N, decision tables |
| 05 | state-machine | State diagrams (Mermaid stateDiagram-v2) |
| 06 | domain-model | Entity relationships (Mermaid graph) |
| 07 | non-functional-risk | NFR + Risk matrix |
| 08 | acceptance-mvp-future | MVP definition, roadmap |

### Phase 3: 🔥 Multi-Agent Architecture & Technology Debate

**This is the core differentiator.** After draft BRDs, invoke a structured debate to evaluate architecture, technology, and flow design.

#### Debate 1: Architecture Pattern Analysis

Spawn 5 agents from `multi-agent-brainstorming` skill:

| Agent | Debate Focus |
|:---|:---|
| 🏗️ **Architect** | Propose 2-3 architecture patterns (Monolith, Modular Monolith, Microservice). Evaluate each against NFRs from doc `07`. |
| 😈 **Skeptic** | Attack each pattern: "Where does this fail under load? What's the operational cost? Hidden complexity?" |
| 🔒 **Constraint Guardian** | Evaluate against real constraints: team size, budget, timeline, infra availability |
| 👤 **User Advocate** | Impact on UX: latency, offline capability, real-time features |
| ⚖️ **Arbiter** | Synthesize, resolve conflicts, produce recommendation |

**Architecture Debate Output** → `brds/architecture-debate.md`:

```markdown
# Architecture Debate Report

## Options Evaluated
| Pattern | Architect | Skeptic | Guardian | User Adv. | Score |
|---------|:---------:|:-------:|:--------:|:---------:|:-----:|
| Modular Monolith | ✅ Simple, fast MVP | ⚠️ Coupling risk | ✅ Small team fit | ✅ Low latency | 8/10 |
| Microservices | ✅ Scalable | ❌ Overkill for MVP | ❌ Infra cost | ⚠️ Network latency | 5/10 |

## Recommended Architecture
{Pattern} — because {synthesized reasoning from all agents}

## Trade-offs Accepted
- {trade-off 1}: Accept {downside} for {benefit}

## Revisit Triggers
- When {condition}, reconsider {alternative}
```

#### Debate 2: Technology Stack Analysis

For each layer (Backend, Frontend, Mobile, DB, Cache, Queue), evaluate options:

| Agent | Debate Focus |
|:---|:---|
| 🏗️ **Architect** | Propose tech options per layer. Evaluate: maturity, ecosystem, performance |
| 😈 **Skeptic** | Challenge: vendor lock-in, learning curve, hiring difficulty, hidden costs |
| 🎯 **Pragmatist** | Team expertise, time-to-market, existing codebase compatibility |
| 🔒 **Constraint Guardian** | License, compliance, security track record |
| ⚖️ **Arbiter** | Decide per layer, document reasoning |

**Technology Analysis Output** → `brds/technology-analysis.md`:

```markdown
# Technology Stack Analysis

## Evaluation Matrix
| Layer | Option A | Option B | Option C | Chosen | Why |
|-------|----------|----------|----------|:------:|-----|
| Backend | Spring Boot (Java) | Gin (Go) | Actix (Rust) | Spring Boot | Team expertise, ecosystem |
| Database | PostgreSQL | MySQL | MongoDB | PostgreSQL | JSONB, extensions, maturity |
| Mobile | Flutter | React Native | Native | Flutter | Single codebase, performance |

## Per-Layer Debate Summary

### Backend: Spring Boot vs Go vs Rust
- **Architect**: Spring Boot — mature ecosystem, JPA, Security modules built-in
- **Skeptic**: Java verbose, memory heavy, but team knows it → reduces risk
- **Pragmatist**: Spring Boot ships fastest with existing team skills
- **Decision**: Spring Boot ✅ (3/3 consensus)
- **Revisit-if**: Need extreme performance (>10K RPS) → consider Go

### Database: PostgreSQL vs MySQL
...

## Competitor Technology Insights
{What tech stacks do Top 3 competitors use? Lessons?}
```

#### Debate 3: Business Flow & Logic Validation

Review all workflow diagrams (doc `02`) and state machines (doc `05`):

| Agent | Debate Focus |
|:---|:---|
| 🏗️ **Architect** | System boundary clarity, service decomposition alignment |
| 😈 **Skeptic** | Edge cases: concurrent access, partial failures, rollback scenarios |
| 👤 **User Advocate** | Flow clarity, error recovery from user perspective, dead ends |
| 🔒 **Constraint Guardian** | Transaction boundaries, data consistency, idempotency |
| ⚖️ **Arbiter** | Flag issues, require fixes |

**Flow Logic Review Output** → `brds/flow-logic-review.md`:

```markdown
# Flow Logic Review Report

## Workflow Issues Found
| # | Workflow | Issue | Severity | Agent | Fix |
|---|---------|-------|:--------:|-------|-----|
| 1 | WF-02: Payment | No rollback on partial failure | 🔴 Critical | Skeptic | Add compensation flow |
| 2 | WF-01: Onboarding | Dead-end after email verification timeout | 🟡 Major | User Adv. | Add retry + support link |

## State Machine Issues
| # | SM | Issue | Severity | Fix |
|---|-----|-------|:--------:|-----|
| 1 | SM-01: Contract | Missing EXPIRED state | 🔴 | Add auto-expire transition |

## Approved Flows
- WF-03: Room Management ✅ (no issues)
- SM-02: Payment Status ✅ (all agents agree)

## Recommendations
- {recommendation 1}
```

### Phase 4: Revise & Cross-Reference

After debate, update BRD documents:

1. **Apply debate fixes** to affected docs (02, 05, 06, 07)
2. **Cross-reference validation**:
   - FR → BR references consistent
   - FR → WF references consistent
   - SM → Entity references match domain model
   - MVP features in `08` match 🔴 priorities in `03`
   - Architecture decision influences NFR targets in `07`
   - Technology choices logged in `decision_log.md`

### Phase 5: Quality Gate (Final Review)

Quick final pass — Arbiter agent confirms:
- [ ] All 9 BRD documents complete
- [ ] 3 debate artifacts generated (architecture, technology, flow)
- [ ] Cross-references consistent
- [ ] No 🔴 Critical issues remain unfixed
- [ ] All decisions logged to `decision_log.md`

**Disposition**: APPROVED | REVISE (max 2 cycles) | REJECT

---

## Transition

```
═══════════════════════════════════════
BRD GENERATION COMPLETE
═══════════════════════════════════════
Project:          {project-name}
Documents:        9 BRD + 3 debate artifacts
Architecture:     {chosen pattern} (Score: X/10)
Tech Stack:       {summary}
Flow Issues Fixed:{N} critical, {M} major
Decisions Logged: {D} entries
═══════════════════════════════════════
Ready: /wf_tech_design {project-name}
═══════════════════════════════════════
```

---

## Guardrails

- **DO** run all 3 debates (architecture, technology, flow logic)
- **DO** generate debate artifacts alongside BRD docs
- **DO** use Mermaid diagrams for workflows and state machines
- **DO** cross-reference between documents (FR↔BR, FR↔WF, SM↔Entity)
- **DO NOT** skip debates — they are mandatory, not optional
- **DO NOT** include implementation code — that's for tech-design-generator
- **DO NOT** auto-resolve 🔴 Critical flow issues — they must be fixed

## Limitations
- Use this skill only when the task clearly matches the scope described above.
- Do not treat the output as a substitute for environment-specific validation, testing, or expert review.
- Stop and ask for clarification if required inputs, permissions, safety boundaries, or success criteria are missing.
