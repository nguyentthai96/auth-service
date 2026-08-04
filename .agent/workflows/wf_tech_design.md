---
description: "Phase 2a: Generate 10 Technical Design documents from approved BRD + architecture/technology debate artifacts. Includes multi-agent security/performance review."
---

# Workflow: Technical Design (`/wf_tech_design`)

## Overview

Tạo 10 tài liệu Technical Design từ BRD + debate artifacts đã approved. Bao gồm multi-agent review bởi Architect, Security Auditor, và Performance Engineer.

---

## Prerequisites

- `/wf_brd` đã hoàn tất với status APPROVED
- Files exist:
  - `project_docs/{project-name}/brds/00..08-*.md`
  - `project_docs/{project-name}/brds/architecture-debate.md`
  - `project_docs/{project-name}/brds/technology-analysis.md`
  - `project_docs/{project-name}/brds/flow-logic-review.md`

## Trigger

```
/wf_tech_design {project-name}
```

---

## Steps

### Step 1: Validate Prerequisites

1. Check BRD docs exist and are APPROVED
2. Check debate artifacts exist (architecture, technology, flow-logic)
3. Read all input files to build context

### Step 2: Load Skills

Read:
- `.agent/skills/tech-design-generator/SKILL.md`
- `.agent/skills/tech-design-generator/references/TECH_TEMPLATES.md`
- `.agent/skills/multi-agent-brainstorming/SKILL.md`

### Step 3: Generate Tech Docs (Sequential — Phase 2 of skill)

Generate 10 documents in order using templates:

| # | Document | Depends On |
|---|----------|-----------|
| 00 | system-overview | architecture-debate, technology-analysis |
| 01 | roles-permissions | BRD 01 stakeholders |
| 02 | module-breakdown | system-overview + BRD 03 |
| 03 | domain-model (DDD) | BRD 06 + technology choice |
| 04 | database-design | domain-model |
| 05 | api-design | module-breakdown + BRD 03 |
| 06 | main-workflows | BRD 02 + api-design |
| 07 | state-machines | BRD 05 + domain-model |
| 08 | validation-rules | BRD 04 + api-design |
| 09 | error-handling | api-design + validation-rules |

### Step 4: 🔥 Multi-Agent Technical Review (Phase 3 of skill)

Invoke `multi-agent-brainstorming` with 5 agents:

| Agent | Focus |
|:---|:---|
| 🏗️ **Architect** | C4 coherence, module coupling, bounded contexts |
| 😈 **Skeptic** | Scalability bottlenecks, SPOFs, over-engineering |
| 🔒 **Security Auditor** | OWASP, auth gaps, data exposure |
| ⚡ **Perf. Engineer** | N+1 queries, missing indexes, cache strategy |
| ⚖️ **Arbiter** | BRD traceability, resolve conflicts, disposition |

**Output**: `technical/tech-review-report.md`

### Step 5: Revise & Finalize

1. Fix all 🔴 Critical issues
2. Cross-reference: FR → API → DB → validation (end-to-end traceability)
3. Log decisions to `decision_log.md`

### Step 6: Auto-Commit

```bash
git add project_docs/{project-name}/technical/
git commit -m "docs({project-name}): tech design — 10 docs + review report

Architecture: {pattern}
Tech Stack: {backend}/{db}/{mobile}
Review: {APPROVED} (Score: X/10)"
```

### Step 7: Transition

```
═══════════════════════════════════════
TECH DESIGN COMPLETE
═══════════════════════════════════════
Next: /wf_ui_design {project-name}
═══════════════════════════════════════
```

---

## Output Validation

- [ ] 10 tech docs (00-09) exist
- [ ] `tech-review-report.md` generated
- [ ] No 🔴 Critical issues remain
- [ ] FR traceability: every FR → API → DB
- [ ] `decision_log.md` updated
- [ ] Changes committed to git

---

## Next Step

```
/wf_ui_design {project-name}
```
