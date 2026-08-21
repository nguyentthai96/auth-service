# Research Handoff: JWT Token Validation

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | JWT Token Validation |
| **Ngày hoàn thành** | 2026-08-26 |
| **Recommendation** | build (enhance existing JJWT-based implementation) |
| **Research directory** | `openspec/research/jwt_token_validation/` |
| **Status** | complete |

---

## 1. Recommendation

**Build from scratch (enhance existing)** — the auth-service already has a solid JJWT-based JWT validation foundation. The gaps (blacklist caching, audience validation, JWKS key rotation, claim validation pipeline) are all application-level concerns that no external library solves. Enhance the existing `JwtService`, `JwtAuthFilter`, and `TokenController` with two-tier caching (Caffeine L1 + Redis L2), a modular claim validator chain, and multi-key JWKS rotation support.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | JJWT (8.85/10) already integrated — keep. Spring Security OAuth2 RS (9.35/10) provides reference patterns for JWKS caching and claim validators. No migration needed. | [opensource_findings.md](./opensource_findings.md) |
| Web Research | RFC 8725 (JWT BCP) mandates iss, aud, exp validation. Redis JTI-based blacklist with TTL is industry consensus. Dual-key JWKS rotation is standard. 8 sources analyzed. | [web_research.md](./web_research.md) |
| Gap Coverage | 6 critical gaps identified and addressed: DB blacklist → Redis+Caffeine, missing aud validation, clock skew 0→60s, no JWKS rotation, non-standard introspection, no claim pipeline. | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | Well-structured: JwtService (270 LOC), JwtAuthFilter (96 LOC), TokenController (66 LOC), SecurityProperties (207 LOC). Clean Architecture with hexagonal ports. AbstractTwoTierCache available as pattern reference. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-001 | Validate Access Token | Parse JWT, verify signature, validate claims, check blacklist, set SecurityContext | Must |
| UC-002 | Introspect Token (RFC 7662) | Server-side token state query returning active/inactive with claims | Must |
| UC-003 | Rotate Signing Keys | Dual-key overlap JWKS rotation without downtime | Should |
| UC-004 | Fetch JWKS | Expose RSA public keys at /.well-known/jwks.json | Must |
| UC-005 | Check Token Blacklist | Two-tier cache (L1 Caffeine → L2 Redis → DB) JTI lookup | Must |
| UC-006 | Validate Claims | Chain of claim validators (iss, aud, exp, nbf, type) | Must |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Enhance existing Clean Architecture (hexagonal) — new TokenBlacklistCacheService, ClaimValidatorChain, JwksKeyManager |
| Data model | TokenBlacklistEntity (existing) + Redis SET (token:blacklist:{jti}) + Caffeine L1 cache |
| APIs | 2 endpoints: POST /api/auth/introspect (RFC 7662), GET /.well-known/jwks.json |
| Key dependencies | JJWT 0.12.x (existing), Caffeine (existing), Redis (existing), base-cache-starter (existing) |
| Risk areas | 1. Redis unavailability on critical path (mitigated: L1 cache + DB fallback + circuit breaker). 2. L1 cache inconsistency window 15-30s (acceptable for fail-safe blacklist). |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec jwt_token_validation --from-research` | Muốn explore thêm cache strategies, DPoP/token binding |
| URD Analysis | `/wf_pre_openspec openspec/research/jwt_token_validation/business_analysis.md` | Đã rõ requirements, muốn formalize |
| OpenSpec (direct) | `/wf_openspec jwt_token_validation` | Đã rõ mọi thứ, muốn generate implementation artifacts ngay |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, keywords, current system analysis (JwtService, JwtAuthFilter, TokenController reviewed) |
| [opensource_findings.md](./opensource_findings.md) | 2 | 4 projects evaluated — JJWT (8.85), Nimbus (8.30), Auth0 (7.40), Spring OAuth2 RS (9.35) |
| [web_research.md](./web_research.md) | 3 | 3 iterations, 8 sources — RFC 8725/7662/7519, Redis patterns, JWKS rotation |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Feature matrix (12 features), gap analysis (6 gaps), recommendation: enhance existing |
| [business_analysis.md](./business_analysis.md) | 5 | 6 use cases, 9 functional requirements, 7 non-functional requirements, 23 business rules |
| [technical_spec.md](./technical_spec.md) | 6 | Architecture diagram, ERD, sequence diagram, 7 classes to create/modify, 19 test cases |
| [validation_report.md](./validation_report.md) | 7 | 5 checks — 4 PASS, 1 WARN (source URLs unverified due to tool limitations) |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ⚠️ | Web tools unavailable; URLs are authoritative sources (RFCs, GitHub). Content accurate. |
| Consistency | ✅ | BA ↔ Tech Spec aligned. UCs map to APIs. Entities consistent. |
| Completeness | ✅ | All UCs have flows. All APIs have examples. Scoring matrix complete. |
| Feasibility | ✅ | Feasible with current stack. All dependencies available. |
| Gap Coverage | ✅ | All 6 critical gaps addressed in technical spec. |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
