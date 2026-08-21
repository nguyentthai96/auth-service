# Kết quả nghiên cứu Internet: ERP IAM System

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Enterprise IAM System (3 Modules: auth, account, system-admin) |
| **Ngày nghiên cứu** | 2025-07-15 |
| **Số iterations** | 4 |
| **Tổng sources** | 15 unique |
| **Keywords ban đầu** | `Enterprise IAM`, `RBAC PBAC`, `Spring Security 7 MFA`, `Menu Permission`, `API Key Management` |
| **Keywords phát triển** | `FactorGrantedAuthority`, `DPoP`, `Bucket4j`, `Recursive CTE`, `policy-as-code`, `tree permission`, `button-level ACL` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"enterprise IAM system architecture microservices best practices"` | PBAC đã trở thành enterprise standard thay RBAC thuần; Zero Trust architecture | `policy-as-code`, `AuthorizationManager` |
| 2 | `"Spring Security 7 multi-factor authentication architecture"` | Spring Security 7 có native MFA: `FactorGrantedAuthority`, `@EnableMultiFactorAuthentication` | `FactorGrantedAuthority`, `progressive authorization` |
| 3 | `"dynamic menu permission system backend frontend button-level"` | Permission-First approach: check `user.can('action')` thay vì `user.role === 'admin'` | `tree permission`, `button-level ACL` |

**Takeaways Iteration 1:**
- PBAC/ABAC đã trở thành enterprise best practice, thay thế RBAC đơn thuần
- JWT chỉ mang identity + baseline roles, KHÔNG overload permissions vào token (token bloat)
- Spring Security 7 có `AuthorizationManager` API mới thay thế legacy `AccessDecisionManager`
- Menu permission là business-specific — không có universal solution, cần custom build
- Zero Trust: mTLS giữa services, mọi inter-service request phải authorized
- Dynamic role-based permission for frontend/backend separation is well-documented pattern

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://spring.io/blog/2024/spring-security-7-mfa | Spring Security 7 MFA Documentation | Native MFA với FactorGrantedAuthority, progressive auth flow | 10 |
| 2 | https://www.keycloak.org/documentation | Keycloak Server Administration | Full IdP capabilities, realm/client model, SPI extensibility | 9 |
| 3 | https://owasp.org/www-project-cheat-sheets/cheatsheets/Access_Control_Cheat_Sheet | OWASP Access Control Cheat Sheet | Permission-based over role-based, defense in depth | 9 |
| 4 | https://bucket4j.com/8.14.0/toc.html | Bucket4j Documentation | Token bucket with Redis ProxyManager, Spring Boot starter | 8 |
| 5 | https://www.nist.gov/publications/guide-abac | NIST ABAC Guide (SP 800-162) | ABAC policy model, PDP/PEP/PAP/PIP architecture | 8 |
| 6 | https://docs.cerbos.dev/cerbos/latest/ | Cerbos Documentation | Policy-as-code, YAML policies, audit trail | 7 |

**Takeaways Iteration 2:**
- **Approach 1 — Spring Security 7 Native MFA**: `FactorGrantedAuthority` cho progressive authorization. Partial auth → 2FA challenge → full auth. Trade-off: tightly coupled với Spring Security lifecycle.
- **Approach 2 — Custom MFA Flow**: Separate partial token → verify endpoint → full token. Trade-off: more flexible but more code to maintain. **Project đã implement approach này** (see `SessionPromotionService.kt`).
- **Approach 3 — Keycloak Delegated MFA**: Delegate MFA hoàn toàn cho Keycloak. Trade-off: simpler code but Keycloak dependency.
- **Backend as Security Enforcer**: Frontend chỉ là UX enhancement, NOT security boundary. Dynamic menu fetch từ backend sau login.

---

### Iteration 3 — TARGETED (Menu Permission + Organization)

**Mục tiêu**: Fill gaps cho menu permission và organization management patterns

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | https://www.besthub.dev/articles/designing-dynamic-role-based-permission-management-for-frontend-and-backend-systems-06de0545fda0 | Dynamic Role-Based Permission Management | Permission = Resource × Action matrix; tree structure with recursive queries; database-driven permission | 9 |
| 2 | https://www.besthub.dev/articles/designing-dynamic-role-based-permissions-for-front-back-end-separation-3085ed068ad3 | Dynamic Role-Based Permissions for Front-Back End Separation | Database-driven roles/permissions, menu visibility by role, button-level ACL | 9 |
| 3 | https://blog.devgenius.io/part-2-frontend-setup-dynamic-menus-and-permissions-in-react-2682793b2e2c | Dynamic Menus and Permissions in React | Frontend pattern: `<ShowIf permission="...">`, server-side filtering + client-side enhancement | 8 |
| 4 | PostgreSQL documentation (recursive CTE) | Recursive CTE Queries | `WITH RECURSIVE` for tree traversal; performant for ≤10 levels | 8 |
| 5 | Spring Security method-level docs | Method Security Annotations | `@PreAuthorize`, custom `AuthorizationManager`, SpEL expressions | 7 |

**Takeaways Iteration 3:**
- Menu permission best practice: **Backend enforces, frontend hides** — project's `MenuPermissionService.kt` already follows this
- Tree structure: PostgreSQL recursive CTE đủ performance cho ≤10 levels (project has `TreeEntity.kt` + `TreeBuilder.kt`)
- Button-level permission: encode as `menu_code:action_code` format (e.g., `user-management:delete`)
- Cache strategy: Redis cache per user per domain, invalidate on permission change (event-driven) — project has `AbstractTwoTierCache.kt`
- Dynamic role-based permission design for frontend/backend is a well-documented pattern with clear implementation strategies

---

### Iteration 4 — TARGETED (API Partner + Rate Limiting + Workflow)

**Mục tiêu**: Fill gaps cho API partner management và approval workflow

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | Stripe API documentation | API Key Management Pattern | Prefix-based keys (`pk_`/`sk_`), show-once, hash storage | 9 |
| 2 | https://bucket4j.com/ | Bucket4j Rate Limiting | Token bucket + Redis ProxyManager, Spring Boot filter integration | 9 |
| 3 | Kong Gateway documentation | API Gateway Rate Limiting | Edge-level rate limiting, consumer quotas, sliding window | 7 |
| 4 | State machine vs workflow patterns | Approval Workflow Patterns | Custom state machine simpler than Camunda/Temporal for ERP approvals — project has `WorkflowEngine.kt` | 8 |

**Stop reason**: All research questions answered — diminishing returns on further search

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Docs | Spring Security 7 MFA | https://spring.io/projects/spring-security | Native MFA, FactorGrantedAuthority | 10 | Framework-native, reduces custom code | Tightly coupled with Spring lifecycle |
| 2 | Docs | Keycloak Administration | https://www.keycloak.org/documentation | Full IdP, OIDC/SAML, admin console | 9 | Enterprise-grade, Red Hat backed | External dependency, complexity |
| 3 | Standard | OWASP Access Control | https://owasp.org/www-project-cheat-sheets/ | Permission-based approach, defense in depth | 9 | Industry standard, comprehensive | Generic guidelines |
| 4 | Standard | NIST ABAC Guide | https://www.nist.gov/publications/guide-abac | PDP/PEP/PAP/PIP architecture, formal model | 8 | Formal specification, well-researched | Academic, needs practical adaptation |
| 5 | Docs | Bucket4j | https://bucket4j.com/ | Token bucket + Redis, Spring Boot | 9 | Production-proven, embeddable | Application-level only |
| 6 | Standard | RFC 6238 TOTP | https://tools.ietf.org/html/rfc6238 | TOTP algorithm specification | 8 | Internet standard | Algorithm only, no implementation guidance |
| 7 | Standard | RFC 4226 HOTP | https://tools.ietf.org/html/rfc4226 | HMAC-based OTP foundation | 7 | Foundation for TOTP | Superseded by TOTP for most uses |
| 8 | Pattern | Stripe API Key Model | https://stripe.com/docs/api/authentication | Show-once keys, prefix-based, rotation | 9 | Industry best practice | Stripe-specific details |
| 9 | Docs | Cerbos | https://docs.cerbos.dev/ | Policy-as-code, audit trail | 7 | Powerful policy engine | Separate Go service |
| 10 | Docs | OpenFGA | https://openfga.dev/docs | Zanzibar-based ReBAC | 7 | Google-proven pattern | Heavy infrastructure |
| 11 | Article | Dynamic Role-Based Permission Management | https://www.besthub.dev/articles/designing-dynamic-role-based-permission-management-for-frontend-and-backend-systems-06de0545fda0 | Permission matrix, tree structure, DB-driven | 9 | Practical implementation guidance | Specific to certain stack |
| 12 | Article | Dynamic Permissions for Front-Back Separation | https://www.besthub.dev/articles/designing-dynamic-role-based-permissions-for-front-back-end-separation-3085ed068ad3 | Menu visibility by role, button-level ACL | 9 | Directly applicable to project | Medium-specific details |
| 13 | Article | Dynamic Menus in React | https://blog.devgenius.io/part-2-frontend-setup-dynamic-menus-and-permissions-in-react-2682793b2e2c | Frontend `ShowIf` pattern, server-side filtering | 8 | Frontend integration guidance | React-specific |
| 14 | Repo | Backend-Public (Dynamic roles/menus) | https://github.com/Fazulhaq/Backend-Public | Spring Boot backend with dynamic roles and menus with permission based | 7 | Reference implementation | Small project, limited scope |
| 15 | Standard | RFC 9449 DPoP | https://www.rfc-editor.org/rfc/rfc9449 | Demonstrating Proof-of-Possession for OAuth2 | 6 | Enhanced token security | New standard, limited adoption |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| **Keycloak** | Full IdP — delegate toàn bộ identity management | OIDC, SAML, MFA, social login, admin console | Không cần build auth from scratch | Enterprise-grade, proven | External dependency, no menu/org/workflow | Menu, Org, Workflow, API Key |
| **Permit.io** | Full-stack permission SaaS | UI dashboard, policy editor, audit trail | Fast setup, managed service | Visual policy management | External SaaS, cost, vendor lock-in | Self-hosted requirement |
| **Casl.js** | Frontend permission framework | Declarative permission checks, React/Vue | Simplifies frontend permission logic | Lightweight, well-documented | Frontend-only, needs backend support | Backend enforcement |
| **Kong Gateway** | API gateway with plugins | Rate limiting, auth, analytics | Plugin ecosystem, scalable | Feature-rich | Heavy infrastructure, separate service | Overkill for current scale |
| **Bucket4j** | Embeddable rate limiting | Token bucket, Redis distributed | Lightweight, Spring Boot native | Simple integration | Application-level only | No admin dashboard |

### So sánh tính năng chi tiết

| Feature | Keycloak | Permit.io | Bucket4j | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Authentication/SSO | ✅ | ❌ | ❌ | ✅ | ⭐ Must (đã có) |
| MFA/2FA | ✅ | ❌ | ❌ | ✅ | ⭐ Must (đã có) |
| RBAC | ✅ | ✅ | ❌ | ✅ | ⭐ Must (đã có) |
| PBAC/ABAC | ⚠️ | ✅ | ❌ | ✅ | ⭐ Must (đã có) |
| Menu Permission | ❌ | ❌ | ❌ | ✅ | ⭐ Must (đã có) |
| Button-level Permission | ❌ | ❌ | ❌ | ✅ | ⭐ Must |
| Organization Hierarchy | ❌ | ❌ | ❌ | ✅ | ⭐ Must (đã có) |
| API Key Management | ❌ | ❌ | ❌ | ✅ | ⭐ Must (đã có) |
| Rate Limiting | ❌ | ❌ | ✅ | ⚠️ | ⭐ Must |
| Approval Workflow | ❌ | ❌ | ❌ | ✅ | Should (đã có) |
| Audit Trail | ✅ | ✅ | ❌ | ✅ | ⭐ Must (đã có) |
| Self-hosted | ✅ | ❌ | ✅ | ✅ | ⭐ Must |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | **Permission-First** | Check `user.can('action')` thay vì `user.role === 'admin'` | Fine-grained, future-proof | More initial setup | Khi cần button-level control | OWASP |
| 2 | **Backend-as-Enforcer** | Frontend chỉ hide UI, backend enforce permissions | Secure | Cần sync permission data | Mọi enterprise app | OWASP |
| 3 | **Token-Minimal** | JWT chỉ mang identity, permissions query on-demand | Small tokens, fresh permissions | Extra API call per request (mitigate with cache) | Khi có nhiều permissions | Industry best practice |
| 4 | **Progressive Auth** | Partial token → 2FA → full token | Flexible MFA flow | More complex flow | Khi có MFA | Spring Security 7 |
| 5 | **Adapter Pattern for IdP** | Interface-based SSO, swap Keycloak/local freely | No vendor lock-in | Need interface design | Khi IdP có thể thay đổi | Clean Architecture |
| 6 | **Show-Once API Key** | Generate → display once → hash store | Security best practice | User must save key | API key management | Stripe |
| 7 | **Event-Driven Cache Invalidation** | Kafka events trigger cache clear | Fresh permissions | Eventual consistency | Distributed permission cache | Microservices pattern |
| 8 | **DB-Driven Menu Tree** | Menu items stored in DB with parent_id, roles assigned via mapping tables | Dynamic configuration | Requires recursive queries | Enterprise admin systems | BestHub articles |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | Spring Security 7 `@EnableMultiFactorAuthentication` exact API? | ✅ | Spring Boot 4.1.0 docs chưa hoàn chỉnh tại thời điểm research | LOW — đã có custom MFA flow fallback |
| 2 | DPoP (Demonstrating Proof-of-Possession) implementation in Spring? | ✅ | RFC 9449 mới, Spring Security 7 support đang phát triển | LOW — optional enhancement cho Phase 4 |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2025-07-15
> ⚠️ Assumption: Spring Security 7 MFA API dựa trên early documentation — cần verify khi Spring Boot 4.1.0 GA
> **Next step**: Comparison Analysis (comparison_analysis.md)
