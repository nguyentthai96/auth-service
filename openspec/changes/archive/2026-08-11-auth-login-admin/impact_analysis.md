# Impact Analysis: auth-login-admin

> Generated: 2026-08-11 | Type: EXTEND | Flow: Command | FRs: 22

---

## 1. Core Files (Affected)

### Backend (auth-service)

| File | Package | Action | FRs | Impact |
|------|---------|--------|-----|--------|
| [CqrsAuthController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) | `auth/adapter/in/web` | MODIFY | FR-002,004,016 | Cookie handling, deviceFingerprint field, refresh from cookie |
| [LoginHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | `auth/application/command` | MODIFY | FR-019,020 | Add session tracking + policy enforcement after login success |
| [SecurityConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | `shared/config` | MODIFY | FR-011,012,015 | CORS, HSTS, CSP headers, permit CAPTCHA endpoint |
| [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | `shared/config` | MODIFY | FR-006,018 | Add LoginRateLimitProperties + SessionProperties + AltchaCaptchaProperties |
| [CaptchaVerifier.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt) | `auth/application` | MODIFY | FR-008,021 | Add `"altcha"` case in CaptchaConfig factory |
| [AuthErrorCode.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt) | `shared/exception` | MODIFY | FR-006 | Add RATE_LIMITED, SESSION_LIMIT_EXCEEDED error codes |
| [RequestDtos.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt) | `auth/adapter/in/web/dto` | MODIFY | FR-008 | Add deviceFingerprint, captchaPayload fields to LoginRequestDto |
| [MfaRateLimitService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt) | `auth/application` | EXTEND (enum) | FR-006 | Add LOGIN_IP, LOGIN_USER, LOGIN_DEVICE to RateLimitType enum |
| [TokenController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt) | `auth/adapter/in/web` | MODIFY | FR-022 | Add session listing + revoke-by-session endpoints |
| application.yml | `resources` | MODIFY | FR-006,018,021 | Add login rate limit, session policy, ALTCHA config |

### Backend (NEW Files)

| File | Package | Action | FRs | Notes |
|------|---------|--------|-----|-------|
| LoginRateLimitFilter.kt | `auth/adapter/in/web/filter` | NEW | FR-006 | Servlet filter — checks multi-dimensional rate limits before handler |
| LoginRateLimitService.kt | `auth/application` | NEW | FR-006 | Extends MfaRateLimitService pattern — IP+username+device dimensions |
| AltchaCaptchaVerifier.kt | `auth/application` | NEW | FR-008,021 | Implements CaptchaVerifier — HMAC-SHA256 verify + replay check |
| CaptchaController.kt | `auth/adapter/in/web` | NEW | FR-021 | GET /api/captcha/challenge endpoint |
| SessionPolicyService.kt | `auth/application` | NEW | FR-018,020 | Enforce maxSessions/maxDevices per role |
| LoginSessionService.kt | `auth/application` | NEW | FR-019 | Track login history + device info |
| LoginSessionEntity.kt | `auth/adapter/out/persistence/entity` | NEW | FR-019 | JPA entity for login_sessions table |
| LoginSessionRepository.kt | `auth/adapter/out/persistence/repository` | NEW | FR-019 | Spring Data JPA repository |
| RateLimitExceededException.kt | `shared/exception` | NEW | FR-006 | HTTP 429 exception |
| SessionLimitExceededException.kt | `shared/exception` | NEW | FR-020 | HTTP 409 exception |

### Frontend (admindashboard)

| File | Path | Action | FRs |
|------|------|--------|-----|
| [authApi.ts](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/src/@auth/authApi.ts) | `src/@auth/` | MODIFY | FR-002,008,021,022 | mock → real API, add CAPTCHA + session APIs |
| [JwtAuthProvider.tsx](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/src/@auth/services/jwt/JwtAuthProvider.tsx) | `src/@auth/services/jwt/` | MODIFY | FR-003,004,005,014,016,017 | localStorage → in-memory, sliding renewal, cookie |
| [SignInPageForm.tsx](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/src/app/(public)/(auth)/components/forms/SignInPageForm.tsx) | `src/app/(public)/(auth)/` | MODIFY | FR-001 | email → username field |
| [JwtSignInTab.tsx](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/src/app/(public)/(auth)/components/tabs/sign-in/JwtSignInTab.tsx) | `src/app/(public)/(auth)/` | MODIFY | FR-007,008,009,010 | Error states + ALTCHA widget |
| [api.ts](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/frontends/admindashboard/src/utils/api.ts) | `src/utils/` | MODIFY | FR-012,015,017 | credentials include, CSRF, device fingerprint |

---

## 2. Call Tree (Key Paths)

### Login Flow (Current)
```
Frontend SignInPageForm → authSignIn() → POST /api/auth/login
  → CqrsAuthController.login() → LoginCommand
    → LoginHandler.handle()
      → userPort.findByUsernameAndActive()
      → captchaGateway.verify() → CaptchaGatewayAdapter → CaptchaVerifier.verify()
      → tokenGenerator.matchesPassword()
      → tokenGenerator.generateAuthResponse()
    → ResponseEntity.ok(LoginResult.Success)
```

### Login Flow (After Change)
```
Frontend SignInPageForm → authSignIn() → POST /api/auth/login
  → LoginRateLimitFilter.doFilterInternal() [NEW — IP/username/device check]
    → LoginRateLimitService.checkMultiDimensional() [NEW]
  → CqrsAuthController.login()
    → LoginHandler.handle()
      → (existing auth flow)
      → SessionPolicyService.enforcePolicy() [NEW — check maxSessions]
      → LoginSessionService.recordLogin() [NEW — save to login_sessions]
    → Set refresh token HttpOnly cookie [NEW]
    → ResponseEntity.ok(LoginResult.Success — without refreshToken in body)
```

### Token Refresh Flow (After Change)
```
Frontend Ky interceptor (sliding renewal) [MODIFIED]
  → POST /api/auth/refresh (cookie auto-sent)
  → CqrsAuthController.refresh() [MODIFIED — extract from cookie]
    → RefreshTokenHandler.handle()
    → LoginSessionService.updateLastActivity() [NEW]
  → Set new refresh cookie + return access token
```

---

## 3. Blast Radius

### SecurityProperties.kt — 🟡 Medium (d=1: 8 callers)
Direct dependents: SecurityConfig, LoginHandler, MfaRateLimitService, AuthService, MfaService, OtpService, PasswordPolicyService, TurnstileCaptchaVerifier
**Action**: Backward-compatible — ADD new nested classes, no modify existing

### CaptchaVerifier.kt — 🟢 Low (d=1: 3 callers)
Direct dependents: AuthService, CaptchaGatewayAdapter, CaptchaConfig (factory)
**Action**: EXTEND — add `"altcha"` case in factory `when` block. Existing callers unaffected

### MfaRateLimitService.kt — 🟡 Medium (d=1: 2 callers + RateLimitType enum used by 4)
Direct dependents of service: MfaController, LoginHandler
Direct dependents of RateLimitType enum: MfaRateLimitService, RateLimitAdminController, getLockInfo(), adminUnlock()
**Action**: EXTEND RateLimitType enum — add LOGIN_IP, LOGIN_USER, LOGIN_DEVICE. getConfig() needs extension. Existing values stable

### LoginHandler.kt — 🟢 Low (d=1: 1 caller)
Direct caller: CqrsAuthController.login()
**Action**: MODIFY — inject SessionPolicyService + LoginSessionService. Add session tracking after successful auth

### CqrsAuthController.kt — 🟢 Low (d=1: external callers only)
No internal callers — REST endpoint
**Action**: MODIFY — add HttpServletResponse param for cookie, modify refresh to extract from cookie

### AuthErrorCode.kt — 🟡 Medium (d=1: 5+ callers via GlobalExceptionHandler)
All exception classes use this enum
**Action**: EXTEND — add 2 new error codes at end. No existing codes modified

---

## 4. Reuse Map

| Existing Component | Reuse For | Level | Match % | Notes |
|-------------------|-----------|-------|---------|-------|
| `MfaRateLimitService` | LoginRateLimitService | PATTERN REUSE | 85% | Same Redis INCR + EXPIRE pattern. Parameterize: key prefix, config source |
| `RateLimitType` enum | Login rate limit types | EXTEND | 100% | Add 3 new enum values |
| `CaptchaVerifier` interface | AltchaCaptchaVerifier | EXTEND | 100% | Implement `verify(token): Boolean` |
| `CaptchaConfig` factory | ALTCHA bean creation | EXTEND | 90% | Add `"altcha"` case in when block |
| `VersionedAuditableEntity` / `SnowflakePersistentAuditableEntity` | LoginSessionEntity base | DIRECT REUSE | 100% | Extend for login_sessions table |
| `RevokeSessionsHandler` | Session revocation on policy exceed | DIRECT REUSE | 100% | Call `tokenStore.revokeAllForUser()` |
| `AuditLogService` | Login event logging | DIRECT REUSE | 100% | Log login events, session revocations |
| `RateLimitExceededEvent` | Login rate limit events | PATTERN REUSE | 90% | Same event structure, different type |
| `TokenGenerator` | Token generation | DIRECT REUSE | 100% | No changes needed |
| `LoginResult` sealed class | Login response | DIRECT REUSE | 100% | No changes needed |
| `GlobalExceptionHandler` | Rate limit exception handling | EXTEND | 95% | Add handler for RateLimitExceededException |
| `JwtAuthFilter` | Auth filter chain | DIRECT REUSE | 100% | No changes needed |

### EXTRACT Candidates

| Logic | Source | Destination | Match % | Decision |
|-------|--------|-------------|---------|----------|
| Redis INCR + EXPIRE + lock check | MfaRateLimitService.checkAndIncrement() | LoginRateLimitService | 85% | **PATTERN REUSE** — don't extract, create new service following same pattern. MFA and Login have different key structures and config sources |
| CaptchaConfig factory when block | CaptchaVerifier.kt L60-63 | Same file | 90% | **EXTEND** — add `"altcha"` case |

> No EXTRACT needed — all reuse is either DIRECT REUSE or PATTERN REUSE. No shared logic needs extraction into common.

---

## 5. Context Snapshot

### Current State
- Backend: 118 lines CqrsAuthController, 95 lines LoginHandler, 233 lines MfaRateLimitService, 70 lines SecurityProperties, 69 lines CaptchaVerifier, 55 lines SecurityConfig
- Frontend: 88 lines authApi.ts (all mock endpoints), JwtAuthProvider uses localStorage
- Config: `app.security.*` in application.yml — JWT, password, MFA, captcha, SSO sections
- Error codes: AUTH_001 → AUTH_019 defined
- Entities: UserEntity extends SnowflakePersistentAuditableEntity (Snowflake ID, audit fields)

### After Change (Estimated)
- Backend: +7 new files (~600-800 lines new code), ~200 lines modified across 10 existing files
- Frontend: ~400-500 lines modified across 5 files
- Config: +3 new config sections (login rate limit, session policy, ALTCHA)
- Error codes: +2 new (AUTH_020 RATE_LIMITED, AUTH_021 SESSION_LIMIT_EXCEEDED)
- Database: +1 new table (`login_sessions`)
- Dependencies: +1 Maven (`altcha-lib-java`)

### Database Schema (NEW)

```sql
CREATE TABLE login_sessions (
    id                  BIGINT PRIMARY KEY,     -- Snowflake ID
    user_id             BIGINT NOT NULL,
    refresh_token_id    BIGINT,                 -- FK to refresh_tokens (nullable)
    ip_address          VARCHAR(45) NOT NULL,   -- IPv4/IPv6
    user_agent          VARCHAR(500),
    device_fingerprint  VARCHAR(64),            -- SHA-256 hash
    device_type         VARCHAR(20),            -- DESKTOP/MOBILE/TABLET
    browser_name        VARCHAR(50),
    os_name             VARCHAR(50),
    geo_country         VARCHAR(3),             -- ISO 3166-1 alpha-3 (Phase 2)
    is_active           BOOLEAN DEFAULT TRUE,
    is_new_device       BOOLEAN DEFAULT FALSE,
    login_at            TIMESTAMP NOT NULL,
    last_activity_at    TIMESTAMP,
    revoked_at          TIMESTAMP,
    revoke_reason       VARCHAR(50),            -- POLICY_EXCEED/MANUAL/LOGOUT/EXPIRED
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,

    CONSTRAINT fk_login_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_login_sessions_user_active ON login_sessions(user_id, is_active);
CREATE INDEX idx_login_sessions_device ON login_sessions(device_fingerprint);
CREATE INDEX idx_login_sessions_ip ON login_sessions(ip_address);
```
