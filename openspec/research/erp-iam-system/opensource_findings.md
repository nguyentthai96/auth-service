# Kết quả tìm kiếm Open Source: ERP IAM System

> Đánh giá các dự án open source đã triển khai tính năng tương tự — scoring matrix + gap analysis.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Enterprise IAM System (Authentication, Authorization, Menu Permission, Org Management, API Partner, Approval Workflow) |
| **Ngày tìm kiếm** | 2025-07-15 |
| **Số dự án tìm thấy** | 8 |
| **Số dự án đánh giá chi tiết** | 5 |
| **Tech stack mục tiêu** | Kotlin + Spring Boot 4.x + PostgreSQL + Redis |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"enterprise IAM open source identity management RBAC ABAC"` | 12 kết quả | Keycloak, Cerbos, OpenFGA nổi bật |
| 2 | `"open source authorization engine Spring Boot Kotlin RBAC"` | 8 kết quả | Casbin, Spring Security |
| 3 | `"open source dynamic menu permission system backend"` | 3 kết quả | Không có solution phù hợp; found articles on dynamic role-based permission design |
| 4 | `"open source rate limiting API key management Java Spring"` | 6 kết quả | Bucket4j nổi bật |
| 5 | `"open source approval workflow engine lightweight Java"` | 5 kết quả | Camunda, Temporal — quá heavy; project đã có custom WorkflowEngine |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | Keycloak | https://github.com/keycloak/keycloak | 25k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 2 | Cerbos | https://github.com/cerbos/cerbos | 3k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 3 | OpenFGA | https://github.com/openfga/openfga | 3k+ | Active (weekly) | Apache 2.0 | ✅ Có |
| 4 | Casbin | https://github.com/casbin/casbin | 18k+ | Active (monthly) | Apache 2.0 | ✅ Có |
| 5 | Bucket4j | https://github.com/bucket4j/bucket4j | 2.5k+ | Active (monthly) | Apache 2.0 | ✅ Có |
| 6 | Camunda | https://github.com/camunda/camunda-bpm-platform | 4k+ | Active | Mixed | ❌ Không (quá heavy cho use case — project đã có custom WorkflowEngine) |
| 7 | Temporal | https://github.com/temporalio/temporal | 12k+ | Active | MIT | ❌ Không (quá heavy cho use case) |
| 8 | Permit.io | https://github.com/permitio/opal | 4k+ | Active | Apache 2.0 | ❌ Không (SaaS-oriented) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Missing core features | Has basics | Full-featured |
| **Applicability** (phù hợp tech stack) | 15% | Different tech stack | Partial fit | Same stack, easy integrate |
| **Activity** (mức độ active) | 15% | No commits 6+ months | Monthly commits | Weekly commits |
| **Documentation** | 15% | No docs | README only | Full docs + examples |
| **Code quality** | 15% | No tests, messy | Some tests | Well-tested, clean |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Few users | Growing | Widely adopted |

### Kết quả đánh giá

#### Keycloak

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 10 | 20% | 2.00 | Full IdP: OIDC, SAML, MFA, UMA 2.0, social login |
| Applicability | 6 | 15% | 0.90 | External service, cần adapter, không embed được |
| Activity | 10 | 15% | 1.50 | Red Hat maintained, weekly commits |
| Documentation | 10 | 15% | 1.50 | Excellent official docs + community guides |
| Code quality | 9 | 15% | 1.35 | Extensive test suite, enterprise-grade |
| Community | 10 | 10% | 1.00 | 25k+ stars, massive community |
| Popularity | 10 | 10% | 1.00 | Industry standard for Java IAM |
| **Tổng điểm** | | | **9.25/10** | |

#### Cerbos

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | PBAC/ABAC policy-as-code, YAML policies |
| Applicability | 5 | 15% | 0.75 | Separate Go service, Java SDK available |
| Activity | 8 | 15% | 1.20 | CNCF sandbox, active development |
| Documentation | 8 | 15% | 1.20 | Good docs, playground available |
| Code quality | 8 | 15% | 1.20 | Well-tested Go codebase |
| Community | 7 | 10% | 0.70 | 3k+ stars, growing |
| Popularity | 7 | 10% | 0.70 | Growing adoption in cloud-native |
| **Tổng điểm** | | | **7.35/10** | |

#### OpenFGA

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | ReBAC + RBAC, Google Zanzibar model |
| Applicability | 4 | 15% | 0.60 | Separate infrastructure, complex setup |
| Activity | 7 | 15% | 1.05 | CNCF sandbox, growing fast |
| Documentation | 8 | 15% | 1.20 | Good docs, playground + visualizer |
| Code quality | 7 | 15% | 1.05 | Decent test coverage |
| Community | 7 | 10% | 0.70 | 3k+ stars |
| Popularity | 6 | 10% | 0.60 | Gaining traction |
| **Tổng điểm** | | | **6.80/10** | |

#### Casbin

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | RBAC, ABAC, ACL — embeddable library |
| Applicability | 6 | 15% | 0.90 | Java adapter available, but not native Spring |
| Activity | 7 | 15% | 1.05 | 5+ years, multi-language |
| Documentation | 6 | 15% | 0.90 | Adequate but scattered |
| Code quality | 7 | 15% | 1.05 | Reasonable test coverage |
| Community | 9 | 10% | 0.90 | 18k+ stars (all languages) |
| Popularity | 8 | 10% | 0.80 | Widely adopted |
| **Tổng điểm** | | | **7.20/10** | |

#### Bucket4j

| Tiêu chí | Điểm (1-10) | Trọng số | Điểm × Trọng số | Ghi chú |
|----------|------------|----------|----------------|---------|
| Feature completeness | 8 | 20% | 1.60 | Token bucket algorithm, Redis + Hazelcast support |
| Applicability | 10 | 15% | 1.50 | Spring Boot starter available, native Java |
| Activity | 8 | 15% | 1.20 | Well-maintained, regular releases |
| Documentation | 8 | 15% | 1.20 | Clear docs with examples |
| Code quality | 9 | 15% | 1.35 | Well-tested, clean codebase |
| Community | 7 | 10% | 0.70 | 2.5k+ stars |
| Popularity | 8 | 10% | 0.80 | Standard choice for JVM rate limiting |
| **Tổng điểm** | | | **8.35/10** | |

### Bảng tóm tắt điểm

| # | Dự án | Feature | Applicability | Activity | Docs | Code | Community | Popularity | **Tổng** |
|---|-------|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | Keycloak | 10 | 6 | 10 | 10 | 9 | 10 | 10 | **9.25** |
| 2 | Bucket4j | 8 | 10 | 8 | 8 | 9 | 7 | 8 | **8.35** |
| 3 | Cerbos | 8 | 5 | 8 | 8 | 8 | 7 | 7 | **7.35** |
| 4 | Casbin | 8 | 6 | 7 | 6 | 7 | 9 | 8 | **7.20** |
| 5 | OpenFGA | 8 | 4 | 7 | 8 | 7 | 7 | 6 | **6.80** |

---

## 4. Gap Analysis chi tiết

### Keycloak — Gap Analysis

**Overall Score**: 9.25 / 10
**URL**: https://github.com/keycloak/keycloak

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Authentication (OIDC, SAML, MFA) | ✅ | | Full IdP, enterprise-grade | - |
| User Management | ✅ | | Built-in admin console | - |
| RBAC | ✅ | | Realm roles + client roles | - |
| PBAC/ABAC | ⚠️ | | UMA 2.0 | Limited condition flexibility vs custom PolicyEvaluator |
| Menu Permission System | | ❌ | - | Không có concept menu/button permission |
| Organization/Department | | ❌ | - | Không có org hierarchy management |
| API Key Management | | ❌ | - | Không native, cần custom SPI |
| Rate Limiting | | ❌ | - | Không có |
| Approval Workflow | | ❌ | - | Không có |
| Spring Boot 4.x Integration | ✅ | | Via resource server starter | External dependency |
| Customization | ⚠️ | | SPI extensible | Complex SPI development |

**Verdict**: Dùng làm optional downstream IdP — delegate authentication
**Recommendation**: Optional IdP — adapter pattern cho phép switch on/off
**Reasoning**: Keycloak xuất sắc cho authentication/SSO nhưng thiếu hoàn toàn business-specific features (menu, org, workflow). Project đã có `SsoAdapter.kt` với adapter pattern.

### Cerbos — Gap Analysis

**Overall Score**: 7.35 / 10
**URL**: https://github.com/cerbos/cerbos

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Fine-grained Policy Evaluation | ✅ | | YAML-based policies, playground | - |
| ABAC/PBAC | ✅ | | Policy-as-code, highly flexible | - |
| User/Role Management | | ❌ | - | Chỉ evaluate, không manage |
| Menu/UI Permission | | ❌ | - | Không có concept |
| Integration (Spring Boot) | ⚠️ | | Java SDK available | Separate Go service, thêm infra |

**Verdict**: Không phù hợp — project đã có PolicyEvaluator custom
**Recommendation**: Bỏ qua — overkill cho current stage
**Reasoning**: PolicyEvaluator hiện tại đã cover PBAC use case. Thêm Cerbos = thêm infrastructure mà không gain thêm functionality đáng kể.

### Bucket4j — Gap Analysis

**Overall Score**: 8.35 / 10
**URL**: https://github.com/bucket4j/bucket4j

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Token Bucket Algorithm | ✅ | | Standard algorithm, configurable | - |
| Redis Backend | ✅ | | ProxyManager + Redisson/Lettuce | - |
| Distributed Rate Limiting | ✅ | | Multi-node support | - |
| Spring Boot Integration | ✅ | | Spring Boot starter available | - |
| API Key Quota | ⚠️ | | Bandwidths configurable | Need custom key-based strategy |
| Admin Dashboard | | ❌ | - | Metrics only, no UI |

**Verdict**: Dùng trực tiếp
**Recommendation**: **ADOPT** — perfect fit cho API partner rate limiting
**Reasoning**: Native Java, Spring Boot starter, Redis-backed distributed rate limiting. Complements existing `ApiPartnerService.kt` in system-admin-service.

### OpenFGA — Gap Analysis

**Overall Score**: 6.80 / 10
**URL**: https://github.com/openfga/openfga

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| ReBAC | ✅ | | Google Zanzibar proven model | Complex for current needs |
| RBAC | ✅ | | DSL-based authorization model | - |
| Infrastructure | | ❌ | - | Separate service, heavy setup |
| Menu Permission | | ❌ | - | Không có concept |

**Verdict**: Không phù hợp — heavy infrastructure, overkill
**Recommendation**: Bỏ qua — xem xét khi cần ReBAC cho complex data relationships
**Reasoning**: Quá heavy cho current scale. RbacEngine + PolicyEvaluator hiện tại đủ dùng.

### Casbin — Gap Analysis

**Overall Score**: 7.20 / 10
**URL**: https://github.com/casbin/casbin

| Aspect | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|--------|:---:|:---:|-----------|----------|
| Embeddable Library | ✅ | | No separate infrastructure | - |
| RBAC/ABAC/ACL | ✅ | | Multiple models supported | - |
| Spring Boot Native | | ❌ | - | Java adapter, not native integration |
| Migration Effort | | ❌ | - | Would replace working RbacEngine |

**Verdict**: Không phù hợp — migration effort không xứng đáng
**Recommendation**: Bỏ qua — custom implementation đã tốt hơn về integration fit
**Reasoning**: Project đã có RbacEngine + PolicyEvaluator custom với integration chặt vào base-core architecture.

---

## 5. Tổng hợp

### Top projects xếp hạng

| Rank | Dự án | Tổng điểm | Verdict | Phù hợp nhất cho |
|------|-------|-----------|---------|------------------|
| 🥇 1 | Keycloak | 9.25/10 | Optional IdP (adapter pattern) | Authentication/SSO delegation |
| 🥈 2 | Bucket4j | 8.35/10 | **ADOPT** | API partner rate limiting |
| 🥉 3 | Cerbos | 7.35/10 | Skip | Future policy engine (nếu scale) |
| 4 | Casbin | 7.20/10 | Skip | Alternative RBAC (nhưng đã có custom) |
| 5 | OpenFGA | 6.80/10 | Skip | Future ReBAC (nếu cần complex relationships) |

### Recommendation tổng hợp

| Quyết định | Lý do | Evidence |
|-----------|-------|---------|
| **ADOPT Bucket4j** cho rate limiting | Production-proven, Spring Boot native, Redis-backed distributed | https://bucket4j.com/ |
| **Optional Keycloak** via adapter pattern | Avoid lock-in, delegate auth khi cần enterprise SSO. `SsoAdapter.kt` đã có sẵn | https://www.keycloak.org/ |
| **Enhance existing** cho menu, org, workflow | Code đã có trong system-admin-service — enhance, not build from scratch | Codebase scan results |
| **Keep existing** RbacEngine + PolicyEvaluator | Already integrated, sufficient cho current scale | Codebase scan results |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-07-15
> **Next step**: Comparison Analysis (comparison_analysis.md)
