---
type: brainstorm_notes
change: auth-login-admin
date: 2026-08-11
selected_direction: "Hybrid Self-hosted PoW CAPTCHA + Multi-dimensional Rate Limiting + Sliding Token + Configurable Session Policy"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Auth Login Admin — Deep Thinking

## Date
2026-08-11

## Context
User đặt 4 câu hỏi từ `pre_openspec.md` Open Questions:
1. CAPTCHA: Muốn **tự xây** (self-hosted), không dùng dịch vụ bên ngoài → tránh chi phí + phụ thuộc
2. Rate limit: Đồng ý 5 req/min per IP, nhưng muốn thêm **browser, device, login history, monitoring**
3. Token TTL: Đồng ý 15 min, nhưng muốn **tự gia hạn khi sử dụng** (sliding window)
4. Session: Muốn **cấu hình custom** — single/multi session tùy loại user, multi-device support

## Questions Asked & Answers

### Q1: CAPTCHA Provider
- **Asked**: reCAPTCHA v3 hay hCaptcha?
- **Answer**: Không dùng cả hai. Muốn **self-hosted**, **tự xây**, không phụ thuộc dịch vụ bên ngoài, tránh phí và instability
- **Implication**: Cần research open-source self-hosted CAPTCHA alternatives → loại bỏ reCAPTCHA/hCaptcha khỏi scope

### Q2: Rate Limit Threshold
- **Asked**: 5 req/min per IP phù hợp?
- **Answer**: Đồng ý 5 req/min per IP + muốn thêm **browser tracking, device fingerprint, login history, access monitoring/surveillance**
- **Implication**: Mở rộng scope đáng kể — từ simple IP rate limit → multi-dimensional security monitoring system

### Q3: Access Token TTL
- **Asked**: Giảm 30 min → 15 min?
- **Answer**: Đồng ý 15 min + muốn **tự gia hạn khi sử dụng** — convenient nhất cho user
- **Implication**: Sliding window pattern — renew token on activity, không bắt user re-login khi đang active

### Q4: Session Management (New Question from User)
- **Asked**: (Implicit) Single session?
- **Answer**: Muốn **custom configuration** — single/multi session **tùy loại user**, multi-device support
- **Implication**: Cần SessionPolicy entity/config — per user type/role khác nhau

---

## Approach Analysis

### Approach 1: Self-hosted CAPTCHA (ALTCHA Proof-of-Work)

#### So sánh 3 lựa chọn

```
┌───────────────────────────────────────────────────────────┐
│                   CAPTCHA OPTIONS MATRIX                  │
├──────────────┬─────────────┬───────────┬─────────────────┤
│              │  reCAPTCHA  │  hCaptcha  │  ALTCHA (PoW)  │
│              │  v3         │           │  Self-hosted    │
├──────────────┼─────────────┼───────────┼─────────────────┤
│ Chi phí      │ Free→$     │ Free→$    │ ✅ Free (MIT)   │
│ Privacy      │ ❌ Google   │ ⚠️ Track  │ ✅ Zero tracking│
│ Self-host    │ ❌ Cloud    │ ❌ Cloud  │ ✅ Full control │
│ Dependency   │ ❌ Google   │ ❌ HC API │ ✅ Local crypto │
│ UX           │ Invisible   │ Widget    │ ✅ Invisible    │
│ GDPR         │ ⚠️ Complex │ ⚠️ Need   │ ✅ Compliant    │
│ Java SDK     │ ✅ Official │ ✅ Offic  │ ✅ altcha-java  │
│ React widget │ ✅ npm      │ ✅ npm    │ ✅ npm/vanilla  │
│ Stability    │ ⚠️ Google   │ ⚠️ HC    │ ✅ Self-managed │
│ Bot detect   │ ✅ Strong   │ ✅ Good   │ ⚠️ PoW only    │
└──────────────┴─────────────┴───────────┴─────────────────┘
```

#### Selected: ALTCHA (Proof-of-Work)

**Lý do chọn:**
1. **Self-hosted** — chạy hoàn toàn local, KHÔNG gọi external API
2. **Zero cost** — MIT license, free forever
3. **Zero dependency** — verification = crypto math (HMAC-SHA256), không cần internet
4. **Privacy-first** — không tracking, không cookie, GDPR compliant
5. **Extensible** — có thể tăng difficulty khi cần, add Sentinel layer sau
6. **Invisible UX** — user không thấy CAPTCHA, browser tự giải PoW trong background

**Architecture:**

```
                    ALTCHA Proof-of-Work Flow
                    ═══════════════════════

    ┌──────────┐     ①GET /api/captcha/challenge
    │ Frontend │───────────────────────────────────▶┌──────────┐
    │ (React)  │                                    │ Backend  │
    │          │◀───────────────────────────────────│ (Spring) │
    │          │     {algorithm, challenge,          │          │
    │          │      salt, signature, maxnumber}    │          │
    │          │                                    │          │
    │  ┌───────────┐  ②Browser solves PoW           │          │
    │  │Web Crypto │  (find X where                 │          │
    │  │  API      │   SHA256(salt+X)==challenge)    │          │
    │  └───────────┘                                │          │
    │          │                                    │          │
    │          │     ③POST /api/auth/login           │          │
    │          │     {username, pwd, captchaPayload} │          │
    │          │───────────────────────────────────▶│          │
    │          │                                    │  ┌───────┤
    │          │                                    │  │Verify │
    │          │                                    │  │HMAC + │
    │          │                                    │  │replay │
    │          │                                    │  │check  │
    │          │     ④Success / Error               │  └───────┤
    │          │◀───────────────────────────────────│          │
    └──────────┘                                    └──────────┘
```

**Code mapping với CaptchaVerifier interface hiện tại:**

```kotlin
// Hiện tại:
interface CaptchaVerifier {
    fun verify(token: String): Boolean  // ← chỉ nhận token string
}

// Approach: Override verify() — decode Base64 payload, verify HMAC + replay
class AltchaCaptchaVerifier(
    private val hmacKey: String,
    private val replayCache: Cache<String, Boolean>  // Caffeine cache
) : CaptchaVerifier {
    override fun verify(token: String): Boolean {
        val payload = Base64.decode(token) // {algorithm, challenge, number, salt, signature}
        // 1. Verify HMAC signature
        // 2. Check replay (salt đã dùng chưa?)
        // 3. Verify PoW: SHA256(salt + number) == challenge
        return valid
    }
}
```

**⚠️ Trade-off**: PoW chỉ chống automated bot, KHÔNG phân biệt human vs bot sophisticated. Nếu cần advanced bot detection (AI-powered) → add Sentinel layer hoặc WAF sau.

**✅ Fit với codebase hiện tại**: `CaptchaVerifier` interface + `CaptchaConfig` factory → chỉ cần thêm `"altcha"` case trong `when(provider)`.

---

### Approach 2: Multi-dimensional Rate Limiting + Login Monitoring

#### Architecture

```
                Multi-dimensional Rate Limiting
                ═════════════════════════════

    Request ──▶ ┌─────────────────────────────────────┐
                │         LoginRateLimitFilter          │
                │                                       │
                │  ① Per-IP Rate Limit                  │
                │     key: rate:login:ip:{ip}           │
                │     limit: 5 req/min                  │
                │                                       │
                │  ② Per-Username Rate Limit             │
                │     key: rate:login:user:{username}   │
                │     limit: 3 failed/15min             │
                │     (chỉ count failed attempts)       │
                │                                       │
                │  ③ Per-Device Fingerprint              │
                │     key: rate:login:device:{fp}       │
                │     limit: 10 req/hour                │
                │     (detect credential stuffing)      │
                │                                       │
                └──────────────┬────────────────────────┘
                               │ All checks pass
                               ▼
                ┌─────────────────────────────────────┐
                │         LoginHandler (CQRS)          │
                │  • BCrypt verify                     │
                │  • Account lock check                │
                │  • CAPTCHA check                     │
                │  • MFA checkpoint                    │
                └──────────────┬────────────────────────┘
                               │ Login success
                               ▼
                ┌─────────────────────────────────────┐
                │      LoginSessionTracker             │
                │  (Post-login recording)              │
                │                                       │
                │  • Save to login_sessions table       │
                │  • Record: userId, ip, userAgent,    │
                │    deviceFingerprint, geoLocation,   │
                │    loginAt, sessionId                 │
                │  • Check: New device? → emit event   │
                │  • Check: Session policy → enforce   │
                └───────────────────────────────────────┘
```

#### Database Schema (NEW)

```sql
-- Login history and device tracking
CREATE TABLE login_sessions (
    id              BIGINT PRIMARY KEY,        -- Snowflake ID
    user_id         BIGINT NOT NULL REFERENCES users(id),
    refresh_token_id BIGINT REFERENCES refresh_tokens(id),
    ip_address      VARCHAR(45) NOT NULL,      -- IPv4/IPv6
    user_agent      TEXT,
    device_fingerprint VARCHAR(64),            -- SHA-256 hash from frontend
    device_type     VARCHAR(20),               -- DESKTOP, MOBILE, TABLET
    browser_name    VARCHAR(50),               -- Chrome, Firefox, Safari
    os_name         VARCHAR(50),               -- Windows, macOS, Linux, iOS, Android
    geo_country     VARCHAR(2),                -- ISO 3166-1 alpha-2
    geo_city        VARCHAR(100),
    is_new_device   BOOLEAN DEFAULT FALSE,
    is_active       BOOLEAN DEFAULT TRUE,      -- FALSE = logged out / revoked
    login_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMPTZ,
    logout_at       TIMESTAMPTZ,
    logout_reason   VARCHAR(20),               -- MANUAL, TOKEN_EXPIRED, REVOKED, POLICY
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_login_sessions_user_id ON login_sessions(user_id);
CREATE INDEX idx_login_sessions_device_fp ON login_sessions(device_fingerprint);
CREATE INDEX idx_login_sessions_active ON login_sessions(user_id, is_active) WHERE is_active = TRUE;
```

#### Redis Key Schema (Extended)

```
# Rate limiting keys
rate:login:ip:{clientIp}          → counter, TTL 60s (5 req/min)
rate:login:user:{username}        → counter, TTL 900s (3 failed/15min)
rate:login:device:{fingerprint}   → counter, TTL 3600s (10 req/hour)

# Active session tracking
session:active:{userId}           → SET of sessionIds (for policy enforcement)
session:device:{userId}:{deviceFp} → sessionId (for device tracking)
```

#### Reuse Analysis

| Component | Reuse | Action |
|-----------|-------|--------|
| `MfaRateLimitService` | ✅ PATTERN | Extract generic `RateLimitService` từ pattern Redis INCR |
| `AuditLogService` | ✅ REUSE | Đã có — dùng cho login events |
| `RateLimitExceededEvent` | ✅ REUSE | Đã có — event publishing |
| `LoginSessionEntity` | ❌ NEW | New entity + repository |
| `LoginSessionService` | ❌ NEW | Track login history |
| `DeviceDetector` | ❌ NEW | Parse User-Agent → device info |

**✅ Decision**: Dùng **Redis INCR pattern** (reuse từ `MfaRateLimitService`) thay vì Bucket4j. Lý do:
1. Pattern đã proven trong production (MFA rate limiting)
2. Không thêm dependency mới
3. Atomic + fail-open strategy đã implement
4. Extend `RateLimitType` enum → thêm `LOGIN_IP`, `LOGIN_USER`, `LOGIN_DEVICE`

---

### Approach 3: Sliding Token Renewal

#### Architecture

```
            Sliding Window Token Renewal
            ═════════════════════════════

    Timeline:  0min ────── 7min ────── 12min ─── 14min ─── 15min
                 │          │           │         │         │
    Token:    [NEW]        │         [RENEW]      │      [EXPIRE if idle]
                           │           │         │
    Activity:  ✅ API call  ✅ API      ✅ API    ...
                           │           │
    Token EXP: 15min →     │    →      15min from now (reset)
                           │
                    Check: exp - now < THRESHOLD (2min)?
                           │
                    YES → POST /api/auth/refresh (silent)
                           │
                    NO → Continue using current token
```

#### Implementation Approaches

| Approach | Mechanism | Pros | Cons |
|----------|-----------|------|------|
| **A: Frontend Timer** | setInterval check exp, refresh at < 2min | Simple, predictable | Timer runs even when idle |
| **B: API Interceptor** | Ky interceptor checks exp before each request | Only refreshes on activity | Slight latency on first stale request |
| **C: Backend Activity** | Backend extends token on each request | True sliding window | Stateful, breaks JWT purity |
| **D: Hybrid (A+B)** | Interceptor + idle timeout | Best UX, efficient | Slightly more complex |

#### Selected: **D. Hybrid (Interceptor + Idle Detection)**

```
    ┌──────────────────────────────────────────────┐
    │              Frontend Token Manager            │
    │                                                │
    │  Ky API Interceptor:                           │
    │  ┌────────────────────────────────────────────┐│
    │  │ beforeRequest(request) {                   ││
    │  │   if (tokenExpiresSoon(accessToken, 120s)) ││
    │  │     await refreshToken(); // silent        ││
    │  │   request.headers.Authorization = ...      ││
    │  │ }                                          ││
    │  └────────────────────────────────────────────┘│
    │                                                │
    │  Visibility Change Listener:                   │
    │  ┌────────────────────────────────────────────┐│
    │  │ document.addEventListener('visibilitychange'│
    │  │   if (visible && tokenExpired(accessToken))││
    │  │     await refreshToken(); // on tab focus  ││
    │  │ )                                          ││
    │  └────────────────────────────────────────────┘│
    │                                                │
    │  Absolute Ceiling: 10 hours                    │
    │  (force re-login regardless of activity)       │
    └──────────────────────────────────────────────────┘
```

**Key Design Decisions:**
1. **Threshold = 2 min** before expiry → trigger silent refresh
2. **Concurrent lock** — only 1 refresh request at a time (Promise lock)
3. **Tab focus recovery** — refresh on `visibilitychange` nếu token expired
4. **Absolute ceiling** = 10 hours → JWT claim `iat` + 10h → force logout
5. **Idle timeout** = refresh token TTL (7 days) → nếu idle > 7 ngày → re-login

---

### Approach 4: Configurable Session Policy

#### Architecture

```
    Session Policy Configuration
    ═════════════════════════════

    ┌──────────────┐
    │ Domain Config │ ─── app.security.session.defaultPolicy: MULTI
    └──────┬───────┘
           │
    ┌──────▼───────────────────────────────────────────┐
    │                SessionPolicy (DB)                 │
    │                                                   │
    │  Per-Role Override:                               │
    │  ┌───────────┬────────────┬───────────┐          │
    │  │ Role      │ maxSession │ maxDevice │          │
    │  ├───────────┼────────────┼───────────┤          │
    │  │ SUPER_ADM │     1      │     1     │ ← Single│
    │  │ ADMIN     │     3      │     3     │ ← Multi │
    │  │ OPERATOR  │     1      │     2     │ ← Mixed │
    │  │ VIEWER    │     5      │     5     │ ← Liberal│
    │  │ (default) │     3      │     3     │          │
    │  └───────────┴────────────┴───────────┘          │
    └──────────────────────────────────────────────────┘

    Login Flow:
    ┌──────────────────────────────────────────────────┐
    │ LoginHandler                                      │
    │   │                                               │
    │   ├─ Authenticate (existing)                      │
    │   │                                               │
    │   ├─ SessionPolicyService.enforce(user, device)   │
    │   │   │                                           │
    │   │   ├─ Count active sessions for user           │
    │   │   ├─ Get policy for user's role               │
    │   │   │                                           │
    │   │   ├─ IF activeSessions >= maxSessions          │
    │   │   │   ├─ policy.onExceed == REVOKE_OLDEST     │
    │   │   │   │   → Revoke oldest session              │
    │   │   │   ├─ policy.onExceed == REJECT_NEW         │
    │   │   │   │   → Throw MaxSessionsExceededException │
    │   │   │   └─ policy.onExceed == REVOKE_ALL         │
    │   │   │       → Revoke all existing sessions       │
    │   │   │                                           │
    │   │   └─ Allow login                              │
    │   │                                               │
    │   └─ Create new session + token                   │
    └──────────────────────────────────────────────────┘
```

#### Session Policy Configuration Model

```kotlin
// NEW: SecurityProperties extension
data class SessionProperties(
    val defaultPolicy: SessionPolicyConfig = SessionPolicyConfig(),
    val roleOverrides: Map<String, SessionPolicyConfig> = emptyMap()
) {
    data class SessionPolicyConfig(
        val maxSessions: Int = 3,           // Max concurrent sessions
        val maxDevices: Int = 3,            // Max unique devices
        val onExceed: ExceedPolicy = ExceedPolicy.REVOKE_OLDEST,
        val allowMultiDevice: Boolean = true,
        val trackLoginHistory: Boolean = true,
        val newDeviceNotification: Boolean = true
    )

    enum class ExceedPolicy {
        REVOKE_OLDEST,     // Netflix model — revoke oldest session
        REJECT_NEW,        // Bank model — block new login
        REVOKE_ALL         // Paranoid model — force logout everywhere
    }
}
```

#### Configuration Example (application.yml)

```yaml
app:
  security:
    session:
      default-policy:
        max-sessions: 3
        max-devices: 3
        on-exceed: REVOKE_OLDEST
        allow-multi-device: true
        track-login-history: true
        new-device-notification: true
      role-overrides:
        SUPER_ADMIN:
          max-sessions: 1
          max-devices: 1
          on-exceed: REVOKE_ALL
        ADMIN:
          max-sessions: 3
          max-devices: 3
          on-exceed: REVOKE_OLDEST
        VIEWER:
          max-sessions: 5
          max-devices: 5
```

---

## Selected Direction

Tổng hợp 4 approaches thành **unified design direction**:

```
    ┌────────────────────────────────────────────────────────┐
    │              SELECTED DIRECTION SUMMARY                │
    ├────────────────────────────────────────────────────────┤
    │                                                        │
    │  1. CAPTCHA: ALTCHA (PoW) — self-hosted               │
    │     • altcha-lib-java cho backend verification         │
    │     • altcha-widget cho frontend (React)               │
    │     • CaptchaVerifier interface → AltchaCaptchaVerifier│
    │     • Replay protection via Caffeine cache             │
    │                                                        │
    │  2. RATE LIMIT: Redis INCR multi-dimensional           │
    │     • Per-IP: 5 req/min                                │
    │     • Per-Username: 3 failed/15min                     │
    │     • Per-Device: 10 req/hour                          │
    │     • Pattern: reuse MfaRateLimitService               │
    │     • NEW: login_sessions table for history            │
    │                                                        │
    │  3. TOKEN: Sliding window via API interceptor           │
    │     • Access TTL: 15 min (sliding via refresh)         │
    │     • Refresh on activity (Ky interceptor)             │
    │     • Tab focus recovery (visibilitychange)            │
    │     • Absolute ceiling: 10 hours                       │
    │     • Concurrent refresh lock (Promise)                │
    │                                                        │
    │  4. SESSION: Configurable per-role policy               │
    │     • SecurityProperties.SessionProperties (config)    │
    │     • Per-role: maxSessions, maxDevices, onExceed      │
    │     • Strategies: REVOKE_OLDEST, REJECT_NEW, REVOKE_ALL│
    │     • login_sessions tracking (DB)                     │
    │     • New device notification (event-driven)           │
    │                                                        │
    └────────────────────────────────────────────────────────┘
```

## Pre-classifications (preliminary)
- Feature type: **EXTEND** (confirmed — mở rộng LoginHandler + CqrsAuthController + JwtAuthProvider)
- Flow type: **Command** (single step: submit credentials → token)
- Affected modules:
  - **auth-service**: LoginHandler, CqrsAuthController, SecurityConfig, SecurityProperties, JwtService
  - **auth-service (NEW)**: AltchaCaptchaVerifier, LoginRateLimitService, LoginSessionService, SessionPolicyService, LoginSessionEntity
  - **admindashboard**: authApi.ts, JwtAuthProvider.tsx, SignInPageForm.tsx, JwtSignInTab.tsx, api.ts

## Codebase Findings

### Reuse Opportunities

| Existing Component | Reuse For | Level |
|-------------------|-----------|-------|
| `CaptchaVerifier` interface | ALTCHA adapter | ✅ EXTEND (add case) |
| `CaptchaConfig` factory | ALTCHA bean creation | ✅ EXTEND (add case) |
| `MfaRateLimitService` pattern | Login rate limiting | ✅ PATTERN REUSE |
| `RateLimitType` enum | Login rate limit types | ✅ EXTEND |
| `AuditLogService` | Login event logging | ✅ DIRECT REUSE |
| `RateLimitExceededEvent` | Login rate limit events | ✅ PATTERN REUSE |
| `TokenGenerator` | Token creation + rotation | ✅ DIRECT REUSE |
| `RevokeSessionsHandler` | Session revocation | ✅ DIRECT REUSE |
| `RefreshTokenRepository` | Token management | ✅ DIRECT REUSE |
| `LoginResult` sealed class | Login response | ✅ DIRECT REUSE |

### Key Constraints
- Architecture: STATELESS (`SessionCreationPolicy.STATELESS`) — session tracking qua DB/Redis, không dùng HttpSession
- Security: RS256 JWT signing (đã migrate từ HMAC)
- Database: PostgreSQL + Snowflake IDs + Flyway migrations
- Cache: Redis (StringRedisTemplate) + Caffeine (in-memory)

## Open Questions for Design Phase

- [RESOLVED] Q1: CAPTCHA provider → ALTCHA (Proof-of-Work, self-hosted)
- [RESOLVED] Q2: Rate limit threshold → 5 req/min per IP + per-username + per-device
- [RESOLVED] Q3: Token TTL → 15 min sliding window + 10h absolute ceiling
- [RESOLVED] Q4: Session policy → Configurable per-role (maxSessions, maxDevices, onExceed)
- [OPEN] Q5: ALTCHA difficulty level? Default SHA-256 iterations = 50,000-100,000 (adjust based on device capability)
- [OPEN] Q6: Absolute session ceiling (10 hours) — configurable per environment?
- [OPEN] Q7: New device notification channel — email? in-app? push? (Phase 2 consideration)
- [OPEN] Q8: Geo-IP library — MaxMind GeoLite2 (free) hoặc ip-api.com? (Phase 2 consideration)
- [OPEN] Q9: Admin dashboard UI cho login session management — cần page mới? (Phase 2 consideration)

## Open Questions for URD Analysis
- Không cần — source là user idea + interactive brainstorm, đủ clarity cho implementation
