# BRD Document Templates

> Reference file for `brd-generator` skill. Contains all 9 BRD document templates.

## Table of Contents

1. [00-project-overview](#00-project-overview)
2. [01-stakeholders-scope](#01-stakeholders-scope)
3. [02-business-workflow](#02-business-workflow)
4. [03-functional-requirements](#03-functional-requirements)
5. [04-business-rules](#04-business-rules)
6. [05-state-machine](#05-state-machine)
7. [06-domain-model](#06-domain-model)
8. [07-non-functional-risk](#07-non-functional-risk)
9. [08-acceptance-mvp-future](#08-acceptance-mvp-future)

---

## 00-project-overview

```markdown
---
type: brd
document: 00-project-overview
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
---

# {Project Name} — Project Overview

## 1. Executive Summary
{1-2 paragraphs: what is this project, why does it exist}

## 2. Problem Statement
{What specific problem does this solve? Who suffers from it?}

## 3. Vision & Goals
### Vision
{Long-term product vision}

### Goals (Measurable)
| # | Goal | Metric | Target |
|---|------|--------|--------|
| G1 | {goal} | {metric} | {target} |

## 4. Target Market
{Domain, market segment, geography}

## 5. Product Overview
{High-level description of the solution}

## 6. Success Criteria
| # | Criteria | Measurement | Threshold |
|---|----------|-------------|-----------|
| SC1 | {criteria} | {how to measure} | {pass/fail value} |

## 7. Constraints & Assumptions
### Constraints
- {technical/business/legal constraints}

### Assumptions
- {key assumptions that must hold true}

## 8. References
- [initial-idea.md](./initial-idea.md)
- [competitor_analysis.md](./competitor_analysis.md)
```

---

## 01-stakeholders-scope

```markdown
---
type: brd
document: 01-stakeholders-scope
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
---

# Stakeholders & Scope

## 1. Stakeholder Map
| Stakeholder | Role | Interest | Influence | Needs |
|:---|:---|:---|:---|:---|
| {name/role} | Primary User | High | Medium | {key needs} |

## 2. User Personas

### Persona 1: {Name}
- **Demographics**: {age, tech-savvy level, context}
- **Goals**: {what they want to achieve}
- **Pain points**: {current frustrations}
- **Usage context**: {when/where/how they use the product}

### Persona 2: ...

## 3. In-Scope (MVP)
- {feature/capability 1}

## 4. Out-of-Scope (Future)
- {explicitly excluded features}

## 5. Scope Boundaries
| Boundary | In | Out |
|:---|:---|:---|
| {area} | {included} | {excluded} |
```

---

## 02-business-workflow

```markdown
---
type: brd
document: 02-business-workflow
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
---

# Business Workflows

## 1. Core Workflow Overview
{High-level description of main business processes}

## 2. Workflow Diagrams

### WF-01: {Workflow Name}
{Description}

\```mermaid
flowchart TD
    A[Start] --> B[Step 1]
    B --> C{Decision}
    C -->|Yes| D[Step 2a]
    C -->|No| E[Step 2b]
    D --> F[End]
    E --> F
\```

**Steps**:
1. {Step description + actor + system behavior}

**Exception Paths**:
- {what happens when X fails}

**Rollback/Compensation**:
- {how to undo partial completion}

### WF-02: ...

## 3. User Journey Map
| Phase | User Action | System Response | Emotion | Touchpoint |
|:---|:---|:---|:---|:---|
| Onboarding | Signs up | Send verification | Curious | Mobile app |

## 4. Cross-Cutting Concerns
- **Idempotency**: Which workflows must be idempotent?
- **Concurrency**: Which workflows may execute in parallel?
- **Timeout**: Maximum duration for each workflow
```

---

## 03-functional-requirements

```markdown
---
type: brd
document: 03-functional-requirements
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
---

# Functional Requirements

## Requirement Catalog

### FR-001: {Requirement Title}
- **Priority**: 🔴 Must-have | 🟡 Should-have | 🟢 Nice-to-have
- **Actor**: {who triggers this}
- **Description**: {what the system must do}
- **Input**: {what data is needed}
- **Output**: {what result is expected}
- **Business Rule**: → BR-{NNN}
- **Workflow**: → WF-{NN}
- **Acceptance Criteria**:
  - Given {context}, When {action}, Then {result}
  - Given {context}, When {action}, Then {result}

### FR-002: ...

## Requirements Matrix
| ID | Title | Priority | Persona | Workflow | BR | Status |
|:---|:---|:---|:---|:---|:---|:---|
| FR-001 | {title} | 🔴 | Persona 1 | WF-01 | BR-001 | DRAFT |
```

---

## 04-business-rules

```markdown
---
type: brd
document: 04-business-rules
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
---

# Business Rules

## Rule Catalog

### BR-001: {Rule Name}
- **Category**: Validation | Calculation | Authorization | Workflow
- **Description**: {precise rule statement}
- **Applies to**: FR-{NNN}
- **Condition**: IF {condition}
- **Action**: THEN {action}
- **Exception**: ELSE {exception handling}
- **Source**: {business stakeholder / regulation / best practice}

## Decision Tables

### DT-01: {Decision Name}
| Condition 1 | Condition 2 | Action |
|:---|:---|:---|
| True | True | {action A} |
| True | False | {action B} |
| False | * | {action C} |
```

---

## 05-state-machine

```markdown
---
type: brd
document: 05-state-machine
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
---

# State Machines

## SM-01: {Entity} State Machine

### States
| State | Description | Entry Condition | Exit Condition |
|:---|:---|:---|:---|
| DRAFT | Initial state | Created | Submitted |
| ACTIVE | In use | Approved | Terminated |

### Transitions
\```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> PENDING: submit
    PENDING --> ACTIVE: approve
    PENDING --> REJECTED: reject
    REJECTED --> DRAFT: revise
    ACTIVE --> TERMINATED: terminate
    TERMINATED --> [*]
\```

### Transition Rules
| From | To | Trigger | Guard | Action | Side Effects |
|:---|:---|:---|:---|:---|:---|
| DRAFT | PENDING | submit | All fields filled | Validate | Notify admin |

### Invariants
- {invariant that must hold across ALL states}

## SM-02: ...
```

---

## 06-domain-model

```markdown
---
type: brd
document: 06-domain-model
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
---

# Domain Model

## 1. Domain Context Map
\```mermaid
graph TB
    subgraph "Core Domain"
        A[{Entity A}]
        B[{Entity B}]
        A -->|"1:N"| B
    end
    subgraph "Supporting Domain"
        C[{Entity C}]
    end
    B -->|"N:1"| C
\```

## 2. Entity Definitions

### Entity: {Name}
- **Description**: {what this represents}
- **Aggregate root?**: Yes/No
- **Key attributes**:
  | Attribute | Type | Required | Constraints | Description |
  |:---|:---|:---|:---|:---|
  | id | UUID | Yes | PK | Unique identifier |
  | name | String | Yes | max 100 | ... |
- **Relationships**:
  - Has many {Entity B} (1:N)
  - Belongs to {Entity C} (N:1)
- **Business rules**: BR-001, BR-003
- **State machine**: SM-01 (if applicable)

## 3. Bounded Context Boundaries
| Context | Entities | Owner | Communication |
|:---|:---|:---|:---|
| {context} | Entity A, B | Team X | REST API |

## 4. Glossary
| Term | Definition | Context |
|:---|:---|:---|
| {term} | {precise definition} | {where used} |
```

---

## 07-non-functional-risk

```markdown
---
type: brd
document: 07-non-functional-risk
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
---

# Non-Functional Requirements & Risk Assessment

## 1. Non-Functional Requirements

### NFR-001: Performance
- **Metric**: API response time
- **Target**: P95 < 500ms
- **Measurement**: APM monitoring

### NFR-002: Availability
- **Target**: 99.5% uptime (MVP)

### NFR-003: Security
- **Standards**: OWASP Top 10
- **Authentication**: {method}
- **Authorization**: RBAC

### NFR-004: Scalability
- **Initial**: {N} concurrent users
- **Growth**: {M} within 1 year

### NFR-005: Usability
- **Target**: Core task completed within {X} minutes

## 2. Risk Assessment
| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|:-----------:|:------:|------------|-------|
| R1 | {risk} | High | High | {mitigation} | {role} |

## 3. Risk Matrix
| | Low Impact | Medium | High |
|:---|:---|:---|:---|
| **High Prob** | Monitor | Mitigate | Prevent |
| **Medium** | Accept | Monitor | Mitigate |
| **Low** | Accept | Accept | Monitor |
```

---

## 08-acceptance-mvp-future

```markdown
---
type: brd
document: 08-acceptance-mvp-future
project: {project-name}
version: 1.0
date: YYYY-MM-DD
status: DRAFT
---

# Acceptance Criteria, MVP Scope & Future Roadmap

## 1. MVP Scope
### MVP Features (🔴 Must-have)
| # | Feature | FR Ref | Acceptance Criteria | Priority |
|---|---------|:---:|---|:---:|
| 1 | {feature} | FR-001 | {criteria} | P0 |

### MVP Boundaries
- **Platforms**: {web/mobile/both}
- **Users**: {which personas}

## 2. Acceptance Criteria

### AC-001: {Feature Name}
- ✅ Given {context}, When {action}, Then {expected}
- ❌ Given {context}, When {action}, Then {should NOT happen}

## 3. Future Roadmap
### Phase 2 (v1.1): {Theme}
- {feature}

### Phase 3 (v2.0): {Theme}
- {feature}

## 4. Release Criteria
- [ ] All MVP features pass acceptance tests
- [ ] No P0/P1 bugs open
- [ ] Performance targets met
- [ ] Security review completed
```
