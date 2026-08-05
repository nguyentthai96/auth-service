# Proposal: Auth Core Features

## 1. Tổng quan

Mở rộng `auth-service` với 4 nhóm tính năng xác thực lõi cho hệ thống ERP IAM:
1. **Multi-Factor Authentication (MFA)**: OTP SMS/Email, TOTP Authenticator, CAPTCHA, Trusted Device
2. **SSO/OAuth2 Integration**: Google, Microsoft, Keycloak với JIT user provisioning
3. **JWT RS256 Migration**: Chuyển từ HMAC-SHA256 sang RSA asymmetric signing + JWKS endpoint
4. **Dynamic Password Policy**: Per-domain complexity rules, password history, expiry

## 2. Phạm vi thay đổi

| Nhóm | Files Modified | Files New | Endpoints New | DB Tables New |
|------|---------------|-----------|--------------|--------------|
| MFA | 4 (AuthService, AuthController, UserEntity, SecurityConfig) | 5 (MfaService, OtpService, TotpService, CaptchaVerifier, MfaController) | 5 | 0 |
| SSO | 2 (SsoAdapter, SecurityConfig) | 3 (SsoController, UserIdentityEntity, SsoDtos) | 4 | 1 (user_identities) |
| RS256 | 3 (JwtService, JwtAuthFilter, SecurityConfig) | 1 (TokenController) | 3 | 0 |
| Password | 2 (PasswordPolicyService, AuthController) | 2 (PasswordPolicyEntity, PasswordHistoryEntity) | 2 | 2 (password_policies, password_history) |
| Shared | 4 (SecurityProperties, AuthExceptions, GlobalExceptionHandler, build.gradle.kts) | 3 (V2 migration, LoginResult, AuthRepositories) | 0 | 0 |
| **Total** | **13** | **16** | **14** | **3** |

## 3. Approach

**Layered Extension (Bottom-Up)** — từ brainstorm analysis:

```
Phase 1: Infrastructure (non-breaking)
  ├── Dependencies (build.gradle.kts)
  ├── V2 Flyway migration
  ├── New entities + repositories
  ├── SecurityProperties extend
  └── JwtService RS256 (with HMAC fallback)

Phase 2: Domain Services
  ├── PasswordPolicyService (Passay)
  ├── OtpService + TotpService + MfaService
  ├── CaptchaVerifier interface + adapter
  └── SsoAdapter full implementation

Phase 3: API Layer
  ├── MfaController, SsoController, TokenController
  ├── AuthController modifications
  ├── SecurityConfig public endpoints
  └── AuthExceptions + GlobalExceptionHandler
```

## 4. Dependencies mới

| Library | Version | Purpose |
|---------|---------|---------|
| `spring-boot-starter-data-redis` | managed | OTP storage, session binding |
| `spring-boot-starter-oauth2-client` | managed | SSO OAuth2 flow |
| `spring-boot-starter-oauth2-resource-server` | managed | JWKS, JWT RS256 decoding |
| `dev.samstevens.totp:totp` | 1.7.1 | TOTP generation/verification |
| `org.passay:passay` | 1.6.4 | Password validation rules |

## 5. Rủi ro & Giảm thiểu

| # | Risk | Impact | Mitigation |
|---|------|--------|------------|
| R1 | RS256 migration invalidates existing tokens | 🟡 | Refresh-based migration — HMAC fallback 7 ngày |
| R2 | Redis dependency — single point of failure | 🟡 | TOTP stateless fallback, Redis Sentinel cho HA |
| R3 | TOTP secret encryption key management | 🟡 | AES-256-GCM, key via env var `TOTP_ENCRYPTION_KEY` |
| R4 | OAuth2 IdP downtime | 🟢 | SSO users có thể set password local, timeout 10s |

## 6. Ước lượng effort

| Phase | Tasks | Complexity | Sprint |
|-------|-------|-----------|--------|
| Phase 1: Infrastructure | 8 | MEDIUM | Sprint 1 |
| Phase 2: Services | 7 | HIGH | Sprint 1-2 |
| Phase 3: API | 7 | MEDIUM | Sprint 2 |
| Phase 4: Testing | 5 | MEDIUM | Sprint 2 |
| **Total** | **27** | | **2 sprints** |

## 7. FR Traceability

| FR | Component | Status |
|-----|-----------|--------|
| FR-001 | OtpService + MfaService | Planned |
| FR-002 | TotpService + MfaService | Planned |
| FR-003 | MfaController + MfaService | Planned |
| FR-004 | CaptchaVerifier + AuthService | Planned |
| FR-005 | MfaService (trusted device cookie) | Planned |
| FR-006 | SsoAdapter + SsoController | Planned |
| FR-007 | SsoAdapter (JIT provisioning) | Planned |
| FR-008 | SsoController (link/unlink) | Planned |
| FR-009 | JwtService (RS256 migration) | Planned |
| FR-010 | TokenController (JWKS endpoint) | Planned |
| FR-011 | TokenController (introspection) | Planned |
| FR-012 | AuthService (force logout) | Planned |
| FR-013 | PasswordPolicyService (Passay) | Planned |
| FR-014 | PasswordPolicyService (history) | Planned |
| FR-015 | MfaService (idempotent verify) | Planned |
| FR-016 | AuditLogAspect (audit events) | Planned |
| FR-017 | SsoAdapter (timeout handling) | Planned |

17/17 FRs covered ✅
