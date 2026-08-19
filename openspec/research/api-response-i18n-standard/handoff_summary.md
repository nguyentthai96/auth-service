# Research Handoff: API Response & i18n Standard

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | api-response-i18n-standard |
| **Ngày hoàn thành** | 2026-08-11 |
| **Recommendation** | Build from scratch (using Spring native infrastructure) |
| **Research directory** | `openspec/research/api-response-i18n-standard/` |
| **Status** | complete |

---

## 1. Recommendation

**Build from scratch using Spring native infrastructure.** Project already has 90%+ of the feature implemented (DatabaseMessageSource, I18nConfig, AuthControllerAdvice with MessageSource, message bundles). No external library adds value — all evaluated solutions (error-handling-starter, Zalando Problem) are subsets of the existing implementation. Remaining work is ~3 developer-days for success message i18n, frontend headers, and removing hardcoded error messages.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | Spring Framework native (9.65/10) — dùng trực tiếp. error-handling-starter (6.55/10) — tham khảo pattern only. Zalando Problem/Spring Web — bỏ qua (superseded by Spring Boot 3.x). | [opensource_findings.md](./opensource_findings.md) |
| Web Research | RFC 9457 validates dual format (ApiResponse + ProblemDetail). Server-side i18n is industry consensus for multi-client APIs. 9 unique sources across 3 iterations. | [web_research.md](./web_research.md) |
| Gap Coverage | 6 gaps identified, all addressed in tech spec. Biggest gap: success response i18n (controllers still hardcode English). All error i18n already done. | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | 90%+ already implemented: DatabaseMessageSource, I18nConfig, AuthControllerAdvice, auth-messages.properties (en+vi). Remaining: success message i18n, frontend Accept-Language, remove hardcoded error messages. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-001 | Server-Side Error Message Rendering | Server renders i18n error messages via MessageSource; client shows detail directly | Must |
| UC-002 | Client Metadata Headers | Frontend sends Accept-Language, X-App-Version, X-Client-Platform on every request | Should |
| UC-003 | Unified Success Response Format | All success responses use ApiResponse<T> with i18n message field | Must |
| UC-004 | Frontend UI Text i18n (Separate) | UI text (labels, buttons) via react-i18next; language switch syncs both layers | Should |
| UC-005 | Runtime Message Management | Admin manages i18n messages via DB (i18n_messages table) without redeployment | Nice |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Dual response format: Success → `ApiResponse<T>`, Error → `ProblemDetail` (RFC 9457). Server-side i18n via Spring MessageSource. |
| Data model | 1 entity: `I18nMessageEntity` (i18n_messages table) — code, locale, message, module, is_active. Already exists. |
| APIs | No new endpoints — response behavior changes on ALL existing endpoints (localized messages + Content-Language header) |
| Key dependencies | Spring MessageSource (native), Caffeine cache (existing), react-i18next (existing). Zero new dependencies. |
| Risk areas | 1. BaseControllerAdvice change affects all services (mitigated: nullable MessageSource). 2. Missing Khmer translations (mitigated: English fallback). |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec api-response-i18n-standard --from-research` | Muốn explore thêm, có nhiều hướng tiếp cận |
| URD Analysis | `/wf_pre_openspec openspec/research/api-response-i18n-standard/business_analysis.md` | Đã rõ requirements, muốn formalize |
| OpenSpec (direct) | `/wf_openspec api-response-i18n-standard` | Đã rõ mọi thứ, muốn generate artifacts ngay |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, keywords, current system analysis (90%+ already implemented) |
| [opensource_findings.md](./opensource_findings.md) | 2 | 4 projects evaluated — Spring native wins (9.65/10) |
| [web_research.md](./web_research.md) | 3 | 9 sources, 3 iterations — RFC 9457, Spring MessageSource, dual format pattern |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Feature matrix (11 features), decision matrix, 6 gaps all addressed |
| [business_analysis.md](./business_analysis.md) | 5 | 5 use cases, 16 business rules, 10 FRs, 7 NFRs |
| [technical_spec.md](./technical_spec.md) | 6 | Architecture, ERD, sequence diagrams, component changes, test cases |
| [validation_report.md](./validation_report.md) | 7 | All 5 checks PASS on first iteration |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ✅ | 9 sources with URLs — RFC 9457, Spring docs, Baeldung, StackOverflow |
| Consistency | ✅ | BA ↔ Tech Spec aligned — UCs map to sequence diagrams, BRs map to implementation |
| Completeness | ✅ | 5 UCs with flows, 4 OS projects scored, 11-feature comparison matrix |
| Feasibility | ✅ | Codebase scan confirms 90%+ already implemented — remaining is incremental |
| Gap Coverage | ✅ | All 6 gaps from comparison_analysis addressed in tech spec |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
