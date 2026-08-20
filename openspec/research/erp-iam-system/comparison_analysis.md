# Phân tích so sánh: ERP IAM System

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Enterprise IAM System (3 Modules: auth-service, account-service, system-admin-service) |
| **Ngày phân tích** | 2026-08-05 |
| **Recommendation** | **Hybrid approach — Custom build + Adopt libraries + Optional Keycloak** |
| **Rationale** | Không có open source nào cover business-specific features (menu permission, org management, approval workflow). Custom build trên nền tảng base-core đã có, adopt Bucket4j cho rate limiting, optional Keycloak cho SSO delegation. |
| **Confidence** | HIGH — Gap analysis rõ ràng, tech stack constraints đã xác định, foundation code đã có |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Keycloak | Open Source IdP | Full identity provider — delegate auth | Enterprise-grade, proven, MFA built-in | External service, no menu/org/workflow, SPI complexity | ⚠️ Optional | 9.25 |
| 2 | Bucket4j | Open Source Library | Embeddable rate limiting — token bucket + Redis | Spring Boot native, lightweight, distributed | Application-level only, no admin dashboard | ✅ Adopt | 8.35 |
| 3 | Cerbos | Open Source Policy Engine | External policy-as-code engine — YAML policies | Powerful ABAC, playground | Separate Go service, overkill for current scale | ❌ Skip | 7.35 |
| 4 | Casbin | Open Source Library | Embeddable authorization — model DSL | Multi-model support, embeddable | Would replace working RbacEngine, not native Spring | ❌ Skip | 7.20 |
| 5 | Custom Build | In-house | Full IAM on base-core foundation | Full control, tight integration, no vendor lock-in | Development effort, maintenance burden | ✅ Primary | N/A |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Keycloak | Bucket4j | Cerbos | Casbin | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| Authentication (login/register) | ✅ | ❌ | ❌ | ❌ | ✅ | ⭐ Must (đã có) |
| MFA/2FA | ✅ | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| OAuth2/SSO | ✅ | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| RBAC | ✅ | ❌ | ❌ | ✅ | ✅ | ⭐ Must (đã có) |
| PBAC/ABAC | ⚠️ | ❌ | ✅ | ✅ | ✅ | ⭐ Must (đã có) |
| Menu Permission (tree) | ❌ | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Button-level Permission | ❌ | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Organization Hierarchy | ❌ | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| API Key Management | ❌ | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Rate Limiting | ❌ | ✅ | ❌ | ❌ | ⚠️ | ⭐ Must |
| Approval Workflow | ❌ | ❌ | ❌ | ❌ | ✅ | Should |
| Audit Trail | ✅ | ❌ | ⚠️ | ❌ | ✅ | ⭐ Must |
| User Profile Management | ❌ | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Device Management | ❌ | ❌ | ❌ | ❌ | ✅ | Should |
| System Configuration | ❌ | ❌ | ❌ | ❌ | ✅ | Nice to have |
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

| Requirement | Source | Keycloak | Bucket4j | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| Authentication + MFA | UC-MFA-01..05 | ✅ | ❌ | ✅ | Không — covered by both |
| OAuth2/SSO | UC-SSO-01..04 | ✅ | ❌ | ✅ | Không — covered by both |
| Menu Permission Tree | UC-MENU-01..05 | ❌ | ❌ | ✅ | Có — chỉ Custom Build |
| Button-level Permission | UC-MENU-04 | ❌ | ❌ | ✅ | Có — chỉ Custom Build |
| Organization Management | UC-ORG-01..06 | ❌ | ❌ | ✅ | Có — chỉ Custom Build |
| API Key + Rate Limiting | UC-API-01..07 | ❌ | ✅ (rate only) | ✅ | Partial — Bucket4j + Custom |
| Approval Workflow | UC-WF-01..08 | ❌ | ❌ | ✅ | Có — chỉ Custom Build |
| Audit Trail | BR-AUDIT-01..04 | ✅ | ❌ | ✅ | Không — covered |
| User Profile CRUD | UC-PROF-01..06 | ❌ | ❌ | ✅ | Có — chỉ Custom Build |
| Session Management | BR-SES-01..03 | ⚠️ | ❌ | ✅ | Partial |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| Authentication | Basic login/register, JWT | MFA, SSO, CAPTCHA, password policy | MFA + SSO + policy engine | HIGH |
| Authorization | RBAC + PBAC (working) | Same + menu/button permission | Menu permission layer | HIGH |
| User Management | UserEntity in auth-service | Separate profile in account-service | Profile service separation | MEDIUM |
| Organization | None | Department/Position/Hierarchy | Full new module | HIGH |
| API Partner | None | API key, rate limiting, quota | Full new module | MEDIUM |
| Workflow | None | Dynamic multi-step approval | Full new engine | HIGH |
| Audit | Basic AOP logging | Immutable audit trail, export | Enhanced audit service | MEDIUM |
| Caching | Caffeine L1 + Redis L2 (basic) | Comprehensive permission/menu caching | 10+ cache patterns | MEDIUM |
| Inter-service | Kafka (compileOnly) | REST sync + Kafka async events | Event integration | MEDIUM |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse Keycloak (Best External) | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 10-13 weeks | 2 weeks setup + 8-10 weeks custom | Draw |
| Maintenance burden | HIGH (all custom code) | MEDIUM (Keycloak managed + custom) | Keycloak |
| Feature coverage | 100% (all features) | 46% (auth only, gaps remain) | Custom Build |
| Integration effort | LOW (same tech stack, base-core) | MEDIUM (external service, adapter) | Custom Build |
| Long-term flexibility | HIGH (full control) | LOW (Keycloak upgrade cycles, SPI) | Custom Build |
| Risk | MEDIUM (development effort) | MEDIUM (proven + custom gaps) | Draw |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Keycloak Only | Full Custom | Hybrid (Recommended) |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 4 | 10 | 10 |
| Integration ease | 25% | 5 | 9 | 8 |
| Maintenance | 20% | 8 | 5 | 6 |
| Community/Support | 15% | 9 | 3 | 6 |
| Learning curve | 10% | 6 | 8 | 7 |
| **Tổng điểm (weighted)** | | **5.85** | **7.40** | **7.65** |

### Reasoning

**Recommended approach**: Hybrid — Custom build + Adopt Bucket4j + Optional Keycloak adapter

**Lý do**:
1. **Business-specific features (menu, org, workflow) = 60%+ effort** — không có open source nào cover. Custom build là bắt buộc.
2. **Foundation đã có** — auth-service đã có RbacEngine, PolicyEvaluator, JWT, MFA, SSO adapter, session management. Mở rộng, không build from scratch.
3. **Bucket4j adoption** — proven rate limiting library, Spring Boot native, Redis-backed. Không reinvent token bucket algorithm.
4. **Keycloak as optional IdP** — adapter pattern cho phép switch on/off. Enable Keycloak khi cần enterprise SAML/OIDC federation.

**Trade-offs chấp nhận**:
- Custom maintenance effort — chấp nhận vì full control và tight integration với base-core
- No external policy engine — chấp nhận vì PolicyEvaluator hiện tại đủ cho current scale

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| Scope creep (quá nhiều features) | HIGH | HIGH | Phased rollout (4 phases), strict prioritization |
| Cross-service data consistency | MEDIUM | MEDIUM | Kafka events + eventual consistency |
| Permission check performance | MEDIUM | MEDIUM | Redis caching (5 min TTL), Caffeine L1 |
| Keycloak lock-in | LOW | LOW | Adapter pattern, Keycloak is optional |
| Menu permission complexity | MEDIUM | MEDIUM | Start simple (role-based), add user overrides later |
| Approval workflow state management | HIGH | HIGH | Immutable workflow versions, clear state machine |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-weeks) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Keycloak Only + Custom gaps | 10-12 weeks | HIGH | MEDIUM |
| Full Custom Build | 10-13 weeks | MEDIUM | HIGH |
| Hybrid (Recommended) | 10-13 weeks | MEDIUM | MEDIUM |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis (17 existing features scanned) |
| 2 | [opensource_findings.md](./opensource_findings.md) | 5 open source projects evaluated + scoring matrix |
| 3 | [web_research.md](./web_research.md) | 4 search iterations, 14 unique sources, 5 products evaluated |

---

> **Next step**: Business Analysis (business_analysis.md)
