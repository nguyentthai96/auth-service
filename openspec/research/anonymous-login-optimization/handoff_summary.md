# Research Handoff: Anonymous Login Optimization

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | Anonymous Login Optimization |
| **Ngày hoàn thành** | 2025-01-20 |
| **Recommendation** | Build from scratch (with pattern references) |
| **Research directory** | `openspec/research/anonymous-login-optimization/` |
| **Status** | complete |

---

## 1. Recommendation

**Build from scratch** using Firebase Auth's API design (`signInAnonymously` → `linkWithCredential`) and Supabase GoTrue's JWT claims structure (`is_anonymous` claim) as design references. No existing open source library provides an embeddable, Spring Boot-native anonymous session promotion capability. The auth-service's existing JWT, Redis, and CQRS infrastructure makes custom build the lowest-risk, highest-integration-quality approach (estimated 5-8 developer-days).

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | 4 projects evaluated (Spring Security 7.90, Firebase 7.70, Keycloak 7.50, Supabase 6.70) — all assessed as "reference pattern only", none suitable for direct adoption or forking | [opensource_findings.md](./opensource_findings.md) |
| Web Research | 8 unique sources analyzed across 3 search iterations; Firebase's `signInAnonymously()` + `linkWithCredential()` is the gold standard; Supabase's `is_anonymous` JWT claim is directly applicable; Redis ephemeral model recommended over DB-backed anonymous users | [web_research.md](./web_research.md) |
| Gap Coverage | 100% gap coverage — all 8 identified gaps addressed in technical spec; custom build covers all 9 Must-have features (vs max 45% by any external solution) | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | auth-service has mature infrastructure (JwtService with RS256, CQRS CommandHandler, StringRedisTemplate, LoginRateLimitService, TokenBlacklistRepository) — anonymous auth is an incremental extension, not greenfield | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-001 | Create Anonymous Session | Generate anonymous JWT token + initialize Redis session with TTL | Must |
| UC-002 | Promote Anonymous Session | Upgrade anonymous to authenticated during login — transfer data, blacklist token | Must |
| UC-003 | Store Anonymous Session Data | CRUD operations on temporary session data in Redis (namespace-based) | Must |
| UC-004 | Renew Anonymous Token | Renew expiring anonymous token while keeping same session | Should |
| UC-005 | Rate Limit Check | IP-based rate limiting for anonymous token creation | Must |
| UC-006 | Transfer Session Data | Migrate Redis data from anonymous namespace to authenticated user namespace | Must |
| UC-007 | Invalidate Anonymous Token | Blacklist anonymous token JTI after promotion or renewal | Must |
| UC-008 | Cleanup Expired Sessions | Automated cleanup of expired anonymous sessions (Redis TTL handles this) | Nice |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Ephemeral Redis model — anonymous sessions stored in Redis only (no PostgreSQL anonymous user records); CQRS CommandHandler pattern for token creation/renewal; existing LoginHandler extended for promotion |
| Data model | Redis-only: `anon:session:{id}` (Hash), `anon:data:{id}:{ns}:{key}` (String); no new PostgreSQL tables; existing `token_blacklist` table reused |
| APIs | 6 endpoints: create session, renew token, store/read/delete data, login with promotion |
| Key dependencies | No new external dependencies — uses existing JJWT, Spring Data Redis (StringRedisTemplate), Spring Security, eventsourcing-utils CQRS |
| Risk areas | (1) Anonymous token abuse via bot farms — mitigated by IP rate limiting + CAPTCHA; (2) Redis memory exhaustion from mass anonymous sessions — mitigated by TTL + data size limits (64KB/session) |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec anonymous-login-optimization --from-research` | Muốn explore thêm — e.g., progressive authentication tiers, multi-level access |
| URD Analysis | `/wf_pre_openspec openspec/research/anonymous-login-optimization/business_analysis.md` | Đã rõ requirements, muốn formalize into URD |
| OpenSpec (direct) | `/wf_openspec anonymous-login-optimization` | Đã rõ mọi thứ, muốn generate implementation artifacts (tasks, design, migration) |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, 8 keywords, 10 search queries, current system analysis (10 integration points, tech stack constraints, existing code patterns) |
| [opensource_findings.md](./opensource_findings.md) | 2 | 4 projects evaluated with scoring matrix (7-criteria weighted), gap analysis per project, recommendation: build from scratch |
| [web_research.md](./web_research.md) | 3 | 3 search iterations, 8 unique sources, 4 implementation approaches compared (Firebase model, Ephemeral/Redis, Hybrid, Progressive Auth) |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Feature matrix (11 features × 5 solutions), gap analysis (8 requirements, 7 system aspects), decision matrix (5 criteria weighted), cost estimate |
| [business_analysis.md](./business_analysis.md) | 5 | 8 use cases (4 primary + 4 internal), 8 FRs, 6 NFRs, 16 business rules, traceability matrix, glossary |
| [technical_spec.md](./technical_spec.md) | 6 | Architecture diagram, Redis key design, 4 sequence diagrams, state machine, 6 API endpoints with examples, security matrix, 15 classes to create + 10 to modify, 18 test cases |
| [validation_report.md](./validation_report.md) | 7 | All 5 checks PASS on first iteration (source verification, consistency, completeness, feasibility, gap coverage) |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ✅ | All 8 sources have valid URLs |
| Consistency | ✅ | BA ↔ Tech Spec aligned (UCs → APIs, FRs → endpoints, BRs consistent) |
| Completeness | ✅ | All UCs have basic + exception flows; all APIs have request/response examples |
| Feasibility | ✅ | Feasible with current stack — no new dependencies, all integration points verified |
| Gap Coverage | ✅ | All 8 gaps from comparison analysis addressed in tech spec |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
