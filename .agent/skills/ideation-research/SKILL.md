---
name: ideation-research
description: "Transform raw ideas into structured research output. Web search for competitors, GitHub scan for top 10 repos by stars + top 3 deep analysis, market analysis, and feature mapping. Output: initial-idea.md, research_notes.md, competitor_analysis.md."
---

# Ideation & Research Agent

## Purpose

Transform a **raw idea** (text, note, description) into **structured research output** that feeds into the BRD generation pipeline.

This skill performs 5 research activities and produces 3 output documents.

---

## When to Use

- At the very start of a new project/product
- When exploring a new feature domain
- When the user has an idea but no formal requirements
- As the first step in the `/wf_ideate` workflow

## When NOT to Use

- When formal requirements (URD/BRD) already exist → use `/wf_pre_openspec`
- When only brainstorming direction is needed → use `/wf_brainstorm_openspec`

---

## Input

**Required**: An idea description (free-form text, any length)

**Optional**:
- Target domain (e.g., "Fintech", "Property Management")
- Target users (e.g., "Landlords and Tenants")
- Constraints (e.g., "Mobile-first", "Must work offline")

---

## Steps

### Step 1: Parse & Understand the Idea

1. Extract core concept from user input
2. Identify:
   - **Problem statement**: What pain point does this solve?
   - **Target users**: Who benefits?
   - **Domain**: What industry/category?
   - **Initial scope**: What's the minimum viable product?
3. Derive project name (kebab-case) from the idea

### Step 2: Web Search — Competitors & Market

Use `search_web` tool to research:

1. **Direct competitors**: "top {domain} apps 2026"
2. **Market trends**: "{domain} market trends pain points"
3. **User reviews**: "{competitor} user complaints reviews"
4. **Feature expectations**: "must-have features {domain} app"

**Capture for each competitor**:
- Name, URL
- Key features
- Pricing model
- User ratings/reviews summary
- Strengths and weaknesses

### Step 3: GitHub Scan — Open Source Landscape

Use `search_web` tool with domain `github.com` to find open-source alternatives.

**3a. Find TOP 10 repos by stars**:

Search queries:
- `site:github.com {keywords} stars:>100`
- `"{domain}" "{keywords}" awesome list github`

For each repo, capture:
| Field | Source |
|:---|:---|
| Repo name | Search results |
| Stars count | Search results / repo page |
| Language | Repo page |
| Last update | Repo page |
| Description | Repo page |
| License | Repo page |

Sort by stars descending → Top 10.

**3b. Deep-analyze TOP 3 best matching**:

From Top 10, select 3 repos with highest relevance to the idea. For each:

1. Read repo README via `read_url_content`
2. Analyze:
   - **Feature overlap**: Which features match our idea?
   - **Tech stack**: Languages, frameworks, databases
   - **Architecture patterns**: Monolith/microservice, design patterns
   - **Documentation quality**: README, API docs, contributing guide
   - **Community health**: Contributors, recent activity, issues
   - **Strengths**: What do they do well?
   - **Weaknesses**: What's missing or poorly done?
   - **Lessons**: What can we adopt or improve upon?

### Step 4: Market Analysis

Synthesize from Steps 2-3:
- Market size and growth trend (if data available)
- Common pain points across competitors
- Feature gaps — what no one does well
- Technology trends in the domain
- Pricing models overview

### Step 5: Feature Mapping

Create a structured feature inventory:

| Feature | Priority | Competitors | Notes |
|:---|:---|:---|:---|
| Feature A | 🔴 Must-have (MVP) | 3/5 competitors have it | Core functionality |
| Feature B | 🟡 Should-have | 1/5 competitors | Differentiator |
| Feature C | 🟢 Nice-to-have | 0/5 competitors | Future roadmap |

**Priority rules**:
- 🔴 **Must-have**: Without this, the product has no value
- 🟡 **Should-have**: Significantly improves user experience
- 🟢 **Nice-to-have**: Competitive advantage but not essential for MVP

---

## Output

Create project directory and generate 3 files:

```
project_docs/{project-name}/
├── brds/
│   ├── initial-idea.md
│   ├── competitor_analysis.md
│   └── prompt.md
└── research_notes.md
```

### Output 1: `initial-idea.md`

```markdown
---
type: initial_idea
project: {project-name}
date: YYYY-MM-DD
domain: {domain}
status: researched
---

# {Project Name} — Initial Idea

## Problem Statement
{What pain point does this solve?}

## Target Users
{Who benefits? User personas.}

## Core Concept
{What is the product/feature?}

## Constraints & Assumptions
- {constraint 1}
- {assumption 1}

## MVP Feature Set
{List of 🔴 Must-have features from Step 5}

## Differentiators
{What makes this different from existing solutions?}

## Initial Scope
{Bounded scope for first version}
```

### Output 2: `competitor_analysis.md`

```markdown
---
type: competitor_analysis
project: {project-name}
date: YYYY-MM-DD
---

# Competitor Analysis: {Project Name}

## Commercial Competitors
| # | Name | URL | Key Features | Pricing | Rating | Strengths | Weaknesses |
|---|------|-----|-------------|---------|--------|-----------|------------|
| 1 | ... | ... | ... | ... | ... | ... | ... |

## Top 10 Open-Source Projects (by ⭐ Stars)
| # | Repo | Stars | Language | Last Update | License | Description |
|---|------|-------|----------|-------------|---------|-------------|
| 1 | user/repo | 15.2k | Rust | 2026-06 | MIT | ... |
| ... |

## Top 3 Best Matching (Deep Analysis)

### 1. {repo-name} (⭐ {stars}) — Match Score: {X}%
- **Feature Overlap**: {list}
- **Tech Stack**: {stack}
- **Architecture**: {patterns}
- **Strengths**: {what they do well}
- **Weaknesses**: {what's missing}
- **Lessons**: {what we can adopt}

### 2. ...
### 3. ...

## Feature Gap Analysis
| Feature | Comp-1 | Comp-2 | Comp-3 | Repo-1 | Repo-2 | Repo-3 | Our Plan |
|---------|:------:|:------:|:------:|:------:|:------:|:------:|:--------:|
| ... | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | ✅ |

## Key Insights
- {insight 1}
- {insight 2}
```

### Output 3: `research_notes.md`

Free-form research notes including:
- Raw search results summaries
- Interesting links and references
- Market data points
- Technology observations
- Questions for further investigation

### Output 4: `prompt.md`

Agent prompt summarizing context for the next pipeline stage (BRD generation):

```markdown
---
type: agent_prompt
next_stage: wf_brd
---

# Context for BRD Generation

## Project: {name}
## Domain: {domain}
## Key inputs:
- initial-idea.md: Core concept and MVP scope
- competitor_analysis.md: Market landscape and gaps
- research_notes.md: Supporting research data

## Instructions for BRD Agent:
Generate 9 BRD documents (00-08) based on the above inputs.
Prioritize features from the MVP feature set.
Reference competitor analysis for validation.
```

---

## Transition

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
Ready: /wf_brd {project-name}
```

---

## Guardrails

- **DO NOT** invent competitors — only list verified products/repos
- **DO NOT** fabricate star counts or metrics — verify from source
- **DO NOT** skip GitHub analysis — it's mandatory for open-source landscape
- **DO NOT** generate BRD documents — that's the next pipeline stage
- **DO** use `search_web` and `read_url_content` tools for all research
- **DO** cite sources for all claims and data points
- **DO** clearly separate facts from assumptions

## Limitations
- Use this skill only when the task clearly matches the scope described above.
- Do not treat the output as a substitute for environment-specific validation, testing, or expert review.
- Stop and ask for clarification if required inputs, permissions, safety boundaries, or success criteria are missing.
