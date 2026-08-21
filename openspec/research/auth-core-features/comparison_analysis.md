# Phân tích so sánh: Auth Core Features

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Recommendation.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Auth Core Features (FR-001 MFA, FR-002 SSO, FR-003 Token RS256, FR-004 Password Policy) |
| **Ngày phân tích** | 2026-08-22 |
| **Recommendation** | **Build from scratch** (using battle-tested open source libraries for core primitives) |
| **Rationale** | Tất cả libraries cần thiết đã available (totp 1.7.1, Passay 1.6.4, Spring OAuth2 native, JJWT). Custom code cho orchestration layer (MfaService, SsoAdapter) và data layer. Auth là core business logic — không nên delegate cho external IdP hoàn toàn. ~95% code đã implemented, V2+V10 migrations đã applied. |
| **Confidence** | **HIGH** — All libraries đã integrate thành công trong codebase, V2+V10 migrations deployed, all services functional |

---

## 2. Ma trận so sánh (Comparison Matrix)

### Tổng quan giải pháp

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:-:|:---:|
| 1 | Keycloak (full delegation) | External IdP | Delegate toàn bộ auth cho Keycloak | Minimal custom code, full-featured IdP | Lose control, adapter deprecated, heavy infrastructure | ⚠️ | 5.5 |
| 2 | Auth0 / Okta | Commercial SaaS | Fully managed auth service | Zero infra management, enterprise features | Cost at scale, vendor lock-in, data sovereignty | ❌ | 4.0 |
| 3 | Spring Authorization Server | Open Source | Spring-native OAuth2 authorization server | Full Spring integration, extensible | Steep learning curve, overkill for current needs | ⚠️ | 6.0 |
| 4 | Custom Build (libraries) | In-house | Build orchestration + use OS libraries for primitives | Full control, exact-fit features, no vendor lock-in | More code to maintain, must handle edge cases | ✅ | 8.5 |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Keycloak (full) | Auth0/Okta | Spring Auth Server | Custom Build | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| MFA (TOTP) | ✅ | ✅ | ❌ | ✅ | ⭐ Must |
| MFA (OTP SMS/Email) | ✅ | ✅ | ❌ | ✅ | ⭐ Must |
| MFA Recovery Codes | ✅ | ✅ | ❌ | ✅ | ⭐ Must |
| CAPTCHA integration | ❌ | ✅ | ❌ | ✅ | ⭐ Must |
| SSO (Google/Microsoft) | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| SSO (Keycloak as optional IdP) | ✅ | ❌ | ⚠️ | ✅ | ⭐ Must |
| JIT Provisioning + Domain event | ⚠️ | ❌ | ❌ | ✅ | ⭐ Must |
| JWT RS256 + JWKS | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Token Introspection (RFC 7662) | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Session binding + force logout | ⚠️ | ✅ | ❌ | ✅ | ⭐ Must |
| Password Policy per domain | ❌ | ⚠️ | ❌ | ✅ | ⭐ Must |
| Password history + expiry | ⚠️ | ⚠️ | ❌ | ✅ | ⭐ Must |
| Multi-domain RBAC embedding in JWT | ❌ | ❌ | ⚠️ | ✅ | ⭐ Must |
| Hexagonal architecture fit | ❌ | ❌ | ⚠️ | ✅ | ⭐ Must |
| Trusted device (skip MFA with TTL) | ❌ | ✅ | ❌ | ✅ | Nice to have |
| CQRS/Event Sourcing integration | ❌ | ❌ | ❌ | ✅ | Nice to have |
| E2EE integration (existing) | ❌ | ❌ | ❌ | ✅ | Nice to have |
| **Coverage** | **7/14** | **6/14** | **3/14** | **14/14** | |

### Priority breakdown

| Priority | Tổng features | Coverage range |
|----------|:---:|:---:|
| ⭐ Must | 14 | 21% (Spring Auth Server) - 100% (Custom Build) |
| Nice to have | 3 | 0% (all external) - 100% (Custom Build) |
| Optional | 0 | N/A |

---

## 4. Gap Analysis tổng hợp

### 4.1 Requirement vs Available Solutions

| Requirement | Source | Keycloak | Auth0/Okta | Custom Build | Gap? |
|-------------|--------|:---:|:---:|:---:|:---:|
| FR-001: MFA (TOTP + OTP + CAPTCHA + Recovery) | UC-001, UC-005 | ⚠️ | ✅ | ✅ | Keycloak lacks CAPTCHA natively |
| FR-002: SSO + JIT + Domain event | UC-002 | ⚠️ | ❌ | ✅ | External solutions can't emit to internal EventPublisher |
| FR-003: RS256 + Introspection + Session | UC-003 | ✅ | ✅ | ✅ | No gap |
| FR-004: Password Policy per Domain | UC-004, UC-006 | ❌ | ⚠️ | ✅ | Keycloak has realm-level only, not per-domain |
| Multi-domain RBAC in JWT claims | Business rule | ❌ | ❌ | ✅ | Critical — external solutions can't embed custom RBAC |
| Hexagonal architecture compliance | Tech constraint | ❌ | ❌ | ✅ | External solutions break architecture |

### 4.2 Current System vs Target System

| Aspect | Current System | Target System | Gap | Impact |
|--------|---------------|---------------|-----|--------|
| JWT Signing | RS256 (primary) + HMAC fallback | RS256 only (post-migration) | Remove HMAC fallback after migration period | LOW |
| MFA | Fully implemented (MfaService + OtpService + TotpService + recovery codes) | Production-ready MFA | SMS/Email delivery adapter (TODO in code) | LOW |
| SSO/OAuth2 | SsoAdapter + OAuth2TokenExchanger + EventPublisher port | Production SSO | Keycloak realm config, production client IDs | LOW |
| Password Policy | PasswordPolicyService with Passay, ConcurrentHashMap cache | Per-domain enforcement | Already working — V2 migration deployed | LOW |
| Token Introspection | TokenController with `/api/auth/introspect` | RFC 7662 compliant | Already implemented | LOW |
| JWKS Endpoint | `/.well-known/jwks.json` implemented | Public key exposure | Already working | LOW |
| Session Management | LoginSessionService + SessionPolicyService with Redis | Full session binding | `revokeAllSessions()` needs verification | MEDIUM |
| Trusted Device | `trustedDeviceHash` + `trusted_device_set_at` (V10) | TTL-based skip MFA | Already implemented with TTL tracking | LOW |
| Recovery Codes | MfaService with SHA-256 hashed Redis list | Single-use backup MFA | Already implemented — generate/verify/count | LOW |

### 4.3 Custom Build vs Reuse

| Factor | Custom Build | Reuse Keycloak (Best External) | Winner |
|--------|:---:|:---:|:---:|
| Time to market | 3-5 developer-days (remaining polish + tests) | 10-15 developer-days (architecture change) | **Custom Build** |
| Maintenance burden | Owner responsibility | Keycloak community | Keycloak |
| Feature coverage | 100% (14/14 Must features) | 50% (7/14 Must features) | **Custom Build** |
| Integration effort | Minimal (already integrated) | High (architecture change) | **Custom Build** |
| Long-term flexibility | Full control, evolve freely | Constrained by Keycloak roadmap | **Custom Build** |
| Risk | Code maintenance, edge cases | Vendor dependency, breaking changes | Neutral |

---

## 5. Recommendation chi tiết

### Decision Matrix

| Tiêu chí | Trọng số | Keycloak (full) | Auth0/Okta | Custom Build |
|----------|----------|:---:|:---:|:---:|
| Feature coverage | 30% | 5 | 4 | 10 |
| Integration ease | 25% | 3 | 2 | 9 |
| Maintenance | 20% | 8 | 9 | 5 |
| Community/Support | 15% | 7 | 9 | 6 |
| Learning curve | 10% | 4 | 6 | 8 |
| **Tổng điểm (weighted)** | | **5.15** | **5.15** | **7.95** |

### Reasoning

**Recommended approach**: Build from scratch (using open source libraries)

**Lý do**:
1. **Feature coverage = 100%**: Custom build là giải pháp duy nhất cover tất cả 14 Must features — đặc biệt multi-domain RBAC embedding trong JWT claims, JIT provisioning + domain events, password policy per domain, recovery codes, và CAPTCHA integration.
2. **Already implemented (~95%)**: Hầu hết code đã written (MfaService, OtpService, TotpService, SsoAdapter, PasswordPolicyService, JwtService with RS256, TokenController, recovery codes). Chuyển sang external solution = throw away working code.
3. **Architecture fit**: Hexagonal architecture hiện tại được preserve. External IdP sẽ break architecture boundaries và coupling patterns.

**Trade-offs chấp nhận**:
- **Code maintenance responsibility** — chấp nhận vì team đã own codebase, auth là core business logic
- **No out-of-the-box admin UI** — chấp nhận vì admin operations qua API endpoints, future admin UI separate concern

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|-----------|
| `dev.samstevens.totp` unmaintained (GitHub 404) | MED | LOW | TOTP is stable protocol (RFC 6238), library works. Fork if needed. |
| RS256 key compromise | LOW | HIGH | Key rotation via JWKS `kid`, private key in PEM file (env-configured path) |
| OTP delivery failure (SMS/Email) | MED | MED | Adapter pattern allows provider swap, fallback to TOTP, recovery codes as backup |
| Redis unavailability | LOW | HIGH | Graceful degradation: skip MFA if Redis down + alert, recovery codes as offline backup |

### Cost/Effort Estimate (Coarse)

| Approach | Effort (developer-days) | Complexity | Long-term cost |
|----------|:-:|:-:|:-:|
| Custom Build (already ~95% done) | 2-4 (remaining polish + tests) | LOW | LOW |
| Keycloak migration | 10-15 (architecture change + data migration) | HIGH | MED |
| Auth0/Okta adoption | 5-8 (integration + vendor setup) | MED | HIGH (subscription) |

---

## 6. Data Sources

| # | Artifact | Vai trò |
|---|---------|---------|
| 1 | [research_brief.md](./research_brief.md) | Scope, keywords, current system analysis |
| 2 | [opensource_findings.md](./opensource_findings.md) | Open source evaluation + scoring matrix |
| 3 | [web_research.md](./web_research.md) | Internet research + product evaluation |

---

> **Next step**: Business Analysis (business_analysis.md)
