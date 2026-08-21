# Research Handoff: ERP IAM System

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | ERP IAM System (3 Modules: auth-service, account-service, system-admin-service) |
| **Ngày hoàn thành** | 2025-07-15 |
| **Recommendation** | Hybrid enhancement (enhance existing + Bucket4j + optional Keycloak) |
| **Research directory** | `openspec/research/erp-iam-system/` |
| **Status** | complete |

---

## 1. Recommendation

**Hybrid Enhancement** — Enhance existing substantial codebase across 3 services (40+ Kotlin files already implemented). Adopt Bucket4j for distributed rate limiting. Optional Keycloak for SSO delegation via existing SsoAdapter. No open source covers business-specific features (menu permission, org management, approval workflow) but project already has implementations for all of them — enhancement only, not build from scratch.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | Keycloak (9.25/10) — optional IdP, don't replace existing. Bucket4j (8.35/10) — **ADOPT** for rate limiting. Cerbos, OpenFGA, Casbin — skip (existing PolicyEvaluator sufficient). | [opensource_findings.md](./opensource_findings.md) |
| Web Research | Spring Security 7 native MFA, Permission-First approach, Stripe API key pattern, DB-driven menu tree design, custom state machine for workflow. 15 unique sources. | [web_research.md](./web_research.md) |
| Gap Coverage | Most features already implemented. Key enhancements: Bucket4j rate limiting, button-level permission, MFA progressive flow refinement. | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | auth-service has: RBAC Engine, PBAC PolicyEvaluator, JWT RS256, MFA (TOTP + OTP + rate limiting), SSO adapter, session management/promotion, E2EE, anonymous sessions, CQRS handlers, domain events. account-service has: profile, device, session, preference, lifecycle modules. system-admin-service has: menu permission, API partner, organization, workflow, audit, config, feature flags, TreeEntity + TreeBuilder. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| UC-MFA-01..05 | MFA Management | Enable/disable 2FA, login with MFA, recovery codes | Must |
| UC-SSO-01..04 | SSO Integration | Login via Keycloak/Google/Microsoft, account linking | Must |
| UC-PWD-01..02 | Password Policy | Configurable rules per domain, force reset | Must |
| UC-PROF-01..05 | User Profile | CRUD profile, change email/phone, avatar upload | Must |
| UC-DEV-01..03 | Device Management | List devices, trust device, remote logout | Should |
| UC-SES-01..02 | Session Management | Active sessions, terminate session | Should |
| UC-LIFE-01..03 | Account Lifecycle | Deactivate, deletion request (GDPR), data export | Should |
| UC-MENU-01..05 | Menu Permission | Tree CRUD, role assign, user override, button-level | Must |
| UC-ORG-01..05 | Organization | Department tree, positions, user assignment | Must |
| UC-API-01..06 | API Partner | Register, key lifecycle, rate limit, usage dashboard | Must |
| UC-WF-01..06 | Approval Workflow | Define, submit, approve/reject, delegate, escalation | Should |
| UC-AUDIT-01..02 | Audit Trail | Search + export immutable logs | Must |

---

## 4. Technical Highlights

| Aspect | Decision/Finding |
|--------|-----------------|
| Architecture | Clean Architecture (Hexagonal) — adapter/in/web, adapter/out/persistence, application, domain/model. All 3 services follow same pattern. |
| Data model | ~30+ entities across 3 services, 3 ERDs. Existing entities verified via codebase scan. |
| APIs | 59 endpoints (15 auth, 14 account, 30 system-admin) |
| Key dependencies | Bucket4j (rate limiting — NEW), dev.samstevens.totp (MFA — existing), Passay (password — existing), Google Tink (E2EE — existing), Spring Kafka (events — existing) |
| Caching | AbstractTwoTierCache (Caffeine L1 + Redis L2) — existing infrastructure, 6 cache patterns |
| Inter-service | REST (sync) + Kafka (async), 4 event topics. ProfileKafkaListener already exists. |
| Existing infrastructure | TreeEntity + TreeBuilder, AbstractTwoTierCache, IdempotencyFilter, VersionedAuditableEntity, DatabaseMessageSource, AuditLogService |
| Risk areas | 1. Bucket4j integration effort (mitigate: Spring Boot starter available) 2. Button-level permission complexity (mitigate: simple permission_code pattern) |

---

## 5. Ready for

| Workflow | Command | Khi nào dùng |
|----------|---------|-------------|
| Brainstorm (deep thinking) | `/wf_brainstorm_openspec erp-iam-system --from-research` | Muốn explore thêm, có nhiều hướng tiếp cận |
| URD Analysis | `/wf_pre_openspec openspec/research/erp-iam-system/business_analysis.md` | Đã rõ requirements, muốn formalize |
| OpenSpec (direct) | `/wf_openspec erp-iam-system` | Đã rõ mọi thứ, muốn generate artifacts ngay |

---

## 6. Research Artifacts

| File | Phase | Content |
|------|-------|---------|
| [research_brief.md](./research_brief.md) | 1 | Scope, 8+ keywords, current system analysis (35+ existing features scanned across 3 services, tech stack constraints) |
| [opensource_findings.md](./opensource_findings.md) | 2 | 5 open source projects evaluated with scoring matrix + gap analysis |
| [web_research.md](./web_research.md) | 3 | 4 search iterations, 15 unique sources, 5 products evaluated |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Enhancement vs Replace vs Adopt, gap analysis, risk assessment |
| [business_analysis.md](./business_analysis.md) | 5 | 47 use cases, 16 FRs, 8 NFRs, traceability matrix, business rules |
| [technical_spec.md](./technical_spec.md) | 6 | 3 ERDs, 59 API endpoints, caching strategy, agent implementation notes |
| [validation_report.md](./validation_report.md) | 7 | 5/5 checks passed on first iteration |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ✅ | All 15 sources verified (spring.io, keycloak.org, OWASP, NIST, GitHub, BestHub, bucket4j.com) |
| Consistency | ✅ | UCs ↔ APIs aligned, entities ↔ ERDs aligned, recommendations ↔ tech choices aligned, all class references verified |
| Completeness | ✅ | All 3 modules covered, 47 UCs, 30+ entities, 59 endpoints, existing code references included |
| Feasibility | ✅ | Feasible with Spring Boot 4.1 + Kotlin + PostgreSQL. All existing code verified via codebase scan. Enhancement-only approach reduces risk. |
| Gap Coverage | ✅ | All enhancement gaps addressed in tech spec. Existing implementations referenced. |

---

## 8. Implementation Phases

| Phase | Focus | Effort | Key Deliverables |
|-------|-------|--------|-----------------|
| Phase 1 — Rate Limiting (P0) | Bucket4j integration, API key rate limiting | 1-2 weeks | Bucket4j + Redis ProxyManager, API key filter enhancement |
| Phase 2 — Permission Enhancement (P1) | Button-level permission, menu permission refinement | 1-2 weeks | permission_code pattern, role_menu_permissions enhancement |
| Phase 3 — Auth Enhancement (P2) | MFA progressive flow refinement, SSO provider config | 1-2 weeks | sso_providers entity, password_policies entity, recovery_codes |
| Phase 4 — Integration & Testing | Kafka events expansion, E2E tests, API docs | 1 week | Inter-service events, integration tests, OpenAPI spec |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
