---
name: autonomous-deliberation
description: "3-tier self-decision protocol for agent autonomy. Classifies decisions by impact (Low/Medium/High), auto-resolves low-impact, runs multi-agent debate for medium-impact, and escalates high-impact to human. All decisions logged to decision_log.md."
---

# Autonomous Deliberation Protocol

## Purpose

Enable agents to **make decisions autonomously** while maintaining quality and traceability.
Replace constant human interruption with a structured self-deliberation system.

**Core principle**: Agents decide what they can, debate what they should, and escalate what they must.

---

## When to Use

- During any pipeline step when a question, tradeoff, or ambiguity arises
- When multiple valid approaches exist and a choice must be made
- When assumptions need to be made to continue work
- When design decisions have downstream implications

## When NOT to Use

- For explicit user instructions (just follow them)
- For factual lookups (just search and verify)
- For trivial formatting/syntax choices (just pick one)

---

## The 3-Tier Protocol

### Tier 1: 🟢 Auto-Decide (Low Impact)

**Criteria** — Decision meets ALL of:
- Easily reversible (< 30 min to change)
- No architectural impact
- Single component affected
- Industry-standard answer exists
- No cost implications

**Examples**: Naming conventions, import order, comment style, file organization within a module, choosing between equivalent libraries for utility tasks.

**Action**:
1. Make the decision immediately
2. Log to `decision_log.md` with tag `[AUTO]`
3. Continue pipeline — do NOT pause

**Log format**:
```markdown
## DEC-{NNN}: {Title} [AUTO]
- **Date**: YYYY-MM-DD
- **Context**: {Why this decision arose}
- **Decision**: {What was chosen}
- **Reasoning**: {One-line rationale}
- **Reversibility**: 🟢 Easy
```

---

### Tier 2: 🟡 Multi-Agent Debate (Medium Impact)

**Criteria** — Decision meets ANY of:
- Multiple valid technical approaches
- Affects 2+ components
- Impacts API contracts or data schema
- Involves technology/library selection
- Trade-off between competing concerns (performance vs maintainability, etc.)

**Examples**: REST vs gRPC, SQL vs NoSQL for a specific use case, monolith vs microservice boundary, caching strategy, authentication approach.

**Action**:
1. Spawn 3 persona agents for structured debate
2. Each agent analyzes from their perspective
3. Tally consensus (≥2/3 = decided)
4. If no consensus → use safe default + defer to `open_questions.md`
5. Log to `decision_log.md` with tag `[DEBATE]`
6. Continue pipeline

**Persona Agents**:

| Agent | Perspective | Focus |
|:---|:---|:---|
| 🏗️ **Architect** | System design, scalability, maintainability | Long-term implications |
| 😈 **Skeptic** | Risk, failure modes, hidden complexity | What can go wrong |
| 🎯 **Pragmatist** | Cost, timeline, team capability, MVP fit | What ships fastest reliably |

**Debate Protocol**:
1. Orchestrator presents the question + options + context
2. Each agent provides analysis (max 200 words each)
3. Orchestrator tallies votes and synthesizes reasoning
4. If ≥2/3 agree → CONSENSUS → apply decision
5. If no consensus → DEFER → use safe default, log to `open_questions.md`

**Log format**:
```markdown
## DEC-{NNN}: {Title} [DEBATE]
- **Date**: YYYY-MM-DD
- **Context**: {Why this decision arose}
- **Options**:
  | Option | Architect | Skeptic | Pragmatist |
  |:---|:---|:---|:---|
  | Option A | {analysis} | {analysis} | {analysis} |
  | Option B | {analysis} | {analysis} | {analysis} |
- **Decision**: {Chosen option}
- **Confidence**: 🟢 High (3/3) | 🟡 Medium (2/3) | 🔴 Deferred
- **Reasoning**: {Synthesized rationale}
- **Revisit-if**: {Conditions that would invalidate this decision}
- **Impact**: {Scope of downstream effects}
- **Status**: APPLIED | DEFERRED
```

---

### Tier 3: 🔴 Human Escalation (High Impact)

**Criteria** — Decision meets ANY of:
- Irreversible or very costly to reverse
- Changes system architecture fundamentally
- Introduces new external dependency or vendor lock-in
- Security or compliance implications
- Cost exceeds defined threshold
- Contradicts existing user requirements
- Affects user-facing behavior significantly

**Examples**: Choosing primary database engine, cloud provider lock-in, breaking API changes, security model decisions, payment provider selection.

**Action**:
1. STOP pipeline execution
2. Document the question clearly with options and analysis
3. Log to `decision_log.md` with tag `[HUMAN]` and status `PENDING`
4. Present to human with structured options
5. Wait for human response
6. Update log with human's decision and resume

**Log format**:
```markdown
## DEC-{NNN}: {Title} [HUMAN]
- **Date**: YYYY-MM-DD
- **Context**: {Why this decision arose}
- **Why escalated**: {Which high-impact criteria triggered}
- **Options**:
  | Option | Pros | Cons | Recommendation |
  |:---|:---|:---|:---|
  | Option A | ... | ... | ✅ Recommended |
  | Option B | ... | ... | |
- **Agent Recommendation**: {Option + reasoning}
- **Human Decision**: {PENDING / filled after response}
- **Status**: PENDING → APPLIED
```

---

## Decision Counter

Maintain a running counter in the decision log header:

```markdown
<!-- decision_counter: 0 -->
<!-- last_updated: YYYY-MM-DD -->
```

Increment for each new decision. Use format `DEC-001`, `DEC-002`, etc.

---

## Open Questions File

When a Tier 2 debate results in no consensus:

**File**: `{project_docs_dir}/open_questions.md`

```markdown
# Open Questions

## OQ-001: {Question}
- **From**: DEC-{NNN}
- **Date**: YYYY-MM-DD
- **Default applied**: {Safe default being used}
- **Why deferred**: No consensus among agents
- **Impact if wrong**: {Consequences}
- **Status**: OPEN | RESOLVED (DEC-{MMM})
```

---

## Change Request Workflow

When human reviews `decision_log.md` and wants to change a past decision:

1. Human creates Change Request: "Change DEC-003 from REST to gRPC"
2. Agent:
   - Marks `DEC-003` as `SUPERSEDED by DEC-{new}`
   - Creates new `DEC-{new}` with `[CHANGE-REQUEST]` tag
   - Runs impact analysis: which files/artifacts are affected
   - Propagates change through affected artifacts
   - Logs all propagation in the new decision entry

---

## Guardrails

- **NEVER** auto-decide on Tier 3 items — always escalate
- **NEVER** skip logging — every decision must be traceable
- **NEVER** re-debate a decided item unless new evidence emerges
- **ALWAYS** use safe/conservative defaults when deferring
- **ALWAYS** include `Revisit-if` conditions for Tier 2 decisions
- **MAX 3 debates** per pipeline step — if more needed, escalate to human

## Limitations
- Use this skill only when the task clearly matches the scope described above.
- Do not treat the output as a substitute for environment-specific validation, testing, or expert review.
- Stop and ask for clarification if required inputs, permissions, safety boundaries, or success criteria are missing.
