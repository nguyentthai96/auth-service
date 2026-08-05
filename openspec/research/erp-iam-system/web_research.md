# Web Research — ERP IAM System

> Created: 2026-08-05
> Search Iterations: 4

---

## Iteration 1: Enterprise RBAC/PBAC Architecture

### Findings
- **PBAC** (Policy-Based Access Control) đã trở thành enterprise best practice, thay thế RBAC đơn thuần
- Architecture pattern: **Centralized Policy Engine** + **Decoupled Enforcement Points**
- JWT chỉ mang identity + baseline roles, KHÔNG overload permissions vào token (token bloat)
- Spring Security 7 có `AuthorizationManager` API mới thay thế legacy `AccessDecisionManager`
- Zero Trust: mTLS giữa services, mọi inter-service request phải authorized

### Key Sources
1. Spring Security Reference - Authorization Architecture
2. OWASP Access Control Cheat Sheet
3. Google Zanzibar paper (2019) — inspiration cho ReBAC
4. NIST ABAC Guide (SP 800-162)

---

## Iteration 2: Menu Permission & Button-Level Access

### Findings
- **Backend as Security Enforcer** — frontend chỉ là UX enhancement, NOT security
- **Permission-First approach**: check `user.can('delete_record')` thay vì `user.role === 'admin'`
- JWT Claims encode permissions → frontend cache trong state management
- Dynamic menu: fetch allowed routes/menu items từ backend sau login
- Component-level permission: custom hook/HOC pattern (`<ShowIf permission="...">`)
- Performance: cache permission set client-side, chỉ refresh khi token renew

### Key Sources
1. Enterprise RBAC patterns in React/Vue
2. Spring Security method-level security annotations

### Products/Tools Evaluated
| Product | Pros | Cons |
|---------|------|------|
| **Casl.js** (frontend) | Permission framework cho React/Vue, declarative | Frontend-only, cần backend support |
| **Permit.io** | Full-stack permission SaaS, UI dashboard | External dependency, cost |
| **Custom implementation** | Full control, fits base-core architecture | Development effort |

**Recommendation:** Custom implementation phù hợp nhất vì:
- Đã có base-core architecture
- Cần integration chặt với auth-service RBAC/PBAC
- Menu system là business-specific, không có one-size-fits-all

---

## Iteration 3: MFA/2FA & Security

### Findings
- **Spring Security 7** native MFA support: `FactorGrantedAuthority`, `@EnableMultiFactorAuthentication`
- TOTP library: `dev.samstevens.totp:java-otp` — chuẩn cho Java/Kotlin
- SMS/Email OTP: async dispatch (`@Async`), tránh blocking login thread
- CAPTCHA: reCAPTCHA v3 (invisible) hoặc hCaptcha recommended
- Rate limiting on verification endpoints: chống brute-force
- Recovery codes: generate 10 codes, hash với Argon2, dùng 1 lần

### Key Sources
1. Spring Security 7 MFA documentation (spring.io)
2. RFC 6238 — TOTP Algorithm
3. RFC 4226 — HOTP Algorithm
4. Google reCAPTCHA v3 documentation

---

## Iteration 4: API Partner & Rate Limiting

### Findings
- **API Gateway** xử lý edge-level rate limiting + auth
- **Centralized Quota Service** dùng Redis cho distributed rate tracking
- Subscription tiers: Free/Pro/Enterprise với different limits
- API key lifecycle: provisioning → rotation → revocation → audit
- Key format best practice: prefix-based (`pk_` production, `sk_` sandbox)
- Overage policies: hard block (429), throttle, allow-and-bill
- Bucket4j + Redis: production-proven cho Spring Boot rate limiting
- Anomaly detection: trend-based monitoring cho unusual API patterns

### Key Sources
1. Stripe API key management pattern
2. Bucket4j documentation
3. Kong API Gateway rate limiting plugin documentation

### Products/Tools Evaluated
| Product | Pros | Cons |
|---------|------|------|
| **Kong Gateway** | Full API management, plugins | Heavy infrastructure |
| **Spring Cloud Gateway** | Native Spring ecosystem | Less feature-rich than Kong |
| **Bucket4j** | Lightweight, embeddable | Application-level only |
| **Redis + Custom Filter** | Full control, flexible | Development effort |

**Recommendation:** **Bucket4j + Redis** — embeddable, Spring Boot native, fits base-core architecture.

---

## 5. Validation Checklist (Phase 3)

- [x] ≥ 3 search iterations → 4 iterations
- [x] ≥ 5 unique sources → 10+ sources
- [x] Products/tools evaluated with pros/cons
- [x] Each finding has source/reference
