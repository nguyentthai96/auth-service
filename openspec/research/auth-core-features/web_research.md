# Web Research: Auth Core Features

## Iteration 1: MFA Implementation Patterns

### Source 1: Spring Security 7 — Native MFA
- **URL**: spring.io/blog (Spring Security 7 docs)
- **Key Findings**:
  - `@EnableMultiFactorAuthentication` — khai báo các Factor authorities cần thiết
  - `FactorGrantedAuthority.PASSWORD_AUTHORITY` + `FactorGrantedAuthority.OTT_AUTHORITY`
  - Không cần custom filter/redirect — framework quản lý state tự động
- **Applicability**: ⚠️ Yêu cầu Spring Security 7+ (Spring Boot 4+). Hiện tại project dùng Spring Boot 3.2. → Cần đánh giá migration path hoặc dùng manual approach.

### Source 2: Manual MFA Flow (Spring Boot 3.x)
- **Key Pattern**: Two-phase login
  1. Phase 1: Username/Password → trả "half-authenticated" response (chứa `mfaToken`)
  2. Phase 2: `/api/auth/mfa/verify` + `mfaToken` + `otpCode` → trả full JWT
- **Rate Limiting**: Bucket4j hoặc Redis INCR cho max 3 attempts per mfaToken
- **OTP Storage**: Redis với TTL 5 phút (KEY: `otp:{userId}:{channel}`)

### Source 3: TOTP Best Practices
- **Secret Storage**: Encrypted at rest trong DB (AES-256)
- **QR Code**: Server-side generation, otpauth:// URI format
- **Recovery**: 8 backup codes (one-time use), stored hashed

---

## Iteration 2: SSO & OAuth2 Patterns

### Source 4: Spring Boot 3 + Keycloak Integration (Modern Approach)
- **URL**: dev.to, baeldung.com
- **Key Findings**:
  - ❌ Deprecated: Keycloak Spring Boot adapter
  - ✅ Modern: `spring-boot-starter-oauth2-resource-server` + `issuer-uri`
  - Auto JWKS fetching và key rotation support
  - JIT Provisioning: Custom `OidcUserService` → check DB → create if not exists
  - Identity Linking: Luôn dùng `sub` claim (KHÔNG dùng email — có thể đổi)

### Source 5: Event-Driven Provisioning
- **Pattern**: Keycloak Event Listener SPI → Kafka → account-service consumer
- **Applicability**: Phù hợp với kiến trúc hiện tại (đã có Kafka topic `iam.user.sso_provisioned`)

---

## Iteration 3: JWT RS256 & Session Binding

### Source 6: RS256 Migration Best Practices
- **URL**: ssojet.com, medium.com, securityboulevard.com
- **Key Findings**:
  - **JWKS Endpoint**: Expose `/.well-known/jwks.json` — resource servers fetch public key dynamically
  - **Algorithm Pinning**: Cấu hình `JwtDecoder` chỉ accept RS256 (chống algorithm switching attack)
  - **Key Rotation**: Hỗ trợ multiple kids trong JWKS — rotate key không downtime
  - **Private Key Storage**: Environment variable hoặc secrets manager (KHÔNG hardcode)

### Source 7: Session Binding Pattern
- **Pattern**: Hybrid Stateless + Stateful
  - Access token: Short-lived (5-15 min), lean claims
  - Refresh token: Long-lived, opaque, stored in DB/Redis
  - `jti` blacklist: Redis SET cho instant revocation
  - Session ID binding: Embed `sid` claim trong JWT → map to Redis session record

---

## Iteration 4: Password Policy

### Source 8: Passay Dynamic Rules
- **URL**: passay.org, baeldung.com
- **Key Findings**:
  - Factory Pattern: Build `List<Rule>` dynamically từ DB config per domain
  - Cache: `ConcurrentHashMap<domainId, PasswordValidator>` — thread-safe
  - History: `PasswordData.Reference` cho historical password checking
  - Custom Rules: Implement `Rule` interface cho domain-specific logic

### Source 9: Password History Table Design
- **Schema**: `password_history(id, user_id, password_hash, created_at)`
- **Logic**: Fetch last N hashes (N = domain policy `historyCount`), BCrypt.matches() each
- **Pruning**: DELETE old entries khi exceed historyCount

---

## Summary of Sources

| # | Source | Topic | Reliability |
|---|---|---|---|
| 1 | spring.io | Spring Security 7 MFA | ⭐⭐⭐⭐⭐ |
| 2 | Community patterns | Manual MFA flow | ⭐⭐⭐⭐ |
| 3 | dev.samstevens.totp docs | TOTP impl | ⭐⭐⭐⭐⭐ |
| 4 | dev.to / baeldung.com | Keycloak + Spring Boot 3 | ⭐⭐⭐⭐⭐ |
| 5 | reddit.com | Event-driven provisioning | ⭐⭐⭐⭐ |
| 6 | ssojet.com / medium.com | RS256 migration | ⭐⭐⭐⭐ |
| 7 | systemweakness.com / curity.io | Session binding | ⭐⭐⭐⭐⭐ |
| 8 | passay.org / baeldung.com | Passay dynamic rules | ⭐⭐⭐⭐⭐ |
| 9 | Community patterns | Password history schema | ⭐⭐⭐⭐ |
