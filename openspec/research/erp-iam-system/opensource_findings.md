# Open Source Findings — ERP IAM System

> Created: 2026-08-05
> Iteration: 1

---

## 1. Evaluated Projects

### Project 1: Keycloak
| Criteria | Score (1-5) | Notes |
|----------|-------------|-------|
| Feature Coverage | ⭐⭐⭐⭐⭐ | Full IdP, RBAC, UMA 2.0, OIDC, SAML, MFA |
| Maturity | ⭐⭐⭐⭐⭐ | Red Hat maintained, huge community |
| Documentation | ⭐⭐⭐⭐⭐ | Excellent, official docs + community guides |
| Customizability | ⭐⭐⭐⭐ | SPI extensible but complex |
| Integration Fit | ⭐⭐⭐ | External service, cần adapter |
| **Total** | **22/25** | |

**Gap Analysis:**
- ✅ Authentication, SSO, MFA, OAuth2/OIDC
- ✅ User management, session management
- ❌ Không có menu permission system
- ❌ Không có approval workflow
- ❌ Không có API key management / rate limiting
- ❌ Không có organization/department management

**Recommendation:** Dùng làm **optional downstream IdP** — delegate authentication, nhưng authorization logic (RBAC/PBAC/menu) vẫn cần custom.

**Source:** https://www.keycloak.org/documentation

---

### Project 2: Cerbos (Policy Engine)
| Criteria | Score (1-5) | Notes |
|----------|-------------|-------|
| Feature Coverage | ⭐⭐⭐⭐ | PBAC/ABAC policy-as-code, YAML policies |
| Maturity | ⭐⭐⭐⭐ | CNCF sandbox, production-ready |
| Documentation | ⭐⭐⭐⭐ | Good docs, playground available |
| Customizability | ⭐⭐⭐⭐⭐ | Policy-as-code, highly flexible |
| Integration Fit | ⭐⭐⭐ | Separate service, SDK available |
| **Total** | **20/25** | |

**Gap Analysis:**
- ✅ Fine-grained policy evaluation
- ✅ YAML-based policy definition
- ❌ Không có UI/menu system
- ❌ Không có user/role management (chỉ evaluate)
- ❌ Overkill cho current stage — project đã có PolicyEvaluator tương tự

**Recommendation:** **Không adopt** — PolicyEvaluator hiện tại đã cover PBAC use case. Có thể xem xét khi scale lên multi-service policy enforcement.

**Source:** https://docs.cerbos.dev/

---

### Project 3: OpenFGA (Google Zanzibar-based)
| Criteria | Score (1-5) | Notes |
|----------|-------------|-------|
| Feature Coverage | ⭐⭐⭐⭐ | ReBAC + RBAC, relationship-based |
| Maturity | ⭐⭐⭐ | CNCF sandbox, growing fast |
| Documentation | ⭐⭐⭐⭐ | Good, playground + visualizer |
| Customizability | ⭐⭐⭐⭐ | DSL-based authorization model |
| Integration Fit | ⭐⭐ | Separate infrastructure, complex setup |
| **Total** | **17/25** | |

**Gap Analysis:**
- ✅ Relationship-based access (owner of, viewer of)
- ✅ Google Zanzibar proven pattern
- ❌ Heavy infrastructure overhead cho current scale
- ❌ Không có menu/UI permission concept
- ❌ Learning curve cao

**Recommendation:** **Không adopt** — quá heavy cho current stage. Có thể xem xét khi cần ReBAC pattern cho complex data relationships.

**Source:** https://openfga.dev/docs

---

### Project 4: Casbin (RBAC/ABAC Library)
| Criteria | Score (1-5) | Notes |
|----------|-------------|-------|
| Feature Coverage | ⭐⭐⭐⭐ | RBAC, ABAC, ACL — embedded library |
| Maturity | ⭐⭐⭐⭐ | 5+ years, multiple language support |
| Documentation | ⭐⭐⭐ | Adequate, but scattered |
| Customizability | ⭐⭐⭐⭐ | Model DSL (Casbin model syntax) |
| Integration Fit | ⭐⭐⭐ | Java adapter available |
| **Total** | **18/25** | |

**Gap Analysis:**
- ✅ Embeddable authorization library
- ✅ Policy model DSL
- ❌ Không native Spring Boot integration
- ❌ Project đã có RbacEngine + PolicyEvaluator custom
- ❌ Migration effort không xứng đáng

**Recommendation:** **Không adopt** — custom implementation đã tốt hơn về integration fit.

**Source:** https://casbin.org/docs/get-started

---

### Project 5: Bucket4j (Rate Limiting)
| Criteria | Score (1-5) | Notes |
|----------|-------------|-------|
| Feature Coverage | ⭐⭐⭐⭐ | Token bucket algorithm, Redis support |
| Maturity | ⭐⭐⭐⭐⭐ | Production-proven, well-maintained |
| Documentation | ⭐⭐⭐⭐ | Clear with examples |
| Customizability | ⭐⭐⭐⭐⭐ | Highly configurable |
| Integration Fit | ⭐⭐⭐⭐⭐ | Spring Boot starter available |
| **Total** | **23/25** | |

**Gap Analysis:**
- ✅ Perfect fit cho API key rate limiting
- ✅ Redis-backed distributed rate limiting
- ✅ Spring Boot integration excellent
- ✅ base-core đã có `base-resilience-starter` (Resilience4j) — Bucket4j complement it

**Recommendation:** **ADOPT** — Dùng cho API partner rate limiting. Integrate vào base-security-starter hoặc tạo filter riêng.

**Source:** https://bucket4j.com/

---

## 2. Summary Scoring Matrix

| Project | Feature | Maturity | Docs | Custom | Integration | Total | Decision |
|---------|---------|----------|------|--------|-------------|-------|----------|
| Keycloak | 5 | 5 | 5 | 4 | 3 | 22/25 | Optional IdP |
| Cerbos | 4 | 4 | 4 | 5 | 3 | 20/25 | Skip |
| OpenFGA | 4 | 3 | 4 | 4 | 2 | 17/25 | Skip |
| Casbin | 4 | 4 | 3 | 4 | 3 | 18/25 | Skip |
| Bucket4j | 4 | 5 | 4 | 5 | 5 | 23/25 | **ADOPT** |

---

## 3. Validation Checklist (Phase 2)

- [x] ≥ 3 projects evaluated → 5 projects
- [x] Scoring matrix completed for each
- [x] Gap analysis for top projects
- [x] Each finding has source URL
