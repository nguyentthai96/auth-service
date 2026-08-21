# Research Handoff: JWT Token Validation

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | JWT Token Validation |
| **Ngày hoàn thành** | 2025-07-11 |
| **Recommendation** | Build (augment existing jjwt-based implementation with structured validation pipeline) |
| **Research directory** | `openspec/research/jwt_token_validation/` |
| **Status** | complete |

---

## 1. Recommendation

**Build from scratch** — augment the existing jjwt-based implementation with a structured `TokenValidationPipeline` that centralizes all validation logic (signature → algorithm whitelist → claims → blacklist → token type dispatch). No library migration needed; jjwt is already deeply integrated and covers 80% of needs. Missing features (JWKS key caching, two-level blacklist cache, type-specific validators, validation audit events) are application-level concerns best addressed by custom Kotlin code following Clean Architecture patterns.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | jjwt (8.85/10) — continue using, already integrated. Spring Security OAuth2 RS (9.30/10) — reference for JWKS patterns only. No migration recommended. | [opensource_findings.md](./opensource_findings.md) |
| Web Research | OWASP JWT Cheat Sheet is the key reference — algorithm whitelist is #1 security requirement. RFC 7519/7662/7517 provide canonical validation spec. 12 unique sources analyzed across 3 iterations. | [web_research.md](./web_research.md) |
| Gap Coverage | 6 gaps identified between current system and target: (1) No centralized pipeline, (2) Algorithm confusion risk, (3) Inconsistent blacklist checks, (4) No key rotation, (5) Code duplication in type checks, (6) No validation audit trail. All addressed in tech spec. | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | Already has JwtService (RS256+HMAC), JwtAuthFilter, ServiceAuthFilter, TokenController, TokenBlacklistEntity, event store. Clean Architecture with CQRS/ES. Kotlin + Spring Boot 3.x + jjwt 0.12.x + Caffeine + Redis. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-001 | Validate Access Token | Verify user JWT on every API request — signature, claims, blacklist, type | Must |
| UC-002 | Validate Anonymous Token | Verify anonymous session token (type=anonymous) + Redis session check | Must |
| UC-003 | Validate Service Token | Verify inter-service JWT (HMAC) for /api/internal/ endpoints | Must |
| UC-004 | Introspect Token | RFC 7662-compliant endpoint returning token status and metadata | Should |
| UC-005 | Revoke Token (Blacklist) | Add JTI to blacklist with reason tracking and cache invalidation | Must |
| UC-006 | Check Token Blacklist | Two-level cache lookup (Caffeine L1 → Redis L2 → PostgreSQL) | Must |
| UC-007 | Review Validation Audit | Admin reviews validation failure events for security monitoring | Nice |
| UC-008 | Rotate JWKS Key | Zero-downtime key rotation via multi-key JWKS endpoint | Nice |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Clean Architecture (Hexagonal) + CQRS + Event Sourcing — validation pipeline as application service |
| Data model | No new tables — uses existing `token_blacklist`, `event_store`. 2 new event types: `iam.token.validation_failed`, `iam.token.validation_suspicious` |
| APIs | 3 endpoints (introspect, JWKS, revoke-all) — all existing, enhanced with pipeline |
| Key dependencies | jjwt 0.12.x (keep), Caffeine (L1 cache), Redis (L2 cache), base-cache-starter (TwoLevelCacheManager) |
| Risk areas | (1) Algorithm confusion attack if whitelist not enforced; (2) Stale L1 cache window for blacklist (30s default, configurable) |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec jwt_token_validation --from-research` | Muốn explore validation pipeline design alternatives |
| URD Analysis | `/wf_pre_openspec openspec/research/jwt_token_validation/business_analysis.md` | Đã rõ requirements, muốn formalize thành URD |
| OpenSpec (direct) | `/wf_openspec jwt_token_validation` | Đã rõ mọi thứ, muốn generate implementation artifacts (design.md, tasks.md, code) |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, keywords, current system analysis (11 related features, 10 integration points) |
| [opensource_findings.md](./opensource_findings.md) | 2 | 5 projects evaluated — jjwt (8.85), Spring Security RS (9.30), nimbus (8.30), Keycloak (7.80), Auth0 (7.10) |
| [web_research.md](./web_research.md) | 3 | 12 unique sources, 3 iterations, 7 architectural patterns identified |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Decision matrix: Custom Build (7.95) > jjwt alone (7.60) > Spring Security RS (7.00) |
| [business_analysis.md](./business_analysis.md) | 5 | 8 use cases, 10 functional requirements, 7 NFRs, 20 business rules |
| [technical_spec.md](./technical_spec.md) | 6 | 9 classes to create, 3 to modify, 17 test cases, full sequence diagrams |
| [validation_report.md](./validation_report.md) | 7 | Overall PASS (2 minor WARN on generic URLs) |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ⚠️ | 2 generic URL references (accepted as pattern references, not specific claims) |
| Consistency | ✅ | BA ↔ Tech Spec fully aligned (UCs → classes, entities → ERD) |
| Completeness | ✅ | All UCs have flows, all APIs have examples, all OS projects scored |
| Feasibility | ✅ | Feasible with current stack — Kotlin/Spring Boot/jjwt/Caffeine/Redis |
| Gap Coverage | ✅ | All 6 identified gaps addressed in tech spec |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
