# Comparison Analysis: Auth Core Features

## 1. MFA Strategy Comparison

| Criteria | Manual Two-Phase (Spring Boot 3.x) | Native MFA (Spring Security 7) |
|---|---|---|
| **Spring Boot Compatibility** | ✅ 3.2+ | ❌ Requires 4.x+ |
| **Implementation Effort** | MEDIUM (custom filter + service) | LOW (declarative annotation) |
| **Flexibility** | HIGH (full control over flow) | MEDIUM (framework-managed) |
| **Maintenance** | Owner responsibility | Framework-managed |
| **Current Fit** | ✅ **Recommended** | ⚠️ Future migration target |

### Recommendation: Manual Two-Phase Login
Lý do: Project dùng Spring Boot 3.2, chưa thể dùng Spring Security 7 native MFA. Design API theo two-phase pattern cho phép dễ dàng migrate sau.

---

## 2. TOTP Library Comparison

| Criteria | dev.samstevens.totp | GoogleAuth | Custom (RFC 6238) |
|---|---|---|---|
| **Active Maintenance** | ✅ Yes | ⚠️ Infrequent | N/A |
| **QR Code Support** | ✅ Built-in | ❌ Manual | ❌ Manual |
| **Spring Boot Starter** | ✅ Available | ❌ No | ❌ No |
| **API Quality** | ✅ Modern | ⚠️ Legacy | ❌ Error-prone |
| **Security** | ✅ Battle-tested | ✅ Battle-tested | ⚠️ Risk |

### Recommendation: `dev.samstevens.totp`

---

## 3. CAPTCHA Provider Comparison

| Criteria | Cloudflare Turnstile | hCaptcha | reCAPTCHA v3 |
|---|---|---|---|
| **Privacy** | ✅ Privacy-first | ✅ Privacy-first | ⚠️ Google tracking |
| **User Experience** | ✅ Invisible | ⚠️ Challenge-based | ✅ Score-based |
| **Free Tier** | ✅ Generous | ✅ Free | ✅ 1M/month |
| **API Simplicity** | ✅ Simple | ✅ Simple | ✅ Simple |
| **ERP Context** | ✅ Best fit | ✅ Good | ⚠️ Privacy concern |

### Recommendation: Pluggable interface, default Cloudflare Turnstile

---

## 4. JWT Signing Algorithm Comparison

| Criteria | HMAC-SHA256 (Current) | RS256 (Target) |
|---|---|---|
| **Key Management** | ⚠️ Shared secret | ✅ Asymmetric (pub/priv) |
| **Multi-service** | ❌ All services need secret | ✅ Only auth has private key |
| **Key Rotation** | ❌ Risky | ✅ JWKS rotation |
| **Standards** | ✅ RFC 7515 | ✅ RFC 7515 |
| **Performance** | ✅ Faster | ⚠️ Slightly slower (acceptable) |
| **Security Level** | ⚠️ Medium | ✅ High |

### Recommendation: Migrate to RS256
JJWT library (đã dùng) hỗ trợ RS256 natively — chỉ cần đổi key type.

---

## 5. Password Validation Comparison

| Criteria | Custom Logic (Current) | Passay Library |
|---|---|---|
| **Rule Flexibility** | ❌ Hardcoded | ✅ Dynamic, composable |
| **Per-Domain Config** | ❌ Global only | ✅ Factory pattern per domain |
| **i18n Messages** | ❌ Manual | ✅ MessageResolver |
| **Password History** | ❌ Not implemented | ✅ HistoryRule + SourceRule |
| **Maintenance** | ❌ Owner | ✅ Community maintained |

### Recommendation: Adopt Passay

---

## 6. Feature Gap Analysis Summary

| Feature | Current State | Gap | Solution |
|---|---|---|---|
| MFA (TOTP) | ❌ Missing | Full implementation needed | `dev.samstevens.totp` + two-phase login |
| MFA (OTP SMS/Email) | ❌ Missing | OTP generation + Redis + delivery | Custom OtpService + adapter pattern |
| CAPTCHA | ❌ Missing | Server-side verification | Pluggable `CaptchaVerifier` interface |
| SSO/OAuth2 | ⚠️ Stub only | Full OAuth2 flow + JIT provisioning | Spring Security OAuth2 native |
| JWT RS256 | ❌ HMAC only | Key pair generation + JWKS endpoint | JJWT RS256 + `/jwks` endpoint |
| Token Introspection | ❌ Missing | Endpoint + jti validation | New `/api/auth/introspect` endpoint |
| Session Binding | ⚠️ Partial (jti blacklist) | Redis session record + `sid` claim | Redis-backed SessionRegistry |
| Password Complexity | ⚠️ Basic (length only) | Dynamic rules per domain | Passay + `PasswordPolicyEntity` |
| Password History | ❌ Missing | History table + validation | `password_history` table + Passay HistoryRule |
| Password Expiry | ❌ Missing | Expiry check on login | `password_changed_at` field + policy config |

---

## 7. Overall Recommendation

**Approach**: BUILD (không buy, không adopt external IdP) — tận dụng codebase hiện có, thêm libraries open source cho MFA/validation.

**Dependency Additions** (build.gradle.kts):
```kotlin
// MFA - TOTP
implementation("dev.samstevens.totp:totp:1.7.1")
// Password Validation
implementation("org.passay:passay:1.6.4")
// OAuth2 SSO (optional, khi enable SSO)
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
// Redis (for OTP, session, rate limiting)
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```
