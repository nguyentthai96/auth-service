# Phân tích so sánh: ERP IAM System

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Enterprise IAM System (3 Modules: auth-service, account-service, system-admin-service) |
| **Ngày phân tích** | 2025-07-15 |
| **Recommendation** | **Hybrid approach — Enhance existing custom code + Adopt Bucket4j + Optional Keycloak** |
| **Rationale** | Codebase đã có substantial implementation cho tất cả 3 services. Enhance existing code, adopt Bucket4j cho rate limiting, optional Keycloak cho SSO delegation. Không cần build from scratch. |
| **Confidence** | HIGH — Codebase scan confirmed 40+ Kotlin files across 3 services, gap analysis rõ ràng, tech stack constraints đã xác định |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Keycloak | Open Source IdP | Full identity provider — delegate auth | Enterprise-grade, proven, MFA built-in | External service, no menu/org/workflow, SPI complexity | ⚠️ Optional | 9.25 |
| 2 | Bucket4j | Open Source Library | Embeddable rate limiting — token bucket + Redis | Spring Boot native, lightweight, distributed | Application-level only, no admin dashboard | ✅ Adopt | 8.35 |
| 3 | Cerbos | Open Source Policy Engine | External policy-as-code engine — YAML policies | Powerful ABAC, playground | Separate Go service, overkill for current scale | ❌ Skip | 7.35 |
| 4 | Casbin | Open Source Library | Embeddable authorization — model DSL | Multi-model support, embeddable | Would replace working RbacEngine, not native Spring | ❌ Skip | 7.20 |
| 5 | Enhance Existing | In-house | Enhance existing implementations across 3 services | Full control, tight integration, no vendor lock-in, leverages existing code | Enhancement effort, maintenance burden | ✅ Primary | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Keycloak | Bucket4j | Cerbos | Casbin | Enhance Existing | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| Authentication (login/register) | ✅ | ❌ | ❌ | ❌ | ✅ (đã có) | ⭐ Must (đã có) |
| MFA/2FA | ✅ | ❌ | ❌ | ❌ | ✅ (đã có) | ⭐ Must (đã có) |
| OAuth2/SSO | ✅ | ❌ | ❌ | ❌ | ✅ (đã có) | ⭐ Must (đã có) |
| RBAC | ✅ | ❌ | ❌ | ✅ | ✅ (đã có) | ⭐ Must (đã có) |
| PBAC/ABAC | ⚠️ | ❌ | ✅ | ✅ | ✅ (đã có) | ⭐ Must (đã có) |
| Menu Permission (tree) | ❌ | ❌ | ❌ | ❌ | ✅ (đã có) | ⭐ Must (đã có) |
| Button-level Permission | ❌ | ❌ | ❌ | ❌ | ✅ (enhance) | ⭐ Must |
| Organization Hierarchy | ❌ | ❌ | ❌ | ❌ | ✅ (đã có) | ⭐ Must (đã có) |
| API Key Management | ❌ | ❌ | ❌ | ❌ | ✅ (đã có) | ⭐ Must (đã có) |
| Rate Limiting | ❌ | ✅ | ❌ | ❌ | ⚠️ (cần Bucket4j) | ⭐ Must |
| Approval Workflow | ❌ | ❌ | ❌ | ❌ | ✅ (đã có) | Should (đã có) |
| Audit Trail | ✅ | ❌ | ⚠️ | ❌ | ✅ (đã có) | ⭐ Must (đã có) |
| User Profile Management | ❌ | ❌ | ❌ | ❌ | ✅ (đã có) | ⭐ Must (đã có) |
| Device Management | ❌ | ❌ | ❌ | ❌ | ✅ (đã có) | Should (đã có) |
| System Configuration | ❌ | ❌ | ❌ | ❌ | ✅ (đã có) | Nice to have (đã có) |
| **Coverage** | **5/15** | **1/15** | **1/15** | **2/15** | **15/15** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 12 | 0% (Cerbos) - 42% (Keycloak) |
| Should | 2 | 0% across all external |
| Nice to have | 1 | 0% across all external |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Keycloak | Bucket4j | Enhance Existing | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| Authentication + MFA | UC-MFA-01..05 | ✅ | ❌ | ✅ (đã có) | Không — covered |
| OAuth2/SSO | UC-SSO-01..04 | ✅ | ❌ | ✅ (đã có) | Không — covered |
| Menu Permission Tree | UC-MENU-01..05 | ❌ | ❌ | ✅ (đã có, enhance) | Enhance button-level |
| Button-level Permission | UC-MENU-04 | ❌ | ❌ | ✅ (enhance) | Cần enhance |
| Organization Management | UC-ORG-01..06 | ❌ | ❌ | ✅ (đã có) | Không — covered |
| API Key + Rate Limiting | UC-API-01..07 | ❌ | ✅ (rate only) | ✅ + Bucket4j | Cần integrate Bucket4j |
| Approval Workflow | UC-WF-01..08 | ❌ | ❌ | ✅ (đã có) | Không — covered |
| Audit Trail | BR-AUDIT-01..04 | ✅ | ❌ | ✅ (đã có) | Không — covered |
| User Profile CRUD | UC-PROF-01..06 | ❌ | ❌ | ✅ (đã có) | Không — covered |
| Session Management | BR-SES-01..03 | ⚠️ | ❌ | ✅ (đã có) | Không — covered |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Authentication | Login/register, JWT RS256, MFA (TOTP + OTP), SSO adapter | Enhanced MFA progressive flow, Keycloak optional | MFA enhancement, Keycloak adapter | MEDIUM |
| Authorization | RBAC + PBAC (working, complete) | Same + button-level menu permission | Button-level permission layer | MEDIUM |
| User Management | UserEntity in auth-service | Same — already separate profile in account-service | Minor enhancements | LOW |
| Organization | Department tree + positions + user assignments (implemented) | Same — enhance with transfer, org chart | Transfer feature | LOW |
| API Partner | API key management, usage tracking (implemented) | Same + Bucket4j rate limiting integration | Rate limiting integration | MEDIUM |
| Workflow | WorkflowEngine + escalation scheduler (implemented) | Same — enhance with conditional routing | Minor enhancements | LOW |
| Audit | AOP audit aspect + audit logs + controller (implemented) | Same — enhance immutability constraints | DB constraint | LOW |
| Caching | AbstractTwoTierCache (Caffeine L1 + Redis L2) | Comprehensive permission/menu caching | Additional cache patterns | LOW |
| Inter-service | Kafka (runtime), ProfileKafkaListener | Expanded event topics | More Kafka topics | LOW |

### 4.3 Enhance Existing vs External Replacement

| Factor | Enhance Existing | Replace with Keycloak (Best External) | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 4-6 weeks (enhancement only) | 8-10 weeks (setup + custom gaps) | Enhance Existing |
| Maintenance burden | MEDIUM (existing codebase) | HIGH (Keycloak + custom + adapter) | Enhance Existing |
| Feature coverage | 100% (all features exist/enhanceable) | 42% (auth only, large gaps remain) | Enhance Existing |
| Integration effort | LOW (same tech stack, patterns known) | MEDIUM (external service, adapter) | Enhance Existing |
| Long-term flexibility | HIGH (full control, no vendor) | LOW (Keycloak upgrade cycles, SPI) | Enhance Existing |
| Risk | LOW (proven codebase, incremental changes) | MEDIUM (new dependency + custom gaps) | Enhance Existing |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Keycloak Only | Full Replace | Hybrid Enhancement (Recommended) |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 4 | 10 | 10 |
| Integration ease | 25% | 5 | 9 | 9 |
| Maintenance | 20% | 8 | 5 | 7 |
| Community/Support | 15% | 9 | 3 | 5 |
| Learning curve | 10% | 6 | 8 | 9 |
| **Tổng điểm (weighted)** | | **5.85** | **7.40** | **8.20** |

### Reasoning

**Recommended approach**: Hybrid Enhancement — Enhance existing codebase + Adopt Bucket4j + Optional Keycloak adapter

**Lý do**:
1. **Codebase already has substantial implementations** — 40+ Kotlin files across 3 services with Clean Architecture. Enhancement ≫ rewrite.
2. **All core modules exist** — menu permission (`MenuPermissionService.kt`), organization (`OrganizationService.kt`, `DepartmentEntity.kt`, `PositionEntity.kt`), workflow (`WorkflowEngine.kt`), audit (`AuditAspect.kt`), profile (`ProfileService.kt`), device (`DeviceService.kt`). Enhancement only.
3. **Bucket4j adoption** — proven rate limiting library, Spring Boot native, Redis-backed. Integrates with existing `ApiPartnerService.kt`.
4. **TreeEntity + TreeBuilder already exist** — no need to create base class. `system-admin-service/shared/persistence/TreeEntity.kt` confirmed.
5. **Keycloak as optional IdP** — `SsoAdapter.kt` already provides adapter pattern for SSO delegation.

**Trade-offs chấp nhận**:
- Custom maintenance effort — chấp nhận vì full control và tight integration với base-core
- No external policy engine — chấp nhận vì PolicyEvaluator hiện tại đủ cho current scale

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Scope creep (quá nhiều enhancements) | MEDIUM | MEDIUM | Prioritize: rate limiting + button-level first |
| Cross-service data consistency | MEDIUM | MEDIUM | Kafka events + eventual consistency (already have KafkaConfig) |
| Permission check performance | LOW | MEDIUM | AbstractTwoTierCache already provides Caffeine L1 + Redis L2 |
| Keycloak lock-in | LOW | LOW | SsoAdapter already provides adapter pattern |
| Menu permission complexity | LOW | MEDIUM | TreeEntity + TreeBuilder already handle tree operations |
| Approval workflow state management | LOW | MEDIUM | WorkflowEngine already implements state machine |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-weeks) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Keycloak Only + Custom gaps | 8-10 weeks | HIGH | MEDIUM |
| Full Rewrite | 12-16 weeks | HIGH | HIGH |
| Hybrid Enhancement (Recommended) | 4-6 weeks | LOW-MEDIUM | LOW |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis (35+ existing features scanned across 3 services) |
| 2 | [opensource_findings.md](./opensource_findings.md) | 5 open source projects evaluated + scoring matrix |
| 3 | [web_research.md](./web_research.md) | 4 search iterations, 15 unique sources, 5 products evaluated |

---

> **Next step**: Business Analysis (business_analysis.md)
