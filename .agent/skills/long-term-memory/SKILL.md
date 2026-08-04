---
name: long-term-memory
description: "Persistent memory system — stores learnings, patterns, decisions, and project context across sessions. Agent remembers past mistakes, successful patterns, and domain knowledge to improve over time. The foundation for continuous learning."
---

# Long-Term Memory System

## Purpose

Cho phép agent **nhớ qua mỗi session** — lưu trữ kinh nghiệm, patterns đã học, quyết định đã làm, lỗi đã gặp, và domain knowledge. Mọi thứ được persist trên filesystem để agent mới load lại ngay.

**Nguyên tắc**: Mỗi task agent hoàn thành → agent trở nên thông minh hơn.

---

## Memory Architecture

```
.agent/memory/
├── index.md                    ← Master index — agent đọc đầu tiên
├── learnings/
│   ├── patterns.md             ← Patterns đã học (what works)
│   ├── anti-patterns.md        ← Anti-patterns đã gặp (what fails)
│   ├── domain-knowledge.md     ← Domain insights (business logic)
│   └── tech-insights.md        ← Technical insights (libs, configs)
├── history/
│   ├── completed_tasks.md      ← Log tất cả tasks đã hoàn thành
│   ├── gate_scores.md          ← Historical gate scores per project
│   └── decision_history.md     ← Key decisions across all projects
├── skills/
│   ├── skill_effectiveness.md  ← Skill nào hiệu quả, skill nào cần cải thiện
│   └── workflow_metrics.md     ← Pipeline performance metrics
└── context/
    ├── {project-1}/
    │   ├── project_summary.md  ← Quick context for this project
    │   ├── architecture.md     ← Architecture decisions snapshot
    │   └── known_issues.md     ← Bugs, quirks, workarounds
    └── {project-2}/
        └── ...
```

---

## Memory Operations

### 1. LOAD (At Session Start)

Agent MUST read memory index at the start of every pipeline:

```markdown
## Memory Load Protocol

1. Read `.agent/memory/index.md` — get overview
2. Read `.agent/memory/learnings/patterns.md` — recall what works
3. Read `.agent/memory/learnings/anti-patterns.md` — recall what fails
4. IF project-specific:
   Read `.agent/memory/context/{project}/project_summary.md`
5. IF relevant domain:
   Read `.agent/memory/learnings/domain-knowledge.md`
```

**Cost**: ~5-10K tokens. Always worth it — prevents repeating mistakes.

### 2. SAVE (After Each Phase/Task)

After completing any significant work, persist learnings:

```markdown
## Memory Save Protocol

1. Extract learnings from current work:
   - What decision was made and why?
   - What pattern was used successfully?
   - What mistake was caught and how?
   - What domain knowledge was discovered?

2. Categorize and append to appropriate file:
   - Technical insight → tech-insights.md
   - Business rule → domain-knowledge.md
   - Successful pattern → patterns.md
   - Mistake/anti-pattern → anti-patterns.md

3. Update task history:
   - Log completion in completed_tasks.md
   - Record gate scores in gate_scores.md

4. Update project context:
   - Refresh project_summary.md if architecture changed
   - Log new known issues
```

### 3. RECALL (During Work)

When encountering a decision or problem, search memory:

```markdown
## Memory Recall Protocol

Before making a decision:
1. Search patterns.md — "Have I solved this before?"
2. Search anti-patterns.md — "Have I failed at this before?"
3. Search decision_history.md — "Have I decided this before?"
4. IF match found → reuse/adapt, don't reinvent
```

### 4. CONSOLIDATE (Periodic)

After every 5 completed tasks, consolidate memory:

```markdown
## Memory Consolidation

1. Review recent entries in all files
2. Merge duplicates
3. Promote frequently-used patterns to top
4. Archive obsolete entries (move to archive/)
5. Update index.md with stats
```

---

## Memory File Formats

### index.md

```markdown
# Agent Memory Index

Last updated: YYYY-MM-DD
Total tasks completed: N
Total projects: M
Total learnings: K

## Quick Stats
- Patterns learned: {N}
- Anti-patterns cataloged: {M}
- Domain insights: {K}
- Average gate score: {X.X}

## Active Projects
| Project | Last Activity | Phase | Health |
|:---|:---|:---:|:---:|
| rentivo | 2026-07-02 | Deploy | 🟢 |

## Recent Learnings (last 5)
1. {learning}
2. ...
```

### patterns.md

```markdown
# Learned Patterns

## PAT-001: {Pattern Name}
- **Learned from**: Project {name}, Phase {N}
- **Date**: YYYY-MM-DD
- **Context**: {When this applies}
- **Pattern**: {What to do}
- **Why it works**: {Explanation}
- **Used count**: {N times}
- **Last used**: YYYY-MM-DD

## PAT-002: ...
```

### anti-patterns.md

```markdown
# Known Anti-Patterns

## ANTI-001: {Anti-Pattern Name}
- **Learned from**: Project {name}, Phase {N}
- **Date**: YYYY-MM-DD
- **What happened**: {Description of failure}
- **Root cause**: {Why it failed}
- **Fix applied**: {What solved it}
- **Prevention**: {How to avoid in future}
- **Severity**: 🔴 Critical / 🟡 Medium / 🟢 Low

## ANTI-002: ...
```

### completed_tasks.md

```markdown
# Task Completion History

## TASK-001: {Project} — {Description}
- **Date**: YYYY-MM-DD
- **Mode**: e2e / feature_add / patch
- **Duration**: {time}
- **Tokens**: {N} (~${cost})
- **Gate scores**: [7.6, 8.3, 7.2, ...]
- **Avg gate score**: {X.X}
- **Decisions**: Auto:{N} Debate:{M} Human:{K}
- **Outcome**: ✅ Success / ⚠️ Partial / ❌ Failed
- **Key learnings**: {1-2 sentence summary}
```

### skill_effectiveness.md

```markdown
# Skill Effectiveness Tracking

| Skill | Used | Success Rate | Avg Time | Notes |
|:---|:---:|:---:|:---:|:---|
| brd-generator | 5 | 100% | 45min | Solid |
| code-generator | 3 | 67% | 60min | Needs entity mapping fix |
| auto-test-generator | 2 | 50% | 30min | State machine tests weak |

## Improvement Queue
1. auto-test-generator: add parametrized state machine tests
2. code-generator: improve JPA relationship mapping
```

---

## Memory-Aware Pipeline Integration

### In `/wf_e2e` and `/wf_feature_add`

```
Step 0: Initialize
  → LOAD memory (index + patterns + anti-patterns)
  → IF project exists in memory → LOAD project context
  → Apply learnings to current pipeline config

Each Phase:
  → RECALL relevant patterns before starting
  → Execute phase
  → Run phase gate
  → SAVE learnings from this phase

Final Step:
  → SAVE comprehensive task record
  → UPDATE skill_effectiveness
  → CONSOLIDATE if 5th task
```

### In `agent-governor`

Governor reads memory to:
- Predict gate scores based on history
- Adjust model routing based on past cost data
- Skip debates for previously-decided patterns
- Pre-warm context with project-specific knowledge

---

## Guardrails

- **DO** load memory at every session start — non-negotiable
- **DO** save after every phase completion
- **DO** keep entries concise (< 200 words each)
- **DO** include "Used count" to track pattern frequency
- **DO NOT** store raw artifacts in memory — only learnings
- **DO NOT** let memory files exceed 500 lines — consolidate
- **DO NOT** delete entries — archive them
- **MAX** 50 patterns, 30 anti-patterns, 20 domain insights active at once

## Limitations
- Memory is file-based — no vector search. Rely on structured markdown + grep.
- Agent must explicitly load memory — not automatic across IDE sessions.
- Consolidation is manual (triggered every 5 tasks) — may accumulate noise.
