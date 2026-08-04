---
description: "Phase 0: Transform raw idea → structured research. Web search, GitHub top 10 + top 3 deep analysis, competitor analysis, feature mapping."
---

# Workflow: Ideation & Research (`/wf_ideate`)

## Overview

Biến ý tưởng thô thành research có cấu trúc. Đây là bước đầu tiên trong pipeline, output feeds vào `/wf_brd`.

---

## Prerequisites

- Input: Ý tưởng (text format, bất kỳ độ dài)
- Optional: Domain, target users, constraints

## Trigger

```
/wf_ideate "Mô tả ý tưởng sản phẩm..."
```

hoặc

```
/wf_ideate {project-name} --idea "Mô tả..."
```

---

## Steps

### Step 1: Initialize Project Structure

```
project_docs/{project-name}/
├── brds/
├── technical/
├── ui-ux/
│   └── mockups/
├── research_notes.md
└── decision_log.md        ← từ template
```

- Derive `{project-name}` from idea (kebab-case, lowercase)
- If project already exists → ASK user: resume or create new?
- Initialize `decision_log.md` from template (xem `autonomous-deliberation` skill)

### Step 2: Parse & Understand

Read skill: `.agent/skills/ideation-research/SKILL.md`

Follow **Step 1: Parse & Understand the Idea** in the skill:
1. Extract problem statement, target users, domain, initial scope
2. Log understanding as first entry in `research_notes.md`

### Step 3: Competitor Research

Follow **Step 2: Web Search** + **Step 3: GitHub Scan** in the skill:

1. **Web search** — find commercial competitors
   - Use `search_web` tool with relevant queries
   - Capture: name, URL, features, pricing, strengths, weaknesses

2. **GitHub scan** — find open-source alternatives
   - Search for top repos by stars
   - Create ranked list: **Top 10 by ⭐ stars**
   - Deep-analyze **Top 3 best matching**:
     - Read README via `read_url_content`
     - Analyze: feature overlap, tech stack, architecture, strengths, weaknesses, lessons

3. Write `competitor_analysis.md` following the template in ideation-research skill

### Step 4: Market Analysis & Feature Mapping

Follow **Step 4** and **Step 5** in the skill:

1. Synthesize market insights from competitor research
2. Create feature inventory with priorities (🔴 Must / 🟡 Should / 🟢 Nice)
3. Append to `research_notes.md`

### Step 5: Generate Output Documents

1. Generate `brds/initial-idea.md` — structured idea document
2. Generate `brds/competitor_analysis.md` — competitor analysis (already from Step 3)
3. Generate `brds/prompt.md` — context summary for BRD agent
4. Finalize `research_notes.md`

### Step 6: Deliberation Check

Invoke `autonomous-deliberation` skill:
- Any decisions made during research → log to `decision_log.md`
- Any open questions → log to `open_questions.md`

### Step 7: Transition Summary

Print transition summary:

```
═══════════════════════════════════════
IDEATION COMPLETE
═══════════════════════════════════════
Project:     {project-name}
Domain:      {domain}
Competitors: {N} commercial + {M} open-source analyzed
MVP Features:{K} must-have identified
Top Match:   {repo-name} (⭐ {stars}, {X}% match)
═══════════════════════════════════════
Next: /wf_brd {project-name}
═══════════════════════════════════════
```

---

## Output Validation

Before marking complete, verify:
- [ ] `brds/initial-idea.md` exists and has problem statement + MVP features
- [ ] `brds/competitor_analysis.md` exists with Top 10 table + Top 3 deep analysis
- [ ] `brds/prompt.md` exists with context summary
- [ ] `research_notes.md` exists with raw research data
- [ ] `decision_log.md` initialized

---

## Next Step

```
/wf_brd {project-name}
```
