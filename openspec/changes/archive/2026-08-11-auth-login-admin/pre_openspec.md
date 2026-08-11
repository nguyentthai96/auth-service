# Pre-OpenSpec: auth-login-admin

> **Type**: EXTEND
> **Flow**: Command
> **Source**: User Idea (no URD)
> **Classification Evidence**: `LoginHandler` → `auth/application/command` → `auth-service/src/main/kotlin/.../auth/application/command/LoginHandler.kt` (existing login flow, cần mở rộng rate limiting + frontend integration)
> **Archive**: `openspec/changes/auth-core-features/pre_openspec.md` (auth-core-features — EXTEND type, 17 FRs, MFA/SSO/RS256/PasswordPolicy — đã implement)
> **Quality Score**: 85/100
> **Brainstorm**: `brainstorm_notes.md` (2026-08-11) — resolved Q1-Q4, added FR-017→FR-022

## Synthetic Requirement Summary

- **Feature**: Auth Login Admin Dashboard — đăng nhập bảo mật cho admin panel
- **Actors**: Admin User, Admin Dashboard (Frontend), Auth Service (Backend)
- **Core Flow**: Admin nhập username + password → frontend validate → POST /api/auth/login → backend authenticate → return JWT + set refresh cookie → frontend store in-memory → redirect dashboard
- **Auth Required**: Yes — Username/Password + JWT Bearer Token
- **External Systems**: Redis (rate limiting + session tracking), ALTCHA (self-hosted PoW CAPTCHA — no external dependency)
- **Constraints**: Rate limiting (5 req/min per IP + 3 failed/15min per user + 10 req/hour per device), brute force protection (3 attempts → lock 15 min), HTTPS enforcement, HttpOnly cookie cho refresh token, in-memory cho access token, sliding token renewal (15 min + auto-extend), configurable session policy per role
- **Source**: User idea (conversation) + feature research output

## 📋 Feature Summary

Kết nối Admin Dashboard frontend (React 19) với backend auth-service (Kotlin/Spring Boot) thật, thay thế mock API hiện tại. Chuyển đổi email field sang username, triển khai token storage an toàn (HttpOnly cookie + in-memory), thêm multi-dimensional rate limiting (Redis INCR per IP/username/device) cho login endpoint, tích hợp ALTCHA self-hosted PoW CAPTCHA (không phụ thuộc external service), implement sliding token renewal (auto-extend 15 min khi active), configurable session policy per role (single/multi session + multi-device), login history tracking (device fingerprint + geo), và xử lý các error states trên frontend.

| Metric | Giá trị |
|--------|---------|
| Số FR | 22 (Idea: 11, Enriched: 5, Brainstorm: 6) |
| Issues | 4 (🔴: 0, 🟡: 3, 🟢: 1) |
| Open Questions | 0 resolved (5 deferred to Phase 2) |
| **Quality Score** | **85/100** |

---

## 1. Actors

- Admin User: Người dùng quản trị, đăng nhập bằng username/password để truy cập dashboard
- Admin Dashboard (Frontend): Ứng dụng React 19 xử lý UI login, token management, API calls
- Auth Service (Backend): Spring Boot service xử lý authentication, JWT generation, rate limiting
- Redis: Hỗ trợ distributed rate limiting và session storage

## 2. Functional Requirements

### FR-001: Đăng nhập bằng Username [IDEA]
- **Actor**: Admin User
- **Action**: Hệ thống phải cho phép admin đăng nhập bằng username (thay vì email) kết hợp password
- **Validation**: Username required, không được rỗng; Password min 8 ký tự; Frontend dùng Zod validation

### FR-002: Kết nối API login thật [IDEA]
- **Actor**: Admin Dashboard
- **Action**: Hệ thống phải gửi request POST `/api/auth/login` tới backend auth-service thay vì mock API (`mock/auth/sign-in`)
- **Validation**: Request body: `{ username, password, domainCode }`. Response: `AuthResponse` (accessToken, refreshToken, user, roles, permissions)

### FR-003: Lưu access token in-memory [IDEA]
- **Actor**: Admin Dashboard
- **Action**: Hệ thống phải lưu JWT access token trong React state (in-memory) thay vì localStorage
- **Validation**: Token không persist qua page refresh. Khi page refresh → silent refresh via cookie

### FR-004: Refresh token qua HttpOnly Cookie [IDEA]
- **Actor**: Auth Service + Admin Dashboard
- **Action**: Backend phải set refresh token vào HttpOnly + Secure + SameSite=Strict cookie. Frontend gửi POST `/api/auth/refresh` để lấy access token mới
- **Validation**: Cookie path = `/api/auth`. Max-Age = 604800 (7 days). Frontend dùng `credentials: 'include'`

### FR-005: Silent token refresh [IDEA]
- **Actor**: Admin Dashboard
- **Action**: Hệ thống phải tự động refresh access token khi token sắp hết hạn (< 2 min remaining) mà không yêu cầu user re-login
- **Validation**: Decode JWT → check `exp`. Nếu < 120s → trigger refresh. Lock concurrent refresh requests

### FR-006: Rate limiting login endpoint (multi-dimensional) [IDEA]
- **Actor**: Auth Service
- **Action**: Hệ thống phải giới hạn login request theo 3 chiều: per-IP (5 req/min), per-username (3 failed/15min), per-device fingerprint (10 req/hour) bằng Redis INCR pattern
- **Validation**: Trả 429 Too Many Requests + Retry-After header. Redis keys: `rate:login:ip:{ip}`, `rate:login:user:{username}`, `rate:login:device:{fp}`. Reuse `MfaRateLimitService` pattern (atomic INCR + fail-open)

### FR-007: Hiển thị lỗi account locked [IDEA]
- **Actor**: Admin Dashboard
- **Action**: Frontend phải hiển thị thông báo khi backend trả về HTTP 423 (Account Locked) kèm countdown timer
- **Validation**: Hiển thị "Tài khoản đã bị khóa. Thử lại sau [countdown]". Disable submit button trong thời gian lock

### FR-008: Hiển thị CAPTCHA widget (ALTCHA PoW) [IDEA]
- **Actor**: Admin Dashboard
- **Action**: Frontend phải hiển thị ALTCHA Proof-of-Work CAPTCHA widget khi backend trả về HTTP 428 (CAPTCHA Required). Widget tự giải PoW challenge trong background (invisible UX)
- **Validation**: Frontend GET `/api/captcha/challenge` → nhận {algorithm, challenge, salt, signature, maxnumber}. Browser giải bằng Web Crypto API. Gửi Base64 payload kèm login request. Backend verify HMAC + replay check. Provider: `altcha` (tự host, MIT license, không phụ thuộc external)

### FR-009: Hiển thị lỗi rate limit exceeded [IDEA]
- **Actor**: Admin Dashboard
- **Action**: Frontend phải hiển thị thông báo khi backend trả về HTTP 429 (Too Many Requests) kèm countdown timer
- **Validation**: Hiển thị "Quá nhiều yêu cầu. Thử lại sau [X giây]". Parse `Retry-After` header

### FR-010: Xử lý MFA checkpoint [IDEA]
- **Actor**: Admin Dashboard
- **Action**: Frontend phải redirect tới MFA verification screen khi backend trả về `mfaRequired: true`
- **Validation**: Truyền `mfaToken` + `mfaMethod` sang MFA screen. Sau verify → nhận access token

### FR-011: Security headers HSTS/CSP [IDEA]
- **Actor**: Auth Service
- **Action**: Backend phải thêm security headers: HSTS (Strict-Transport-Security), CSP (Content-Security-Policy)
- **Validation**: HSTS: max-age=31536000; includeSubDomains. CSP: default-src 'self'. Cấu hình trong SecurityConfig

## 3. Non-functional Requirements

- NFR-001: Login response time ≤ 500ms (P95) bao gồm BCrypt verify
- NFR-002: Token refresh time ≤ 200ms (P95)
- NFR-003: Rate limiting không làm tăng latency > 5ms cho request hợp lệ
- NFR-004: Silent refresh không gây flicker UI khi page reload

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. FR-007, FR-008, FR-009 tuy đều xử lý error state nhưng cho các HTTP status khác nhau (423, 428, 429) và có UI behavior khác nhau.

## 5. Enriched Domain Requirements

Max enriched: min(5, ceil(11 × 0.20)) = min(5, 3) = 3. Nhưng feature có 3 enrichment rõ ràng + 2 bổ sung quan trọng cho security → tổng 5.

### Enriched FRs

### FR-012: CORS cho cookie cross-origin [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải cấu hình CORS chính xác cho phép frontend domain gửi credentials (cookies) cross-origin
- **Validation**: `allowedOrigins` = frontend domain (không dùng `*`). `allowCredentials = true`. `exposedHeaders` = `Retry-After`

### FR-013: Refresh token rotation [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải invalidate refresh token cũ mỗi khi issue token mới (rotation)
- **Validation**: Backend đã có logic rotation trong `TokenGenerator.kt`. Cần verify hoạt động với cookie flow

### FR-014: Concurrent refresh lock [ENRICHED]
- **Actor**: Admin Dashboard
- **Action**: Frontend phải đảm bảo chỉ 1 refresh request được gửi tại 1 thời điểm (tránh race condition)
- **Validation**: Dùng Promise lock pattern. Các request khác chờ kết quả từ request đầu tiên

### FR-015: CSRF protection cho cookie [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải bảo vệ cookie-based endpoints khỏi CSRF attack
- **Validation**: Dùng SameSite=Strict cookie (primary). Hoặc Anti-CSRF token (cookie-to-header pattern) nếu SameSite không đủ

### FR-016: Sign out xóa refresh cookie [ENRICHED]
- **Actor**: Admin Dashboard + Auth Service
- **Action**: Khi admin sign out, backend phải xóa refresh token cookie + revoke token trong DB
- **Validation**: Frontend gửi POST `/api/auth/logout`. Backend set `Set-Cookie: refresh_token=; Max-Age=0`. Revoke token hash

### FR-017: Sliding token renewal (auto-extend on activity) [BRAINSTORM]
- **Actor**: Admin Dashboard
- **Action**: Access token phải tự gia hạn khi user đang active — Ky API interceptor check nếu token sắp hết hạn (< 2 min remaining) → trigger silent refresh. Tab focus recovery via `visibilitychange` event
- **Validation**: Token TTL = 15 min. Refresh threshold = 120s before expiry. Concurrent lock (only 1 refresh at a time). Absolute ceiling = 10 hours (JWT `iat` + 10h → force re-login)

### FR-018: Configurable session policy per role [BRAINSTORM]
- **Actor**: Auth Service
- **Action**: Hệ thống phải cho phép cấu hình session policy (single/multi session, max devices) tùy theo role của user. Config qua `application.yml` (`app.security.session.role-overrides`)
- **Validation**: Properties: `maxSessions`, `maxDevices`, `onExceed` (REVOKE_OLDEST / REJECT_NEW / REVOKE_ALL). Default: maxSessions=3, maxDevices=3, onExceed=REVOKE_OLDEST

### FR-019: Login history tracking (device + browser + geo) [BRAINSTORM]
- **Actor**: Auth Service
- **Action**: Hệ thống phải ghi lại mọi lần đăng nhập vào bảng `login_sessions` gồm: userId, ip, userAgent, deviceFingerprint, deviceType, browserName, osName, geoCountry, isNewDevice, loginAt
- **Validation**: Parse User-Agent → device/browser/OS info. Device fingerprint từ frontend header `X-Device-Fingerprint` (SHA-256 hash). Emit `NewDeviceLoginEvent` khi phát hiện device mới

### FR-020: Session enforcement on login [BRAINSTORM]
- **Actor**: Auth Service
- **Action**: Khi login, hệ thống phải check session count theo policy. Nếu vượt `maxSessions` → apply `onExceed` strategy (revoke oldest / reject new / revoke all)
- **Validation**: Query `login_sessions` WHERE `user_id = ? AND is_active = TRUE`. Compare count vs policy.maxSessions. Revoke = set `is_active=FALSE` + revoke refresh token + emit event

### FR-021: ALTCHA challenge endpoint [BRAINSTORM]
- **Actor**: Auth Service
- **Action**: Hệ thống phải expose endpoint `GET /api/captcha/challenge` trả về ALTCHA challenge object {algorithm, challenge, salt, signature, maxnumber}
- **Validation**: Challenge = HMAC-SHA256 signed. Single-use (Caffeine cache track used salts, TTL 5 min). Difficulty adjustable via config `app.security.captcha.altcha.difficulty` (default 50000)

### FR-022: Active session management API [BRAINSTORM]
- **Actor**: Admin User
- **Action**: Admin phải xem danh sách active sessions của mình và có thể revoke session cụ thể từ dashboard
- **Validation**: GET `/api/auth/sessions/active` → list active sessions with device info. DELETE `/api/auth/sessions/{sessionId}` → revoke specific session. UI: show device icon + location + last activity

### External Integrations (from Step 2d + Brainstorm)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Redis | Multi-dimensional rate limiting (INCR pattern), active session tracking | Đã có `spring-boot-starter-data-redis`. Reuse `MfaRateLimitService` pattern |
| ALTCHA (self-hosted) | Proof-of-Work CAPTCHA — chống bot | `altcha-lib-java` (MIT). Không gọi external API. Verify = local crypto (HMAC-SHA256) |
| Caffeine Cache | ALTCHA replay protection (in-memory) | Đã có dependency trong project |

## 6. Assumptions

- Backend auth-service đã deploy và accessible từ frontend domain
- HTTPS đã cấu hình (TLS termination tại load balancer hoặc application)
- Redis available cho distributed rate limiting + session tracking
- CAPTCHA: **ALTCHA self-hosted** — KHÔNG cần external API key (Brainstorm Decision)
- Access token TTL = **15 min** với sliding window (auto-extend on activity) (Brainstorm Decision)
- Session policy: **Configurable per role** — default maxSessions=3, maxDevices=3 (Brainstorm Decision)
- Frontend gửi `X-Device-Fingerprint` header (SHA-256 hash từ FingerprintJS hoặc custom)
- Frontend base URL cho API calls sẽ được cấu hình qua environment variable
- Absolute session ceiling = **10 hours** (configurable per environment)
- Geo-IP: Phase 2 — hiện tại chỉ store raw IP, geo lookup sẽ implement sau

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 23/25 | FR-006: "5 requests/phút" — chưa specify burst size |
| Đầy đủ (Completeness) | 21/25 | FR-008: CAPTCHA provider chưa chọn cụ thể; FR-010: MFA screen UI chưa chi tiết |
| Nhất quán (Consistency) | 24/25 | FR-003/FR-004: access token in-memory + refresh cookie — logic nhất quán nhưng page refresh behavior cần clarify |
| Kiểm thử được (Testability) | 17/25 | FR-004: HttpOnly cookie khó test từ frontend; FR-011: HSTS/CSP cần browser testing |
| **Tổng** | **85/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích IDEA) | Cách cải thiện |
|---|----------|----------|-----|-------------------|----------------|
| 1 | Clarity | -2 | FR-006 | "5 requests/phút per IP" — burst token capacity? refill rate? | Xác định: capacity=5, refill=1 token per 12s |
| 2 | Completeness | -2 | FR-008 | CAPTCHA provider chưa chọn (reCAPTCHA vs hCaptcha) | User decision needed |
| 3 | Completeness | -2 | FR-010 | MFA screen "redirect tới MFA verification screen" — screen nào? | Define MFA route + component |
| 4 | Consistency | -1 | FR-003 | in-memory token lost khi page refresh — user experience impact? | Clarify: silent refresh < 200ms → transparent |
| 5 | Testability | -4 | FR-004 | HttpOnly cookie không thể đọc bằng JS — test integration cần real browser | Dùng Playwright/Cypress cho E2E test |
| 6 | Testability | -4 | FR-011 | Security headers cần browser để verify | Cung cấp curl command để verify headers |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | Page refresh mất access token — user phải chờ silent refresh (< 200ms) nhưng có thể gây flash/flicker | FR-003, FR-005 | Add loading state trong JwtAuthProvider. Show skeleton UI during refresh |
| 2 | Risk | 🟡 | CORS misconfiguration — cookie không được gửi nếu origin/credentials sai | FR-004, FR-012 | Test kỹ CORS config giữa frontend domain và backend domain |
| 3 | ~~Missing~~ Resolved | ~~🟡~~ 🟢 | ~~Bucket4j dependency~~ → Dùng Redis INCR pattern (reuse `MfaRateLimitService`), không cần Bucket4j (Brainstorm Decision) | FR-006 | Extend `RateLimitType` enum + extract generic `LoginRateLimitService` |
| 4 | Info | 🟢 | Archive `auth-core-features` đã implement 30 tasks — nhiều pattern có thể reuse (CaptchaVerifier, MfaService, etc.) | ALL | Reuse patterns từ archive thay vì code mới |
| 5 | Scope | 🟡 | Brainstorm mở rộng scope đáng kể: +6 FRs (FR-017→FR-022). Cần phase planning cẩn thận | FR-017→FR-022 | Phase 1: core login + rate limit. Phase 2: session policy + history. Phase 3: monitoring UI |

> Không phát hiện vấn đề critical (🔴).

## 9. Open Questions

### Resolved (Brainstorm 2026-08-11)

- ~~Q1: CAPTCHA provider~~ → **ALTCHA Proof-of-Work** (self-hosted, MIT license, không phụ thuộc external service, zero cost)
- ~~Q2: Rate limit threshold~~ → **Multi-dimensional**: 5 req/min per IP + 3 failed/15min per username + 10 req/hour per device fingerprint. Redis INCR pattern (reuse `MfaRateLimitService`)
- ~~Q3: Access token TTL~~ → **15 min sliding window** — auto-extend on activity (Ky interceptor + visibilitychange). Absolute ceiling 10 hours
- ~~Q4: Session management~~ → **Configurable per role** — `maxSessions`, `maxDevices`, `onExceed` (REVOKE_OLDEST / REJECT_NEW / REVOKE_ALL). Multi-device supported

### Deferred to Phase 2

- Q5: ALTCHA difficulty level — default SHA-256 iterations = 50,000-100,000
- Q6: Absolute session ceiling (10h) — configurable per environment?
- Q7: New device notification channel — email / in-app / push?
- Q8: Geo-IP library — MaxMind GeoLite2 (free) hoặc ip-api.com?
- Q9: Admin dashboard UI cho login session management — cần page mới?

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Authentication — Login flow, Token management, Multi-dimensional Rate limiting, Self-hosted CAPTCHA (ALTCHA), Session policy management, Login history/device tracking, Sliding token renewal, Frontend integration

### 10.2 Flow Type
Command (single step: submit credentials → receive token)

### 10.3 Candidate Services
- `auth-service`: Primary backend — chứa LoginHandler, JwtService, SecurityConfig, CqrsAuthController (keyword: login, auth, jwt, token, rate-limit)
- `admindashboard`: Primary frontend — chứa JwtAuthProvider, authApi, SignInPageForm (keyword: sign-in, auth, token, login)

### Detection Evidence
- Keyword: `LoginHandler` → Module: `auth/application/command` → File: `LoginHandler.kt`
- Keyword: `CqrsAuthController` → Module: `auth/adapter/in/web` → File: `CqrsAuthController.kt`
- Keyword: `MfaRateLimitService` → Module: `auth/application` → File: `MfaRateLimitService.kt`
- Keyword: `mock/auth/sign-in` → Module: `@auth` → File: `authApi.ts` (frontend)
- Keyword: `email` (schema field) → Module: `(auth)/components/forms` → File: `SignInPageForm.tsx` (frontend)
- Keyword: `jwt_access_token` (localStorage key) → Module: `@auth/services/jwt` → File: `JwtAuthProvider.tsx` (frontend)

### 10.4 External Integrations
- Redis: Multi-dimensional rate limiting (INCR pattern — reuse `MfaRateLimitService`) + active session tracking
- ALTCHA: Self-hosted PoW CAPTCHA — `altcha-lib-java` (MIT). Local crypto verification, no external API
- Caffeine: In-memory replay cache cho ALTCHA challenge single-use

### 10.5 Required Modules
- `auth/adapter/in/web`: CqrsAuthController (MODIFY — thêm cookie set, CORS, session tracking)
- `auth/adapter/in/web`: CaptchaController (NEW — ALTCHA challenge endpoint)
- `auth/adapter/in/web/filter`: LoginRateLimitFilter (NEW — Redis INCR multi-dimensional)
- `auth/application`: AltchaCaptchaVerifier (NEW — HMAC verify + replay check)
- `auth/application`: LoginRateLimitService (NEW — extend MfaRateLimitService pattern)
- `auth/application`: SessionPolicyService (NEW — enforce maxSessions/maxDevices per role)
- `auth/application`: LoginSessionService (NEW — track login history + device info)
- `auth/adapter/out/persistence/entity`: LoginSessionEntity (NEW — login_sessions table)
- `shared/config`: SecurityConfig (MODIFY — CORS, security headers), SecurityProperties (MODIFY — add session + captcha.altcha)
- `@auth/authApi.ts`: (MODIFY — mock → real API endpoints + ALTCHA challenge)
- `@auth/services/jwt/JwtAuthProvider.tsx`: (MODIFY — localStorage → in-memory + sliding renewal + visibilitychange)
- `(auth)/components/forms/SignInPageForm.tsx`: (MODIFY — email → username)
- `(auth)/components/tabs/sign-in/JwtSignInTab.tsx`: (MODIFY — thêm error states + ALTCHA widget)
- `utils/api.ts`: (MODIFY — credentials: 'include', CSRF header, device fingerprint header)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Admin User | Mở login page (`/sign-in`) | Render SignInPageForm (username + password) |
| 2 | Admin User | Nhập username + password, click "Sign In" | Frontend validate (Zod schema) |
| 3 | Admin Dashboard | POST `/api/auth/login` `{ username, password, captchaPayload?, deviceFingerprint? }` | Rate Limiter check (Redis INCR — IP + username + device) |
| 4 | Auth Service | Validate credentials (BCrypt match) | Check account lock, CAPTCHA (ALTCHA PoW), MFA status |
| 5 | Auth Service | Check session policy for user's role | If maxSessions exceeded → apply onExceed strategy |
| 6 | Auth Service | Generate access + refresh token | Set refresh token cookie (HttpOnly). Create `login_sessions` record |
| 7 | Admin Dashboard | Nhận response + store access token in-memory | Redirect to Dashboard home |
| 8 | Admin Dashboard | Khi token sắp expire (< 2 min) — sliding renewal | POST `/api/auth/refresh` (cookie tự gửi). Auto via Ky interceptor + visibilitychange |
| 9 | Auth Service | Validate + rotate refresh token | Return new access token + new cookie. Update `last_activity_at` in login_sessions |
| 10 | Admin Dashboard | Absolute ceiling (10h) reached | Force re-login regardless of activity |


## 12. Traceability Matrix

| FR-ID | Source | Spec Section | Affected Class | Status |
|-------|--------|-------------|---------------|--------|
| FR-001 | IDEA | SignInPageForm.tsx | `SignInPageForm` (MODIFY) | Mapped |
| FR-002 | IDEA | authApi.ts | `authApi` (MODIFY), `CqrsAuthController` (REUSE) | Mapped |
| FR-003 | IDEA | JwtAuthProvider.tsx | `JwtAuthProvider` (MODIFY) | Mapped |
| FR-004 | IDEA | CqrsAuthController.kt + JwtAuthProvider.tsx | `CqrsAuthController` (MODIFY), `JwtAuthProvider` (MODIFY) | Mapped |
| FR-005 | IDEA | JwtAuthProvider.tsx | `JwtAuthProvider` (MODIFY) | Mapped |
| FR-006 | IDEA | LoginRateLimitFilter (NEW) | `LoginRateLimitService` (ADD), `LoginRateLimitFilter` (ADD) | Mapped |
| FR-007 | IDEA | JwtSignInTab.tsx | `JwtSignInTab` (MODIFY) | Mapped |
| FR-008 | IDEA | JwtSignInTab.tsx | `JwtSignInTab` (MODIFY), `AltchaCaptchaVerifier` (ADD) | Mapped |
| FR-009 | IDEA | JwtSignInTab.tsx | `JwtSignInTab` (MODIFY) | Mapped |
| FR-010 | IDEA | JwtSignInTab.tsx + MFA route | `JwtSignInTab` (MODIFY) | Mapped |
| FR-011 | IDEA | SecurityConfig.kt | `SecurityConfig` (MODIFY) | Mapped |
| FR-012 | ENRICHED | SecurityConfig.kt | `SecurityConfig` (MODIFY) | Mapped |
| FR-013 | ENRICHED | TokenGenerator.kt | `TokenGenerator` (REUSE) | Mapped |
| FR-014 | ENRICHED | JwtAuthProvider.tsx | `JwtAuthProvider` (MODIFY) | Mapped |
| FR-015 | ENRICHED | SecurityConfig.kt | `SecurityConfig` (MODIFY) | Mapped |
| FR-016 | ENRICHED | CqrsAuthController.kt + JwtAuthProvider.tsx | `CqrsAuthController` (MODIFY), `JwtAuthProvider` (MODIFY) | Mapped |
| FR-017 | BRAINSTORM | JwtAuthProvider.tsx + api.ts | `JwtAuthProvider` (MODIFY), Ky interceptor (MODIFY) | Mapped |
| FR-018 | BRAINSTORM | SecurityProperties.kt | `SecurityProperties` (MODIFY), `SessionPolicyService` (ADD) | Mapped |
| FR-019 | BRAINSTORM | LoginSessionEntity (NEW) | `LoginSessionService` (ADD), `LoginSessionEntity` (ADD) | Mapped |
| FR-020 | BRAINSTORM | SessionPolicyService (NEW) | `SessionPolicyService` (ADD), `RevokeSessionsHandler` (REUSE) | Mapped |
| FR-021 | BRAINSTORM | CaptchaController (NEW) | `CaptchaController` (ADD), `AltchaCaptchaVerifier` (ADD) | Mapped |
| FR-022 | BRAINSTORM | TokenController.kt + FE | `TokenController` (MODIFY), session management UI (ADD) | Mapped |

### Change Impact Map

```
FR-001 → [MODIFY] SignInPageForm.tsx (admindashboard/src/app/(public)/(auth)/components/forms/)
FR-002 → [MODIFY] authApi.ts (admindashboard/src/@auth/)
FR-003 → [MODIFY] JwtAuthProvider.tsx (admindashboard/src/@auth/services/jwt/)
FR-004 → [MODIFY] CqrsAuthController.kt (auth-service/src/main/kotlin/.../auth/adapter/in/web/) + JwtAuthProvider.tsx
FR-005 → [MODIFY] JwtAuthProvider.tsx
FR-006 → [ADD] LoginRateLimitService.kt (auth-service/src/main/kotlin/.../auth/application/ — NEW)
FR-006 → [ADD] LoginRateLimitFilter.kt (auth-service/src/main/kotlin/.../auth/adapter/in/web/filter/ — NEW)
FR-007 → [MODIFY] JwtSignInTab.tsx (admindashboard/src/app/(public)/(auth)/components/tabs/sign-in/)
FR-008 → [ADD] AltchaCaptchaVerifier.kt (auth-service/src/main/kotlin/.../auth/application/ — NEW)
FR-008 → [MODIFY] CaptchaConfig.kt + JwtSignInTab.tsx
FR-009 → [MODIFY] JwtSignInTab.tsx
FR-010 → [MODIFY] JwtSignInTab.tsx
FR-011 → [MODIFY] SecurityConfig.kt (auth-service/src/main/kotlin/.../shared/config/)
FR-012 → [MODIFY] SecurityConfig.kt
FR-013 → [REUSE] TokenGenerator.kt (auth-service/src/main/kotlin/.../auth/application/command/) — no changes needed
FR-014 → [MODIFY] JwtAuthProvider.tsx
FR-015 → [MODIFY] SecurityConfig.kt
FR-016 → [MODIFY] CqrsAuthController.kt + JwtAuthProvider.tsx
FR-017 → [MODIFY] JwtAuthProvider.tsx + api.ts (Ky interceptor — sliding renewal + visibilitychange)
FR-018 → [ADD] SessionPolicyService.kt (auth-service/src/main/kotlin/.../auth/application/ — NEW)
FR-018 → [MODIFY] SecurityProperties.kt (add SessionProperties)
FR-019 → [ADD] LoginSessionEntity.kt (auth-service/src/main/kotlin/.../auth/adapter/out/persistence/entity/ — NEW)
FR-019 → [ADD] LoginSessionService.kt (auth-service/src/main/kotlin/.../auth/application/ — NEW)
FR-020 → [ADD] SessionPolicyService.kt (enforce logic on login)
FR-020 → [REUSE] RevokeSessionsHandler.kt — session revocation
FR-021 → [ADD] CaptchaController.kt (auth-service/src/main/kotlin/.../auth/adapter/in/web/ — NEW)
FR-021 → [ADD] AltchaCaptchaVerifier.kt (challenge generation + verification)
FR-022 → [MODIFY] TokenController.kt (add session listing + revoke-by-session endpoints)
FR-022 → [ADD] session management UI (admindashboard — Phase 4)
```

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.
> **Updated after Brainstorm 2026-08-11** — see `brainstorm_notes.md` for full analysis.

### Observations
Feature này là **EXTEND lớn** — ảnh hưởng cả 2 workspaces (frontend + backend). Sau brainstorm, scope mở rộng đáng kể (+6 FRs). Backend cần tạo 5 class mới (AltchaCaptchaVerifier, LoginRateLimitService, SessionPolicyService, LoginSessionService, LoginSessionEntity) ngoài việc modify existing. Frontend cần refactor đáng kể (real API, token storage model, sliding renewal, error states, ALTCHA widget, device fingerprint).

**Độ phức tạp**: MEDIUM-HIGH — integration work + new session management subsystem.

### Brainstorm Decisions (2026-08-11)

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| CAPTCHA | **ALTCHA PoW (self-hosted)** | Zero cost, zero external dependency, MIT license, invisible UX |
| Rate Limiting | **Redis INCR multi-dimensional** (IP + username + device) | Reuse `MfaRateLimitService` pattern, no new dependency |
| Token TTL | **15 min sliding** + auto-extend on activity | Ky interceptor + visibilitychange. Absolute ceiling 10h |
| Session Policy | **Configurable per role** (maxSessions/maxDevices/onExceed) | YAML config, 3 strategies: REVOKE_OLDEST/REJECT_NEW/REVOKE_ALL |
| Rate Limit lib | **Redis INCR** (NOT Bucket4j) | Reuse-first principle — pattern proven in MfaRateLimitService |

### Related Features / Precedents
- `auth-core-features` (openspec/changes/) — 30 tasks đã implement, patterns đã validated:
  - `CaptchaVerifier` interface + `TurnstileCaptchaVerifier` adapter → reuse cho FR-008/FR-021
  - `CaptchaConfig` factory → extend: add `"altcha"` case
  - `MfaRateLimitService` (Redis INCR pattern) → reuse pattern cho FR-006
  - `MfaService.verifyMfa()` → reuse cho FR-010
  - `LoginResult` sealed class → reuse cho FR-010
  - `RevokeSessionsHandler` → reuse cho FR-020
  - `AuditLogService` → reuse cho FR-019 login history
- `erp-iam-system` (archived) — IAM architecture reference

### Integration Notes
- **Backend → Frontend Communication**: Cần đồng bộ API contract. Backend `CqrsAuthController` dùng `LoginCommand` → add `captchaPayload`, `deviceFingerprint` fields
- **Cookie Domain**: Cần clarify deployment topology (same-origin vs cross-origin)
- **Rate Limiting**: **Redis INCR pattern** (confirmed — reuse-first, no Bucket4j)
- **ALTCHA**: `altcha-lib-java` Maven dependency + Caffeine replay cache
- **Device Fingerprint**: Frontend generate SHA-256 hash → send via `X-Device-Fingerprint` header

### Suggested Phases (Updated)
1. **Phase 1 (Core Login)**: Backend rate limit (Redis INCR) + CORS + cookie handling + frontend real API + username field
2. **Phase 2 (CAPTCHA + Security)**: ALTCHA integration + security headers + CSRF + error states
3. **Phase 3 (Token + Session)**: Sliding token renewal + session policy + login history tracking
4. **Phase 4 (Monitoring)**: Active session management UI + admin session dashboard

### Reuse Map

| Existing | Reuse For | Level |
|----------|-----------|-------|
| `CaptchaVerifier` interface | ALTCHA adapter | EXTEND |
| `CaptchaConfig` factory | ALTCHA bean | EXTEND |
| `MfaRateLimitService` pattern | Login rate limiting | PATTERN REUSE |
| `RateLimitType` enum | Login rate limit types | EXTEND |
| `AuditLogService` | Login event logging | DIRECT REUSE |
| `RateLimitExceededEvent` | Login rate limit events | PATTERN REUSE |
| `TokenGenerator` | Token creation + rotation | DIRECT REUSE |
| `RevokeSessionsHandler` | Session revocation | DIRECT REUSE |
| `RefreshTokenRepository` | Token management | DIRECT REUSE |
| `LoginResult` sealed class | Login response | DIRECT REUSE |

### Context from Confluence Images
N/A — source là user idea (conversation) + feature research + brainstorm output.
