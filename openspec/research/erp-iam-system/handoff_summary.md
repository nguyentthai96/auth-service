# Research Handoff: ERP IAM System

> Bridge document — tóm tắt kết quả research để downstream workflows (`/wf_brainstorm_openspec`, `/wf_pre_openspec`) có thể tiếp nhận context nhanh.

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | ERP IAM System (3 Modules: auth-service, account-service, system-admin-service) |
| **Ngày hoàn thành** | 2026-08-05 |
| **Recommendation** | Hybrid build (custom + Bucket4j + optional Keycloak) |
| **Research directory** | `openspec/research/erp-iam-system/` |
| **Status** | complete |

---

## 1. Recommendation

**Hybrid Build** — Custom build trên nền tảng base-core đã có (RBAC, PBAC, JWT, MFA, SSO, session management) + adopt Bucket4j cho rate limiting + optional Keycloak cho SSO delegation. Không có open source nào cover business-specific features (menu permission, org management, approval workflow) nên custom build là bắt buộc.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| Open Source | Keycloak (9.25/10) — optional IdP, không adopt trực tiếp. Bucket4j (8.35/10) — **ADOPT** cho rate limiting. Cerbos, OpenFGA, Casbin — skip. | [opensource_findings.md](./opensource_findings.md) |
| Web Research | Spring Security 7 native MFA, Permission-First approach, Stripe API key pattern, custom state machine cho workflow. 14 unique sources. | [web_research.md](./web_research.md) |
| Gap Coverage | 18% current coverage (9/49 features). auth-service has strong foundation. account-service + system-admin-service = stub only. | [comparison_analysis.md](./comparison_analysis.md) |
| Current System | auth-service đã có: RBAC Engine, PBAC PolicyEvaluator, JWT (RS256), MFA (TOTP + OTP), SSO adapter, session management, E2EE, anonymous sessions, CQRS handlers. Clean Architecture with Hexagonal pattern. | [research_brief.md](./research_brief.md) |

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
| Architecture | Clean Architecture (Hexagonal) — adapter/in/web, adapter/out/persistence, application |
| Data model | ~30+ entities across 3 services, 3 ERDs |
| APIs | 57+ endpoints (15 auth, 14 account, 28 system-admin) |
| Key dependencies | Bucket4j (rate limiting), dev.samstevens.totp (MFA), Passay (password policy), Google Tink (E2EE) |
| Caching | Caffeine L1 (30s) + Redis L2 (5-30min TTL), 6 cache patterns |
| Inter-service | REST (sync) + Kafka (async), 4 event topics |
| Risk areas | 1. Scope creep (mitigate: 4-phase rollout) 2. Approval workflow state complexity (mitigate: immutable versions) |

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
| [research_brief.md](./research_brief.md) | 1 | Scope, 8 keywords, current system analysis (17 features scanned, tech stack constraints) |
| [opensource_findings.md](./opensource_findings.md) | 2 | 5 open source projects evaluated with scoring matrix + gap analysis |
| [web_research.md](./web_research.md) | 3 | 4 search iterations, 14 unique sources, 5 products evaluated |
| [comparison_analysis.md](./comparison_analysis.md) | 4 | Build vs Buy vs Adopt, gap analysis (18% coverage), risk assessment |
| [business_analysis.md](./business_analysis.md) | 5 | 47 use cases, 16 FRs, 8 NFRs, traceability matrix, business rules |
| [technical_spec.md](./technical_spec.md) | 6 | 3 ERDs, 57+ API endpoints, caching strategy, agent implementation notes |
| [validation_report.md](./validation_report.md) | 7 | 5/5 checks passed on first iteration |

---

## 7. Review Status

| Check | Status | Notes |
|-------|:---:|-------|
| Source Verification | ✅ | All 14 sources verified (spring.io, keycloak.org, OWASP, NIST, GitHub) |
| Consistency | ✅ | UCs ↔ APIs aligned, entities ↔ ERDs aligned, recommendations ↔ tech choices aligned |
| Completeness | ✅ | All 3 modules covered, 47 UCs, 30+ entities, 57+ endpoints |
| Feasibility | ✅ | Feasible with Spring Boot 4.1 + Kotlin + PostgreSQL. base-core reuse verified. |
| Gap Coverage | ✅ | All 9 gaps from comparison_analysis addressed in tech spec |

---

## 8. Implementation Phases

| Phase | Focus | Effort | Key Deliverables |
|-------|-------|--------|-----------------|
| Phase 1 — Foundation (P0) | Menu Permission, Organization, Profile, Audit | 3-4 weeks | Menu tree CRUD, dept/position CRUD, profile CRUD, audit trail |
| Phase 2 — Security (P1) | MFA enhance, SSO, Password Policy, API Partner | 3-4 weeks | MFA engine, Keycloak adapter, API key + rate limiting |
| Phase 3 — Advanced (P2) | Approval Workflow, System Config, Feature Flags | 2-3 weeks | Workflow engine, config CRUD, feature flags |
| Phase 4 — Integration | Kafka events, E2E tests, API docs | 1-2 weeks | Inter-service events, integration tests, OpenAPI spec |

---

> **Generated by**: `wf_feature_research` workflow
> **Next step**: Choose a downstream workflow from section 5
