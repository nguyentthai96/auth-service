## Context

Auth-service backend đã implement đầy đủ auth flow (LoginHandler, JWT RS256, MFA, SSO, password policy — 30 tasks trong `auth-core-features`). Frontend admindashboard hiện sử dụng mock API cho toàn bộ authentication. Change này kết nối frontend với backend thật, đồng thời bổ sung rate limiting, CAPTCHA, session management chưa có.

See `proposal.md` — Why for full motivation.

### Current Architecture Constraints
- Backend: CQRS pattern (CommandHandler), Clean Architecture (port/adapter), stateless JWT (SessionCreationPolicy.STATELESS)
- Frontend: React 19, Ky HTTP client, FuseTheme (MUI-based)
- Existing patterns: `MfaRateLimitService` (Redis INCR), `CaptchaVerifier` interface (pluggable), `SecurityProperties` (type-safe config)

## Goals / Non-Goals

**Goals:**
- Production-grade login security cho admin dashboard
- Zero external dependency cho CAPTCHA (self-hosted ALTCHA)
- Reuse existing patterns (MfaRateLimitService, CaptchaVerifier) — minimize new code
- Configurable session policy per role
- Transparent UX cho token renewal (sliding window)

**Non-Goals:**
- Geo-IP lookup (Phase 2 — chỉ store raw IP ở phase này)
- Push notification cho new device login (Phase 2)
- Admin UI page cho session management (Phase 4 — chỉ API ở phase này)
- OAuth2 social login (separate feature)
- Password reset flow changes

## Decisions

### D1: Rate Limiting — Redis INCR over Bucket4j

**Choice**: Reuse `MfaRateLimitService` Redis INCR pattern
**Why**: Already battle-tested in production (MFA flow), no new dependency, simpler architecture
**Alternative**: Bucket4j — token bucket algorithm, more flexible burst handling, but adds dependency and complexity
**Trade-off**: Redis INCR is simpler but less flexible for burst scenarios. Acceptable for login endpoint where strict per-window counting is preferred

**Implementation**:
- Extend `RateLimitType` enum: add `LOGIN_IP("LOGIN_IP")`, `LOGIN_USER("LOGIN_USER")`, `LOGIN_DEVICE("LOGIN_DEVICE")`
- New `LoginRateLimitService` following same pattern as `MfaRateLimitService`
- New `LoginRateLimitFilter` (OncePerRequestFilter) applied before handler
- Redis keys: `rate:login:ip:{ip}`, `rate:login:user:{username}`, `rate:login:device:{fp}`
- Config: extend `SecurityProperties` with `LoginRateLimitProperties`

### D2: CAPTCHA — ALTCHA Proof-of-Work (self-hosted)

**Choice**: ALTCHA library (`altcha-lib-java`, MIT license)
**Why**: Zero cost, zero external dependency, invisible UX (browser solves PoW in background via Web Crypto API), MIT license
**Alternative**: reCAPTCHA v3 (Google, free tier has limits), hCaptcha (privacy-friendly but external), Turnstile (already have adapter but external)
**Trade-off**: PoW CAPTCHA is less effective than AI-based CAPTCHA (reCAPTCHA) against sophisticated bots. Acceptable for admin dashboard (limited user base)

**Implementation**:
- New `AltchaCaptchaVerifier` implements `CaptchaVerifier` interface — verify HMAC + PoW solution
- Extend `CaptchaConfig` factory: add `"altcha"` case
- New `CaptchaController` — `GET /api/captcha/challenge`
- Replay protection: Caffeine cache (already in project) for used challenge salts, TTL 5 min
- Frontend: ALTCHA widget component — `GET /api/captcha/challenge` → solve PoW → send Base64 payload

### D3: Token Storage — In-memory + HttpOnly Cookie

**Choice**: Access token in JavaScript variable (closure/React state), refresh token in HttpOnly cookie
**Why**: XSS cannot steal access token (short-lived, in-memory), XSS cannot access refresh token (HttpOnly)
**Alternative**: localStorage (current — vulnerable to XSS), sessionStorage (no cross-tab)
**Trade-off**: Page refresh loses access token → need silent refresh (< 200ms). Acceptable with skeleton UI

**Implementation**:
- Backend: `CqrsAuthController.login()` → set `Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth`
- Backend: `CqrsAuthController.refresh()` → extract refresh token from cookie (not request body)
- Frontend: `JwtAuthProvider` stores access token in React ref/state (not localStorage)
- Frontend: Ky instance with `credentials: 'include'` to auto-send cookies

### D4: Sliding Token Renewal — Ky Interceptor + visibilitychange

**Choice**: Hybrid approach — API interceptor (check before each request) + visibilitychange event (tab focus recovery)
**Why**: Covers both active-use and tab-switch scenarios. Ky supports `beforeRequest` hooks natively
**Alternative**: setTimeout-based timer — simpler but doesn't handle tab inactivity well

**Implementation**:
- Ky `beforeRequest` hook: decode JWT, check `exp - now < 120s` → trigger refresh
- `visibilitychange` event: when tab becomes visible, check token validity
- Concurrent lock: Promise-based — first caller refreshes, others await same promise
- Absolute ceiling: check `iat + 36000s` (10h) — if exceeded, force logout

### D5: Session Policy — YAML Config + LoginSessionService

**Choice**: Configuration via `application.yml` with per-role overrides
**Why**: No DB change needed for policy, DevOps can adjust without deploy (if externalized)
**Alternative**: Database-backed policy (PolicyEntity) — more flexible but overkill for initial version

**Implementation**:
- New `SessionProperties` in `SecurityProperties`: `maxSessions`, `maxDevices`, `onExceed`, `roleOverrides` map
- New `SessionPolicyService`: enforce policy on login, 3 strategies (REVOKE_OLDEST, REJECT_NEW, REVOKE_ALL)
- New `LoginSessionEntity` extends `SnowflakePersistentAuditableEntity` — table `login_sessions`
- User-Agent parsing: regex-based (no external library) for device/browser/OS

### D6: Component Mapping

| Component | Package | Base Class | Dependencies |
|-----------|---------|------------|-------------|
| LoginRateLimitFilter | `auth/adapter/in/web/filter` | OncePerRequestFilter | LoginRateLimitService |
| LoginRateLimitService | `auth/application` | N/A (@Service) | StringRedisTemplate, SecurityProperties, AuditLogService |
| AltchaCaptchaVerifier | `auth/application` | CaptchaVerifier (interface) | SecurityProperties, Caffeine Cache |
| CaptchaController | `auth/adapter/in/web` | N/A (@RestController) | AltchaCaptchaVerifier |
| SessionPolicyService | `auth/application` | N/A (@Service) | LoginSessionRepository, SecurityProperties, RevokeSessionsHandler |
| LoginSessionService | `auth/application` | N/A (@Service) | LoginSessionRepository, ApplicationEventPublisher |
| LoginSessionEntity | `auth/adapter/out/persistence/entity` | SnowflakePersistentAuditableEntity | N/A |
| LoginSessionRepository | `auth/adapter/out/persistence/repository` | JpaRepository | N/A |
| RateLimitExceededException | `shared/exception` | AuthException | AuthErrorCode |
| SessionLimitExceededException | `shared/exception` | AuthException | AuthErrorCode |

## Risks / Trade-offs

**[R1]** ALTCHA PoW difficulty too low → bots solve easily
→ Mitigation: Configurable difficulty (`app.security.captcha.altcha.difficulty`), default 50,000 iterations. Monitor and adjust

**[R2]** Page refresh UX — access token lost, user sees flash
→ Mitigation: Skeleton UI during silent refresh (< 200ms P95). Frontend shows loading state, not blank page

**[R3]** CORS misconfiguration — cookies not sent cross-origin
→ Mitigation: E2E test CORS config before production. Document exact origin setup in deployment guide

**[R4]** MfaRateLimitService RateLimitType enum modification — affects existing MFA flow
→ Mitigation: Only ADD new enum values, never modify existing. `getConfig()` extended with `when` default branch

**[R5]** SecurityProperties expansion — 8 existing callers
→ Mitigation: Only ADD new nested classes (LoginRateLimitProperties, SessionProperties, AltchaProperties). Zero modification to existing fields. Backward-compatible

## Open Questions

- Q5: ALTCHA difficulty level (50,000 vs 100,000 SHA-256 iterations) — deferrable, default 50,000 works for most devices
- Q6: Absolute ceiling per environment (dev: 24h, prod: 10h) — deferrable, can use profile-specific YAML
