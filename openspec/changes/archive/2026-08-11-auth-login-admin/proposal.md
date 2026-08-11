## Why

Admin Dashboard frontend hiện tại sử dụng mock API cho authentication — không có bảo mật thực tế, không rate limiting, không CAPTCHA. Cần kết nối với backend auth-service thật để enable production-grade login flow cho admin users, bao gồm bảo vệ chống brute force, quản lý session, và giám sát login history.

## What Changes

- Chuyển đổi frontend auth từ mock API (`mock/auth/*`) sang real backend endpoints (`/api/auth/*`)
- Thay đổi login field từ `email` sang `username` (admin-specific identity)
- Token storage: `localStorage` → in-memory (access token) + HttpOnly cookie (refresh token)
- Thêm multi-dimensional rate limiting cho login endpoint (per-IP, per-username, per-device)
- Tích hợp ALTCHA Proof-of-Work CAPTCHA (self-hosted, zero external dependency)
- Implement sliding token renewal (15 min TTL, auto-extend on activity, absolute ceiling 10h)
- Configurable session policy per role (single/multi session, max devices, on-exceed strategy)
- Login history tracking (device fingerprint, browser, OS, IP, geo)
- Active session management API (view/revoke sessions from dashboard)
- Frontend error handling cho các states: account locked (423), CAPTCHA required (428), rate limited (429)
- Security headers: HSTS, CSP, CORS configuration cho cookie-based auth
- CSRF protection via SameSite cookie attribute

## Capabilities

### New Capabilities
- `login-rate-limiting`: Multi-dimensional rate limiting (IP + username + device fingerprint) sử dụng Redis INCR pattern, reuse từ MfaRateLimitService
- `altcha-captcha`: Self-hosted Proof-of-Work CAPTCHA — challenge generation endpoint + backend verification, implement CaptchaVerifier interface
- `session-management`: Configurable session policy per role (maxSessions, maxDevices, onExceed strategy), login history tracking, active session API
- `sliding-token-renewal`: Auto-extend access token trên frontend khi user active, sử dụng Ky interceptor + visibilitychange event, absolute ceiling 10h

### Modified Capabilities
- `user-auth`: Chuyển frontend từ mock → real API, thay email → username, token storage model mới (in-memory + HttpOnly cookie), CORS/CSRF configuration, security headers

## Impact

### Backend (auth-service)
- **MODIFY**: CqrsAuthController (cookie handling), LoginHandler (session tracking), SecurityConfig (CORS/headers), SecurityProperties (new config sections), CaptchaVerifier (ALTCHA factory case), AuthErrorCode (2 new codes), RequestDtos (new fields)
- **NEW**: LoginRateLimitFilter, LoginRateLimitService, AltchaCaptchaVerifier, CaptchaController, SessionPolicyService, LoginSessionService, LoginSessionEntity, LoginSessionRepository, RateLimitExceededException, SessionLimitExceededException
- **EXTEND**: RateLimitType enum (+3 values), application.yml (+3 config sections)
- **DATABASE**: New `login_sessions` table with indexes

### Frontend (admindashboard)
- **MODIFY**: authApi.ts (mock → real), JwtAuthProvider.tsx (token model), SignInPageForm.tsx (email → username), JwtSignInTab.tsx (error states), api.ts (credentials, headers)

### Dependencies
- **NEW**: `altcha-lib-java` Maven dependency (ALTCHA verification library)
- **EXISTING**: Redis (rate limiting + session tracking), Caffeine Cache (ALTCHA replay protection)

### APIs
- **NEW**: `GET /api/captcha/challenge` — ALTCHA PoW challenge generation
- **NEW**: `GET /api/auth/sessions/active` — List active sessions
- **NEW**: `DELETE /api/auth/sessions/{sessionId}` — Revoke specific session
- **MODIFY**: `POST /api/auth/login` — Add captchaPayload, deviceFingerprint fields
- **MODIFY**: `POST /api/auth/refresh` — Extract refresh token from HttpOnly cookie instead of request body
