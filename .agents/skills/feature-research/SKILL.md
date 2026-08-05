---
name: feature-research
description: Use when researching a feature before implementation — discovering open source projects, analyzing internet solutions, evaluating products/tools, writing business analysis documents, and creating technical specifications. Triggers on "research feature", "find open source", "analyze how others implement", "write business analysis", "technical specification".
---

# Feature Research

## Overview

Research tính năng trước khi triển khai — tìm kiếm open source, khám phá internet, phân tích sản phẩm/công cụ đã triển khai, viết tài liệu nghiệp vụ và đặc tả kỹ thuật.

**Core principle:** Evidence-based research → mỗi finding phải có source URL. Không hallucinate.

## When to Use

- Cần research một tính năng mới trước khi implement
- Muốn tìm open source projects đã triển khai tính năng tương tự
- Cần so sánh các giải pháp/sản phẩm đã có trên thị trường
- Cần viết tài liệu phân tích nghiệp vụ (Business Analysis)
- Cần viết đặc tả kỹ thuật chi tiết (Technical Specification)

**When NOT to use:**
- Simple lookup (dùng `search_web` trực tiếp)
- Debug/fix bug (dùng standard tools)
- Đã có URD sẵn (dùng `/wf_pre_openspec`)

---

## Input Detection

```
Request Analysis
├── Starts with "http://" or "https://" → MODE_URL
│   → read_url_content(url)
│   → Extract: title, content, keywords
│   → Generate feature concept from article
│
├── Ends with ".md" or ".txt" or is file path → MODE_FILE
│   → view_file(path)
│   → Extract: feature description, requirements
│
├── Contains feature name/description → MODE_NAME
│   → Parse: feature name, scope, keywords
│
└── Free-form text → MODE_IDEA
    → Interactive brainstorm
    → Refine into feature concept
```

---

## Research Strategy (Perplexity-style)

### Iterative Search Loop

```
┌─────────────────────────────────────────┐
│           SEARCH ITERATION LOOP          │
├─────────────────────────────────────────┤
│                                          │
│  Iteration 1: BROAD SEARCH              │
│  ├── search_web("{feature} best practices")
│  ├── search_web("{feature} open source GitHub")
│  ├── search_web("{feature} architecture patterns")
│  ├── Collect: URLs, summaries, keywords  │
│  └── Extract: new leads, deeper queries  │
│                                          │
│  Iteration 2: DEEP DIVE                 │
│  ├── read_url_content(top_articles)      │
│  ├── read_url_content(top_repos README)  │
│  ├── Extract: approaches, trade-offs     │
│  ├── Identify: new keywords, leads       │
│  └── Cross-reference: validate findings  │
│                                          │
│  Iteration 3: TARGETED FOLLOW-UP        │
│  ├── search_web(new_keywords)            │
│  ├── read_url_content(follow-up URLs)    │
│  ├── Fill gaps in understanding          │
│  └── Verify conflicting information      │
│                                          │
│  Iteration N: STOP when:                │
│  ├── Diminishing returns (< 10% new info)│
│  ├── All research questions answered     │
│  └── Max 5 iterations reached            │
│                                          │
└─────────────────────────────────────────┘
```

### Search Query Templates

| Mục đích | Query Template |
|----------|---------------|
| General | `"how to implement {feature} best practices"` |
| Architecture | `"{feature} architecture design patterns"` |
| Open source | `"{feature} open source GitHub repository"` |
| Comparison | `"{feature} vs {alternative} comparison"` |
| Tutorial | `"{feature} tutorial step by step"` |
| Production | `"{feature} production experience lessons learned"` |
| Problems | `"{feature} challenges pitfalls common mistakes"` |

### Domain Prioritization

| Priority | Domain | Reason |
|----------|--------|--------|
| High | github.com | Source code, repos |
| High | medium.com, dev.to | Technical articles |
| High | stackoverflow.com | Community solutions |
| Medium | Official docs (spring.io, etc.) | Framework documentation |
| Medium | baeldung.com, dzone.com | Java/Spring specific |
| Low | Generic blogs | Supplementary info |

---

## Open Source Evaluation Framework

### Scoring Matrix

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core features | Has basics | Full-featured |
| **Applicability** | 15% | Different tech stack | Partial fit | Same stack, easy integrate |
| **Activity** | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Gap Analysis per Project

```markdown
### {{Project Name}} — Gap Analysis

**Overall Score**: X.X / 10

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Core Feature A | ✅ | | Well implemented | - |
| Core Feature B | | ❌ | - | Not supported |
| Scalability | ✅ | | Horizontal scale | Memory usage |
| Security | ⚠️ | | Basic auth | No E2EE |
| Documentation | ✅ | | Comprehensive | Outdated API docs |
| Integration | | ❌ | - | No Spring Boot starter |
| Test coverage | ✅ | | > 80% | No integration tests |

**Verdict**: {{Có thể dùng / Cần fork+customize / Không phù hợp}}
**Recommendation**: {{Dùng trực tiếp / Tham khảo pattern / Bỏ qua}}
```

---

## Product/Tool Evaluation Framework

### Comparison Table

```markdown
| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap | URL |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|-----|
| Product A | {{Approach}} | F1, F2, F3 | B1, B2 | + Fast, + Easy | - Cost, - Lock-in | G1 | url |
| Open Source B | {{Approach}} | F1, F2 | B1 | + Free, + Flexible | - No support | G2 | url |
| Custom Build | {{Approach}} | F1, F2, F3, F4 | B1, B2, B3 | + Full control | - Time, - Cost | - | - |
```

### Feature-by-Feature Comparison

```markdown
| Feature | Product A | OS Project B | Custom Build | Cần cho project? |
|---------|:---------:|:------------:|:------------:|:----------------:|
| Feature 1 | ✅ | ✅ | ✅ | ⭐ Must |
| Feature 2 | ✅ | ❌ | ✅ | ⭐ Must |
| Feature 3 | ❌ | ✅ | ✅ | Nice to have |
| Feature 4 | ✅ | ❌ | ✅ | Nice to have |
```

---

## Sub-Agent Coordination

### Dispatch Strategy

```
Phase 2 + Phase 3 → Order-independent dispatch:
(Can run in parallel if `dispatching-parallel-agents` skill is available, otherwise sequential)
├── sub-agent: web-researcher
│   Task: Search internet cho articles, blogs, documentation
│   Tools: search_web, read_url_content
│   Output: web_research.md
│
├── sub-agent: opensource-analyst
│   Task: Find & evaluate GitHub repos, open source projects
│   Tools: search_web, read_url_content
│   Output: opensource_findings.md
│
└── sub-agent: codebase-scanner
    Task: Scan current project cho related features/patterns
    Tools: grep_search, view_file, list_dir
    Output: current_system_analysis section in research_brief.md

Phase 7 → SEQUENTIAL after Phase 6:
└── sub-agent: review-validator
    Task: Validate all outputs, check sources, verify consistency
    Tools: All read tools
    Output: validation_report.md (using template)
```

### Sub-Agent Task Templates

**codebase-scanner:**
```
Scan the current project to find existing code related to "{{FEATURE_NAME}}".
This informs the research — we need to know what already exists before
searching externally.

Scan targets:
1. RELATED FEATURES — grep for keywords related to {{FEATURE_NAME}}:
   - Search service/handler/controller layers for related domain concepts
   - Search entity/model layers for related data structures
   - Search config files for related configuration
   - List matches with: file path, class/method name, brief description

2. EXISTING PATTERNS — identify code patterns the project already uses:
   - Architecture pattern (MVC, Clean Architecture, Hexagonal, etc.)
   - Data access pattern (Repository, DAO, JPA, raw SQL, etc.)
   - API style (REST, gRPC, MID-based handler, etc.)
   - Error handling pattern (exceptions, Result types, error codes, etc.)
   - Logging/observability patterns
   - Test patterns (what testing frameworks, where tests live)

3. TECH STACK CONSTRAINTS — identify from build files + configs:
   - Language version (e.g., Java 25, Kotlin 2.5)
   - Framework + version (e.g., Spring Boot 4.x, custom framework)
   - Database(s) (e.g., PostgreSQL, Oracle, Redis)
   - Build tool (Maven, Gradle, npm, etc.)
   - Key dependencies that constrain decisions

4. INTEGRATION POINTS — identify where {{FEATURE_NAME}} would connect:
   - Which existing modules/packages would interact with this feature?
   - Which APIs/endpoints are related?
   - Which database tables/schemas are related?
   - Any shared utilities or base classes to extend?

Tools to use:
- grep_search: keyword search across codebase
- list_dir: understand project structure
- view_file: read relevant files (build configs, key source files)

Output format: Populate section 4 "Current System Analysis" of the
research_brief.md with findings:
- 4.1 Related Features table (Feature | Module | Relevance | Notes)
- 4.2 Existing Code Patterns (architecture, data access, API style, etc.)
- 4.3 Tech Stack Constraints (language, framework, DB, build tool)
- 4.4 Integration Points (modules, APIs, tables that will interact)

IMPORTANT: Do NOT search the internet. Only scan local project files.
IMPORTANT: Do NOT modify any files. Read-only scan.
```

**web-researcher:**
```
Research the feature "{{FEATURE_NAME}}" on the internet.
Search for:
1. Best practices and architecture patterns
2. Tutorial articles and step-by-step guides
3. Production experiences and lessons learned
4. Common challenges and pitfalls

For each finding, you MUST include:
- Source URL
- Key insights
- Relevance score (1-10)
- Pros/cons of the approach described

Stop after 3-5 search iterations or when diminishing returns.
Output as markdown following the web_research template at
`skills/feature-research/templates/web_research_template.md`.
```

**opensource-analyst:**
```
Find open source projects implementing "{{FEATURE_NAME}}".
Search GitHub and other repositories for:
1. Complete implementations
2. Libraries/frameworks supporting this feature
3. Reference architectures

For each project found, evaluate using the scoring matrix:
- Feature completeness (20%)
- Applicability to our tech stack (15%)
- Activity level (15%)
- Documentation quality (15%)
- Code quality (15%)
- Community size (10%)
- Popularity (10%)

Perform gap analysis for top 3-5 projects.
Output as markdown following the opensource_findings template at
`skills/feature-research/templates/opensource_findings_template.md`.
```

**review-validator:**
```
Review all research outputs in openspec/research/{{FEATURE_NAME}}/ for quality.

Output: Generate `validation_report.md` using template at
`skills/feature-research/templates/validation_report_template.md`

Checks (5 categories):
1. SOURCE VERIFICATION: Every claim has a URL source? Test URLs for 404.
2. CONSISTENCY: Business Analysis ↔ Technical Spec aligned?
3. COMPLETENESS: All UCs have full flows? All entities defined?
4. FEASIBILITY: Technical spec feasible with current tech stack?
5. GAP COVERAGE: All identified gaps addressed?

For each check:
- PASS: No issues found
- WARN: Minor issues, can proceed
- FAIL: Must fix before finalizing

If this is a retry (iteration > 1):
- Only re-check previously FAILED categories
- Update existing validation_report.md with new iteration results
- Document what was fixed between iterations

IMPORTANT: If a URL returns 404 or timeout, mark as [ARCHIVED] — do NOT retry the same URL.
Output validation report with specific issues and locations.
```

---

## Anti-Hallucination Rules

| Rule | Enforcement |
|------|------------|
| Every finding MUST have source URL | review-validator checks |
| No invented repos/projects | Verify via search_web |
| No fabricated statistics | Cross-reference sources |
| Assumptions MUST be marked | `⚠️ Assumption: ...` prefix |
| Missing info = explicit gap | `❌ Gap: ...` — never guess |
| Conflicting sources = document both | Show both sides + note conflict |
| Do NOT skip review loop | Workflow validation gate — review-validator is mandatory |
| Do NOT hallucinate scores | Scoring matrix must be evidence-based |

---

## Output Directory Structure

```
openspec/research/<feature-name>/
├── research_brief.md          # Phase 1: Scope, keywords, current system
├── opensource_findings.md     # Phase 2: Open source evaluation + gap analysis
├── web_research.md            # Phase 3: Internet research findings
├── comparison_analysis.md     # Phase 4: Comparison matrix + overall gap
├── business_analysis.md       # Phase 5: Business analysis (use case decomposition)
├── technical_spec.md          # Phase 6: Technical specification (agent-ready)
├── validation_report.md       # Phase 7: Review loop results
└── handoff_summary.md         # Handoff: Bridge to downstream pipeline
```

---

## Handoff to OpenSpec Pipeline

After Phase 7 (review), generate `handoff_summary.md` in the research output directory:

```markdown
---
type: research_handoff
feature: {{FEATURE_NAME}}
date: {{DATE}}
recommendation: {{build / buy / adopt}}
research_dir: openspec/research/{{FEATURE_NAME}}/
status: complete
---

# Research Handoff: {{FEATURE_NAME}}

## Recommendation
{{1-2 sentence summary of build/buy/adopt decision with key reasoning}}

## Key Findings
- Open Source: {{top project + verdict}}
- Web Research: {{top insight + source count}}
- Gap Coverage: {{percentage or qualitative assessment}}

## Use Cases Identified
{{List from business_analysis.md — UC-001, UC-002, etc. with 1-line descriptions}}

## Ready for
- `/wf_brainstorm_openspec {{FEATURE_NAME}} --from-research` — deep thinking with research context
- `/wf_pre_openspec openspec/research/{{FEATURE_NAME}}/business_analysis.md` — formal URD analysis

## Research Artifacts
| File | Content |
|------|---------|
| [research_brief.md](./research_brief.md) | Scope, keywords, current system |
| [opensource_findings.md](./opensource_findings.md) | Open source evaluation |
| [web_research.md](./web_research.md) | Internet research |
| [comparison_analysis.md](./comparison_analysis.md) | Comparison + gap analysis |
| [business_analysis.md](./business_analysis.md) | Business analysis (use cases) |
| [technical_spec.md](./technical_spec.md) | Technical specification |
| [validation_report.md](./validation_report.md) | Quality review results |
```

---

## Templates

Templates nằm tại `skills/feature-research/templates/`:
- `research_brief_template.md` — Phase 1 output
- `opensource_findings_template.md` — Phase 2 output (scoring matrix + gap analysis)
- `web_research_template.md` — Phase 3 output (iterative search + product evaluation)
- `comparison_analysis_template.md` — Phase 4 output (comparison matrix + gap synthesis)
- `business_analysis_template.md` — Phase 5 output (use case decomposition + semantic)
- `technical_spec_template.md` — Phase 6 output (data flow + screen flow + processing steps)
- `validation_report_template.md` — Phase 7 output (review loop results)
- `handoff_summary_template.md` — Handoff output (bridge to downstream pipeline)

---

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Research quá rộng, không focus | Scope rõ trong research_brief → chỉ search related |
| Không verify source | Review-validator sub-agent bắt buộc check |
| Skip gap analysis | Gap analysis là core output — không skip |
| Business analysis quá generic | Dùng template với use case decomposition chi tiết |
| Technical spec không agent-ready | Section 9 "Agent Implementation Notes" bắt buộc |
| Không scan project hiện tại | Codebase-scanner sub-agent chạy ở Phase 1 |
