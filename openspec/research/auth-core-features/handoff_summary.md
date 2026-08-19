# Research Handoff: Auth Core Features

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | Auth Core Features (FR-001 → FR-004) |
| **Ngày hoàn thành** | 2026-08-19 |
| **Recommendation** | build (using open source libraries for primitives) |
| **Research directory** | `openspec/research/auth-core-features/` |
| **Status** | complete |

---

## 1. Recommendation

**BUILD from scratch** sử dụng battle-tested open source libraries (dev.samstevens.totp, Passay, Spring OAuth2, JJWT). Auth là core business logic — custom code chỉ cần cho orchestration layer (MfaService, SsoAdapter, PasswordPolicyService). ~90% code đã implemented, V2 database migration đã applied.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | Top libraries: Spring OAuth2 (10.0/10), JJWT (9.7/10), Passay (8.65/10), dev.samstevens.totp (8.1/10) — all adopted, all integrated | [opensource_findings.md](./opensource_findings.md) |
| Web Research | Two-phase MFA login pattern (Spring Boot 3.x), RS256 dual-key migration, Passay factory pattern — 9 unique sources across 4 iterations | [web_research.md](./web_research.md) |
| Gap Coverage | 10/10 gaps addressed: MFA done, SSO done, RS256 done, introspection done, password policy done. 1 partial: `revokeAllSessions()` TODO | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | Hexagonal architecture, Snowflake IDs, CQRS pattern, Redis integration, 9 Flyway migrations. All auth-core entities/services already exist. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-001 | Đăng nhập với MFA | Two-phase login: credentials → mfaToken → OTP/TOTP verify → full JWT | Must |
| UC-002 | Đăng nhập qua SSO/OAuth2 | OAuth2 authorization code flow + JIT provisioning (Google, Microsoft, Keycloak) | Must |
| UC-003 | Token Introspection & Session | RFC 7662 introspection, JWKS endpoint, force logout | Must |
| UC-004 | Thay đổi Password | Domain-scoped Passay validation + BCrypt history check + expiry enforcement | Must |
| UC-005 | Thiết lập TOTP Authenticator | QR code URI generation, AES-256-GCM secret encryption, confirm flow | Must |
| UC-006 | Cấu hình Password Policy | Admin endpoint to CRUD per-domain password policy + cache invalidation | Should |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Hexagonal (port/adapter) — `adapter/in/web`, `adapter/out/persistence`, `application` |
| Data model | 4 entities: UserEntity (altered), UserIdentityEntity (new), PasswordPolicyEntity (new), PasswordHistoryEntity (new) |
| APIs | 18 endpoints identified (5 MFA + 4 SSO + 3 Token + 6 Password/Admin) |
| Key dependencies | `dev.samstevens.totp:1.7.1`, `org.passay:1.6.4`, `spring-oauth2-client`, `spring-oauth2-resource-server`, `spring-data-redis` |
| Risk areas | 1) `dev.samstevens.totp` GitHub 404 (Maven Central still works), 2) `revokeAllSessions()` partially implemented |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec auth-core-features --from-research` | Muốn explore thêm, có nhiều hướng tiếp cận |
| URD Analysis | `/wf_pre_openspec openspec/research/auth-core-features/business_analysis.md` | Đã rõ requirements, muốn formalize |
| OpenSpec (direct) | `/wf_openspec auth-core-features` | Đã rõ mọi thứ, muốn generate artifacts ngay |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, keywords, current system analysis (14 related features, tech stack, integration points) |
| [opensource_findings.md](./opensource_findings.md) | 2 | Open source evaluation: 5 projects scored (8 found, 5 evaluated) + gap analysis per project |
| [web_research.md](./web_research.md) | 3 | Internet research: 4 iterations, 9 sources, 6 patterns/approaches identified |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Comparison matrix (4 solutions), feature matrix (16 features), decision matrix, cost estimate |
| [business_analysis.md](./business_analysis.md) | 5 | Business analysis: 6 use cases, 18 business rules, traceability matrix, NFRs |
| [technical_spec.md](./technical_spec.md) | 6 | Technical specification: ERD, 3 sequence diagrams, 18 API endpoints, 18 classes, 18 test cases |
| [validation_report.md](./validation_report.md) | 7 | Quality review: all 5 checks PASS on first iteration |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ✅ | All sources verified. `dev.samstevens.totp` GitHub 404 marked [ARCHIVED], Maven Central OK |
| Consistency | ✅ | BA ↔ Tech Spec fully aligned — UCs → APIs → Entities → BRs consistent |
| Completeness | ✅ | All UCs have flows, all entities have schemas, all APIs have examples |
| Feasibility | ✅ | Feasible with current stack — ~90% code already exists and working |
| Gap Coverage | ✅ | 10/10 gaps addressed, 1 partial (revokeAllSessions TODO in code) |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
