# Open Source Findings: Auth Core Features

## 1. TOTP / OTP Libraries

### 1.1 dev.samstevens.totp
- **Repo**: https://github.com/samstevens/java-totp
- **License**: MIT
- **Stars**: ~800+
- **Status**: Actively maintained (2025)
- **Score**: ⭐⭐⭐⭐⭐ (5/5)

**Capabilities**:
- Secret generation (Base32 encoded)
- QR Code rendering (SVG/PNG)
- TOTP verification with configurable time-step (default 30s)
- Clock drift tolerance (configurable window)
- Spring Boot starter available

**Gap Analysis**: Không hỗ trợ OTP SMS/Email natively — cần tự build OTP generation + storage (Redis) + delivery (event-driven).

### 1.2 com.warrenstrange:googleauth
- **Repo**: https://github.com/wstrange/GoogleAuth
- **License**: BSD-2
- **Status**: Stable but infrequent updates
- **Score**: ⭐⭐⭐⭐ (4/5)

**Capabilities**:
- RFC 6238 compliant TOTP
- Simple API: `GoogleAuthenticator.authorize(secret, code)`
- Scratch codes (backup/recovery)

**Gap Analysis**: Không có QR generation built-in; ít cập nhật.

### 1.3 aerogear-otp (JBoss)
- **Status**: ❌ DEPRECATED — Archived
- **Score**: ⭐ (1/5)
- **Recommendation**: KHÔNG sử dụng.

### Recommendation: `dev.samstevens.totp`
Lý do: API hiện đại, active maintenance, QR support, Spring Boot starter.

---

## 2. Password Validation Libraries

### 2.1 Passay
- **Repo**: https://github.com/vt-middleware/passay
- **License**: Apache 2.0 / LGPL 3.0
- **Status**: Actively maintained
- **Score**: ⭐⭐⭐⭐⭐ (5/5)

**Capabilities**:
- `LengthRule`, `CharacterRule`, `CharacterCharacteristicsRule`
- `DictionaryRule` (banned password lists)
- `HistoryRule`, `SourceRule` (reference-based rules)
- Custom `Rule` interface for domain-specific logic
- Thread-safe `PasswordValidator` — có thể cache per domain
- Custom `MessageResolver` cho i18n

**Gap Analysis**: Không có built-in DB storage cho policy configs — cần tự xây `PasswordPolicyEntity` + factory pattern.

### Recommendation: `Passay`
Lý do: Industry standard, dynamic rule composition, thread-safe caching.

---

## 3. CAPTCHA Providers

### 3.1 Cloudflare Turnstile
- **Type**: Privacy-preserving, invisible challenge
- **Cost**: Free tier generous
- **Score**: ⭐⭐⭐⭐⭐ (5/5)
- **Endpoint**: `https://challenges.cloudflare.com/turnstile/v0/siteverify`

### 3.2 hCaptcha
- **Type**: Privacy-first, challenge-based
- **Cost**: Free for most uses
- **Score**: ⭐⭐⭐⭐ (4/5)
- **Endpoint**: `https://api.hcaptcha.com/siteverify`

### 3.3 Google reCAPTCHA v3
- **Type**: Score-based (invisible)
- **Cost**: Free up to 1M requests/month
- **Score**: ⭐⭐⭐⭐ (4/5)
- **Endpoint**: `https://www.google.com/recaptcha/api/siteverify`

### Recommendation: Pluggable Adapter Pattern
Sử dụng `CaptchaVerifier` interface + adapter per provider. Default: Cloudflare Turnstile (privacy + free). Cấu hình qua `application.yml`.

---

## 4. OAuth2/SSO Libraries

### 4.1 Spring Security OAuth2 (Native)
- **Module**: `spring-boot-starter-oauth2-resource-server` + `spring-boot-starter-oauth2-client`
- **Score**: ⭐⭐⭐⭐⭐ (5/5)

**Capabilities**:
- Auto JWKS fetching từ Keycloak issuer-uri
- `JwtAuthenticationConverter` cho claim mapping
- `OidcUserService` cho JIT provisioning (OAuth2 Login flow)
- Full Spring Security integration

### 4.2 Keycloak Admin Client
- **Module**: `org.keycloak:keycloak-admin-client`
- **Score**: ⭐⭐⭐ (3/5 — Optional)

**Capabilities**:
- Programmatic realm/user management
- Event listener SPI cho Kafka integration

**Gap Analysis**: Chỉ cần nếu system-admin-service cần quản lý Keycloak realm. Cho use case hiện tại (optional downstream), không bắt buộc.

### Recommendation: Spring Security Native OAuth2
Không dùng Keycloak adapter (deprecated từ Keycloak 21+). Dùng Spring Security native với `issuer-uri` config.

---

## 5. Scoring Matrix Summary

| Library/Tool | Maturity | Activity | Fit | Security | Score |
|---|---|---|---|---|---|
| `dev.samstevens.totp` | 5 | 4 | 5 | 5 | **4.75** |
| Passay | 5 | 4 | 5 | 5 | **4.75** |
| Cloudflare Turnstile | 5 | 5 | 4 | 5 | **4.75** |
| Spring OAuth2 Native | 5 | 5 | 5 | 5 | **5.00** |
| JJWT (already in use) | 5 | 5 | 5 | 5 | **5.00** |
