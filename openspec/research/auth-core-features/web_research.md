# Kết quả nghiên cứu Internet: Auth Core Features

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — từ broad đến targeted.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | Auth Core Features (MFA, SSO/OAuth2, RS256 JWT, Password Policy) |
| **Ngày nghiên cứu** | 2026-08-19 |
| **Số iterations** | 4 |
| **Tổng sources** | 9 unique |
| **Keywords ban đầu** | `Spring Boot MFA TOTP`, `OAuth2 SSO Keycloak`, `JWT RS256 JWKS`, `Passay password policy` |
| **Keywords phát triển** | `two-phase login flow`, `JIT provisioning`, `algorithm pinning`, `session binding`, `password history table` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD

**Mục tiêu**: Phát hiện landscape, thu thập keywords, tìm top sources

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"Spring Boot 3 MFA TOTP implementation best practices"` | Spring Security 7 có native `@EnableMultiFactorAuthentication` — nhưng yêu cầu Spring Boot 4+ | `FactorGrantedAuthority`, `two-phase login`, `mfaToken` |
| 2 | `"Spring Security OAuth2 Keycloak JIT provisioning 2024"` | Keycloak adapter deprecated từ v21+, dùng Spring Security native | `OidcUserService`, `issuer-uri`, `JIT provisioning` |
| 3 | `"JWT RS256 vs HMAC comparison Spring Boot"` | RS256 asymmetric cho phép resource servers verify mà không cần shared secret | `algorithm pinning`, `JWKS rotation`, `kid` |

**Takeaways Iteration 1:**
- Spring Security 7 native MFA chưa available cho Spring Boot 3.x — cần manual two-phase approach
- Keycloak adapter deprecated — Spring Security OAuth2 native là hướng đi đúng
- RS256 migration có thể thực hiện với dual-key fallback (RS256 primary, HMAC legacy)

---

### Iteration 2 — DEEP DIVE

**Mục tiêu**: Đọc sâu top articles, extract approaches và trade-offs

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:-:|
| 1 | spring.io/blog | Spring Security 7 — Native MFA | `@EnableMultiFactorAuthentication`, `FactorGrantedAuthority.PASSWORD_AUTHORITY` + `FactorGrantedAuthority.OTT_AUTHORITY` — framework quản lý state tự động | 9 |
| 2 | baeldung.com | Spring Boot 3 + Keycloak Integration | Modern approach: `spring-boot-starter-oauth2-resource-server` + `issuer-uri`. Auto JWKS fetching. Custom `OidcUserService` cho JIT | 9 |
| 3 | ssojet.com + medium.com | RS256 Migration Best Practices | JWKS endpoint at `/.well-known/jwks.json`, algorithm pinning (chống switching attack), multiple `kid` for rotation | 8 |
| 4 | passay.org + baeldung.com | Passay Dynamic Rules | Factory pattern: build `List<Rule>` từ DB config, thread-safe `PasswordValidator` cache | 8 |
| 5 | curity.io + systemweakness.com | Session Binding Patterns | Hybrid stateless+stateful: short-lived access token + opaque refresh in DB/Redis + `sid` claim + `jti` blacklist | 8 |

**Takeaways Iteration 2:**
- **Two-Phase Login**: Phase 1 (credentials) → `mfaToken` JWT (5min, type=mfa). Phase 2 (verify OTP/TOTP + mfaToken) → full JWT. Redis-backed OTP with TTL.
- **JIT Provisioning**: Custom `OidcUserService` → check DB by `sub` claim → create if not exists → publish Kafka event
- **RS256 Key Management**: Private key in env var / secrets manager (NEVER hardcode), public key in JWKS endpoint
- **Algorithm Pinning**: Configure `JwtDecoder` to ONLY accept RS256 — prevent algorithm switching attacks
- **Conflicting info**: Có 2 camps: (a) Keycloak as primary IdP vs (b) auth-service as primary with Keycloak as optional downstream. Đã chọn (b) vì phù hợp với kiến trúc hiện tại.

---

### Iteration 3 — TARGETED (MFA + Session)

**Mục tiêu**: Fill gaps về OTP flow, trusted device, rate limiting

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"OTP Redis storage pattern TTL rate limiting"` (gap: OTP delivery) | Redis key `otp:{userId}:{channel}` với TTL 5 phút, atomic `INCR` cho attempts | ✅ |
| 2 | `"TOTP secret encryption at rest best practices"` (gap: secret storage) | AES-256-GCM encryption, IV prepended to ciphertext, Base64 storage | ✅ |
| 3 | `"trusted device cookie MFA skip pattern"` (gap: UX optimization) | SHA-256 hash of device fingerprint, stored on user entity, cookie-based (30 days) | ✅ |
| 4 | `"MFA rate limiting Bucket4j Redis"` (gap: abuse prevention) | Redis INCR with window, max 3 OTP attempts per mfaToken, max 5 OTP verify failures in 15 min | ✅ |

---

### Iteration 4 — TARGETED (Password + Token)

**Mục tiêu**: Password history schema, token introspection endpoint details

| # | Query (nguồn gốc) | Kết quả | Gap filled? |
|---|-------------------|---------|:-:|
| 1 | `"password history table design PostgreSQL"` (gap: schema) | `password_history(id, user_id, password_hash, created_at)`. Fetch last N hashes, BCrypt.matches() each. Prune old entries > historyCount. | ✅ |
| 2 | `"token introspection RFC 7662 Spring Boot"` (gap: endpoint spec) | POST `/introspect` with `token` param, return `{active, sub, roles, exp, iat}`. Resource server validates without parsing JWT locally. | ✅ |
| 3 | `"password expiry check on login force change"` (gap: UX flow) | Check `password_changed_at` + `maxAgeDays` on login. If expired → return `PASSWORD_EXPIRED` error before issuing tokens. | ✅ |

**Stop reason**: All questions answered — tất cả gaps đã fill, consistent findings across sources.

---

## 3. Bài viết/Nguồn quan trọng

| # | Loại | Title | URL | Key Insights | Relevance | Pros | Cons |
|---|------|-------|-----|-------------|:-:|------|------|
| 1 | Docs | Spring Security 7 — Native MFA | spring.io/blog | `@EnableMultiFactorAuthentication` — declarative MFA | 9 | Framework-managed state | Requires Spring Boot 4+ |
| 2 | Docs | dev.samstevens.totp Documentation | Maven Central / README | QR code URI, secret gen, verification | 9 | Modern API, Spring starter | GitHub repo 404 |
| 3 | Tutorial | Spring Boot 3 + Keycloak Modern Approach | baeldung.com, dev.to | `issuer-uri` config, auto JWKS fetching | 9 | Step-by-step, verified | Keycloak-specific examples |
| 4 | Article | RS256 JWT Migration Best Practices | ssojet.com, medium.com | JWKS endpoint, algorithm pinning, key rotation | 8 | Security-focused | Generic, not Spring-specific |
| 5 | Blog | Session Binding Patterns | curity.io, systemweakness.com | Hybrid stateless+stateful, `sid` claim, `jti` blacklist | 8 | Enterprise-grade patterns | Complex implementation |
| 6 | Docs | Passay Documentation | passay.org | Dynamic rules, MessageResolver, HistoryRule | 8 | Official, comprehensive | No per-domain examples |
| 7 | Tutorial | Passay Spring Boot Integration | baeldung.com | Bean validation, custom Rule interface | 8 | Practical code | Basic examples only |
| 8 | Community | Event-Driven SSO Provisioning | reddit.com | Keycloak Event Listener SPI → Kafka pattern | 7 | Real-world experience | Anecdotal |
| 9 | RFC | Token Introspection RFC 7662 | tools.ietf.org | Standard endpoint spec, response format | 9 | IETF standard | Abstract, no impl guide |

---

## 4. Đánh giá sản phẩm/Công cụ

### Bảng đánh giá tổng quan

| Sản phẩm/Công cụ | Cách giải quyết bài toán | Tính năng chính | Lợi ích | Thuận lợi | Bất lợi | Gap |
|-------------------|--------------------------|----------------|---------|-----------|---------|-----|
| Cloudflare Turnstile | Privacy-preserving invisible challenge | Bot detection, no user friction | Free, privacy-first | Invisible, simple API, generous free tier | Dependent on Cloudflare | None for ERP use case |
| hCaptcha | Privacy-first challenge-based CAPTCHA | Challenge puzzles, bot detection | Privacy, free | GDPR compliant, accessible | Challenge-based = user friction | UX friction |
| Google reCAPTCHA v3 | Score-based invisible assessment | Risk scoring per request | Widely adopted, free 1M/month | Score-based, invisible | Google tracking, privacy concern | Privacy for ERP |

### So sánh tính năng chi tiết

| Feature | Cloudflare Turnstile | hCaptcha | reCAPTCHA v3 | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|
| Invisible (no user friction) | ✅ | ❌ | ✅ | ⭐ Must |
| Privacy-first | ✅ | ✅ | ❌ | ⭐ Must |
| Free tier | ✅ | ✅ | ✅ | ⭐ Must |
| Simple server-side API | ✅ | ✅ | ✅ | ⭐ Must |
| ERP/Enterprise suitable | ✅ | ✅ | ⚠️ | ⭐ Must |
| Score-based risk | ❌ | ❌ | ✅ | Nice to have |
| Self-hosted option | ❌ | ❌ | ❌ | Optional |

---

## 5. Patterns & Approaches

| # | Approach | Mô tả | Ưu điểm | Nhược điểm | Phù hợp khi | Source |
|---|---------|--------|---------|------------|------------|-------|
| 1 | Two-Phase Login (Manual MFA) | Phase 1: credentials → mfaToken. Phase 2: OTP/TOTP + mfaToken → full JWT | Full control, works on Spring Boot 3.x | More code to maintain | Spring Boot < 4.x, custom requirements | spring.io, community patterns |
| 2 | Native MFA (Spring Security 7) | `@EnableMultiFactorAuthentication` — framework manages MFA state | Minimal code, declarative | Requires Spring Boot 4+ | Future migration target | spring.io |
| 3 | Dual-Key JWT Migration | RS256 primary signing, HMAC fallback for 7-day migration | Zero downtime migration | Temporary complexity | Migrating from symmetric to asymmetric | ssojet.com, medium.com |
| 4 | Pluggable CAPTCHA | Interface + adapter per provider, config-driven | Swap provider without code change | Abstraction overhead | Multi-environment (dev=noop, prod=turnstile) | Community best practice |
| 5 | Domain-scoped Password Policy | DB-backed policy entity per domain + Passay factory | Dynamic, per-tenant | Cache invalidation complexity | Multi-tenant SaaS / multi-domain | passay.org, baeldung.com |
| 6 | Hybrid Session (Stateless+Stateful) | Short-lived JWT + Redis-backed refresh + `jti` blacklist | Best of both worlds | Redis dependency | Enterprise with revocation needs | curity.io |

---

## 6. Câu hỏi chưa trả lời

| # | Câu hỏi | Đã tìm kiếm? | Lý do chưa trả lời được | Ảnh hưởng |
|---|---------|:-:|--------------------------|-----------|
| 1 | SMS provider cụ thể nào sẽ dùng cho OTP delivery? | ✅ | Out of scope — adapter pattern cho phép plug bất kỳ provider | LOW — không ảnh hưởng architecture |
| 2 | Keycloak realm configuration chi tiết? | ✅ | Optional downstream — chỉ cần khi enable SSO với Keycloak | LOW — config-only, không ảnh hưởng code |
| 3 | `dev.samstevens.totp` GitHub repo 404 — long-term maintenance? | ✅ | Package vẫn trên Maven Central 1.7.1 — TOTP là stable protocol, ít thay đổi | MEDIUM — monitor, có thể fork nếu cần |

---

> **Sources**: Tất cả URLs đã verify tại thời điểm 2026-08-19
> **Next step**: Comparison Analysis (comparison_analysis.md)
