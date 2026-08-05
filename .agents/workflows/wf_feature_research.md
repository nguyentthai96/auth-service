---
description: Research tính năng trước khi triển khai — tìm open source, phân tích internet, đánh giá sản phẩm, viết tài liệu nghiệp vụ và đặc tả kỹ thuật
---

**[WORKFLOW]** Standalone pipeline — research & phân tích tính năng trước khi triển khai.

- **Primary skill**: `feature-research` — Core research methodology, Perplexity-style search, evaluation frameworks
- **Primary agent**: `feature-researcher` — Orchestrator + sub-agent dispatch
- **Support skills**: `brainstorming` (explore ideas), `architecture` (architecture decisions), `mermaid-diagram-enterprise` (diagrams)

**Pipeline position**: Standalone — **KHÔNG** nằm trong pipeline hiện tại.

```
/wf_feature_research  (standalone)
        │
        ▼ (output có thể dùng làm input)
/wf_pre_openspec  →  /wf_brainstorm_openspec  →  /wf_openspec  →  ...
```

> ℹ️ Workflow này tách biệt hoàn toàn với pipeline OpenSpec hiện tại.
> Output của nó có thể làm input cho `/wf_pre_openspec` hoặc `/wf_brainstorm_openspec` nhưng KHÔNG bắt buộc.

---

## Input

**Auto-detect logic**: See SKILL.md § Input Detection for detailed pseudocode.

| Input Pattern | Mode | Action |
|--------------|------|--------|
| Bắt đầu `http://` hoặc `https://` | URL | `read_url_content(url)` → extract content → derive feature |
| Kéo file `.md` / `.txt` vào chat | File | `view_file(path)` → extract description |
| Tên tính năng + mô tả | Name | Parse trực tiếp |
| Mô tả tự do | Idea | Interactive brainstorm |

**Examples:**
```
/wf_feature_research "Event Sourcing"
/wf_feature_research https://martinfowler.com/eaaDev/EventSourcing.html
/wf_feature_research  (kéo file event-sourcing-idea.md vào)
/wf_feature_research "Tôi muốn hệ thống ghi nhận mọi thay đổi trạng thái..."
```

---

## Methodology

Read and follow `skills/feature-research/SKILL.md` — it is the **single source of truth** for:
- Research Strategy (Perplexity-style iterative search loop)
- Open Source Evaluation Framework (scoring matrix, gap analysis)
- Product/Tool Evaluation Framework
- Sub-Agent Coordination & Task Templates
- Anti-Hallucination Rules

This workflow defines **orchestration**: phase sequence, validation gates, and output format.

---

## Steps

### Phase 1 — Understand & Scope

1. **Detect input mode** — See SKILL.md § Input Detection
2. **Scan current project** — Dispatch sub-agent `codebase-scanner` (See SKILL.md § Sub-Agent Task Templates):
   - Tìm code liên quan đến feature (grep keywords qua service/handler/entity/config layers)
   - Xác định architecture patterns đang dùng (MVC, Clean Architecture, handler-based, etc.)
   - Xác định tech stack constraints (language, framework, database, build tool)
   - Xác định integration points (modules, APIs, tables sẽ tương tác với feature mới)
   - Output → Populate section 4 "Current System Analysis" trong `research_brief.md`
3. **Generate research brief**:
   - Tạo directory: `openspec/research/<feature-name>/`
   - Tạo `research_brief.md` theo template `skills/feature-research/templates/research_brief_template.md`
   - Populate: keywords, search queries, current system findings (từ codebase-scanner output)

**VALIDATION Phase 1:**
- [ ] Directory created
- [ ] research_brief.md generated
- [ ] Keywords identified (≥ 5)
- [ ] Current system scanned — section 4.1 (Related Features), 4.2 (Existing Patterns), 4.3 (Tech Stack) populated

---

### Phase 2 — Open Source Discovery (order-independent with Phase 3)

Dispatch sub-agent `opensource-analyst`. See SKILL.md § Sub-Agent Task Templates.

Use scoring matrix and gap analysis framework from SKILL.md § Open Source Evaluation Framework.

Output: `openspec/research/<feature>/opensource_findings.md` — use template `skills/feature-research/templates/opensource_findings_template.md`

> Phase 2 and Phase 3 are order-independent — can run in parallel if `dispatching-parallel-agents` skill is available, otherwise sequential.

**VALIDATION Phase 2:**
- [ ] ≥ 3 projects evaluated
- [ ] Scoring matrix completed for each
- [ ] Gap analysis for top projects
- [ ] Each finding has source URL

---

### Phase 3 — Internet Research (order-independent with Phase 2)

Dispatch sub-agent `web-researcher`. See SKILL.md § Sub-Agent Task Templates.

Use iterative search strategy from SKILL.md § Research Strategy (Perplexity-style).

Output: `openspec/research/<feature>/web_research.md` — use template `skills/feature-research/templates/web_research_template.md`

**VALIDATION Phase 3:**
- [ ] ≥ 3 search iterations
- [ ] ≥ 5 unique sources
- [ ] Products/tools evaluated with pros/cons
- [ ] Each finding has source URL

---

### Phase 4 — Deep Analysis & Gap Analysis

Merge results from Phase 2 + Phase 3 + Phase 1 (current system).

Use comparison matrix and feature comparison patterns from SKILL.md § Product/Tool Evaluation Framework.

Generate `comparison_analysis.md` — use template `skills/feature-research/templates/comparison_analysis_template.md`

**VALIDATION Phase 4:**
- [ ] Comparison matrix complete
- [ ] Feature comparison table complete
- [ ] Gap analysis documented
- [ ] Recommendation provided with reasoning

---

### Phase 5 — Business Analysis Document

**Read template:** `skills/feature-research/templates/business_analysis_template.md`

Decompose feature into Use Cases with semantic descriptions, flows, business rules.

Generate `business_analysis.md`

**VALIDATION Phase 5:**
- [ ] ≥ 1 use case defined
- [ ] Each UC has basic flow + ≥ 1 exception flow
- [ ] Each UC has semantic description (tại sao cần)
- [ ] Traceability matrix complete
- [ ] Business rules documented

---

### Phase 6 — Technical Specification

**Read template:** `skills/feature-research/templates/technical_spec_template.md`

Create architecture diagrams, data schema, data flow, screen flow, API spec, agent implementation notes.

Generate `technical_spec.md`

**VALIDATION Phase 6:**
- [ ] Architecture diagram present
- [ ] ERD diagram present
- [ ] ≥ 1 sequence diagram
- [ ] Screen flow documented
- [ ] API endpoints listed
- [ ] Agent Implementation Notes complete

---

### Phase 7 — Review Loop (sub-agent: `review-validator`)

Dispatch sub-agent `review-validator`. See SKILL.md § Sub-Agent Task Templates.

**Max retries**: 2 per check category. **Total iterations**: Maximum 3 full review cycles.

Output: `openspec/research/<feature>/validation_report.md` — use template `skills/feature-research/templates/validation_report_template.md`

**Review loop rules:**

1. **First iteration**: Run all 5 checks (source verification, consistency, completeness, feasibility, gap coverage). Record results in `validation_report.md`.
2. **If any check FAIL**:
   - Source verification FAIL → re-search (Phase 3), max 2 retries
   - Consistency FAIL → fix documents (Phase 5-6), max 2 retries
   - Completeness FAIL → add missing items (Phase 5-6), max 2 retries
   - Feasibility FAIL → flag for user review (cannot auto-fix)
   - Gap coverage FAIL → re-analyze (Phase 4), max 2 retries
3. **After retry**: Re-run ONLY the failed checks. Update `validation_report.md` with iteration number.
4. **Degradation**: If a check still FAIL after max retries → downgrade to ⚠️ WARN with documented reason. Proceed.
5. **Permanent failures**: URLs returning 404 → mark as `[ARCHIVED]`, don't retry.

**Exit conditions:**
- All PASS → proceed to Output Summary
- All PASS/WARN → proceed to Output Summary (warnings documented)
- Any FAIL after max retries → downgrade to WARN, proceed with documented limitations

**Generate handoff:**
- Generate `openspec/research/<feature>/handoff_summary.md` — use template `skills/feature-research/templates/handoff_summary_template.md`
- See SKILL.md § Handoff to OpenSpec Pipeline for content structure

---

### Output Summary

```
═══════════════════════════════════════
FEATURE RESEARCH COMPLETE
═══════════════════════════════════════

Feature:     <feature-name>
Input:       <input mode + source>

Research:
  Open Source Projects:  <count> evaluated (<top repo>)
  Web Sources:           <count> sources
  Search Iterations:     <count>
  Products/Tools:        <count> evaluated

Analysis:
  Recommendation:        <build / buy / adopt>
  Gap Score:             <coverage %>
  Use Cases:             <count>
  API Endpoints:         <count>

Generated Files:
  ✅ openspec/research/<feature>/research_brief.md
  ✅ openspec/research/<feature>/opensource_findings.md
  ✅ openspec/research/<feature>/web_research.md
  ✅ openspec/research/<feature>/comparison_analysis.md
  ✅ openspec/research/<feature>/business_analysis.md
  ✅ openspec/research/<feature>/technical_spec.md
  ✅ openspec/research/<feature>/validation_report.md
  ✅ openspec/research/<feature>/handoff_summary.md

Review:
  Source Verification:   ✅ / ⚠️ / ❌
  Consistency:           ✅ / ⚠️ / ❌
  Completeness:          ✅ / ⚠️ / ❌
  Feasibility:           ✅ / ⚠️ / ❌
  Gap Coverage:          ✅ / ⚠️ / ❌

═══════════════════════════════════════
Optional next steps:
  → /wf_pre_openspec <feature> `business_analysis`
    (Uses business analysis as URD source)
  → /wf_brainstorm_openspec <feature>
    (Deep thinking with research context)
  → /wf_openspec <feature>
    (Generate implementation artifacts)
═══════════════════════════════════════
```

---

## Guardrails

- **Do NOT write implementation code** — this is research and documentation only
- **Do NOT modify existing pipeline** — this workflow is standalone
- **Do visualize** — use Mermaid diagrams liberally in specs
- **Do scan project first** — always check current system before researching externally
- **Do evaluate gaps** — gap analysis is core output, not optional
- **Do use templates** — follow `feature-research/templates/` for consistency
- See SKILL.md § Anti-Hallucination Rules for source verification requirements
