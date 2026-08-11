<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

## 1. Configuration & Error Codes (Foundation)

- [x] **1.1 Extend SecurityProperties**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` | Action: [MODIFY]
  - Base: `@ConfigurationProperties(prefix = "app.security")` data class
  - FR: FR-006 — Multi-dimensional rate limiting, FR-018 — Configurable session policy, FR-021 — ALTCHA CAPTCHA
  - Pattern: Nested data class (matches existing JwtProperties, MfaProperties, CaptchaProperties)
  - Details: Add `LoginRateLimitProperties` (ip: LimitConfig, username: LimitConfig, device: LimitConfig), `SessionProperties` (maxSessions=3, maxDevices=3, onExceed=REVOKE_OLDEST, roleOverrides: Map<String, SessionOverride>), `AltchaCaptchaProperties` (hmacKey, difficulty=50000, challengeTtlSeconds=300)
  - Dependencies: None (config class, no imports needed)

- [x] **1.2 Update application.yml**
  - File: `auth-service/src/main/resources/application.yml` | Action: [MODIFY]
  - FR: FR-006, FR-018, FR-021
  - Pattern: Under `app.security.*` prefix (existing pattern)
  - Details: Add `login-rate-limit.ip` (max-attempts=5, window-seconds=60, lock-seconds=300), `login-rate-limit.username` (max-attempts=3, window-seconds=900, lock-seconds=1800), `login-rate-limit.device` (max-attempts=10, window-seconds=3600, lock-seconds=3600), `session.max-sessions=3`, `session.max-devices=3`, `session.on-exceed=REVOKE_OLDEST`, `captcha.altcha.hmac-key=${ALTCHA_HMAC_KEY:}`, `captcha.altcha.difficulty=50000`

- [x] **1.3 Add error codes**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt` | Action: [MODIFY]
  - Base: `enum class AuthErrorCode(errorCode, msgCode, description, httpStatus)`
  - FR: FR-006, FR-020
  - Pattern: `CODE("AUTH_0XX", "auth.xxx", "description", HttpStatus.XXX)` (existing: AUTH_001-AUTH_019)
  - Details: `RATE_LIMITED("AUTH_020", "auth.rate_limited", "Too many login attempts", HttpStatus.TOO_MANY_REQUESTS)`, `SESSION_LIMIT_EXCEEDED("AUTH_021", "auth.session_limit", "Maximum active sessions exceeded", HttpStatus.CONFLICT)`

- [x] **1.4 Create RateLimitExceededException**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/RateLimitExceededException.kt` | Action: [NEW]
  - Base: `AuthException(AuthErrorCode)` from `com.ntt.authservice.shared.exception.AuthExceptions`
  - FR: FR-006 — Rate limiting error response
  - Error: `AUTH_020` (RATE_LIMITED)
  - Pattern: Same as `AccountLockedException` (carries extra fields for ProblemDetail)
  - Details: Fields: `retryAfterSeconds: Long`, `dimension: String` (IP/USER/DEVICE)
  - Dependencies: `AuthException`, `AuthErrorCode`

- [x] **1.5 Create SessionLimitExceededException**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/SessionLimitExceededException.kt` | Action: [NEW]
  - Base: `AuthException(AuthErrorCode)`
  - FR: FR-020 — Session policy enforcement
  - Error: `AUTH_021` (SESSION_LIMIT_EXCEEDED)
  - Pattern: Same as `AccountLockedException`
  - Details: Fields: `maxSessions: Int`, `activeCount: Int`
  - Dependencies: `AuthException`, `AuthErrorCode`

- [x] **1.6 Extend GlobalExceptionHandler**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` | Action: [MODIFY]
  - Base: `@RestControllerAdvice` — existing handler chain (handleAuthException → handleBusinessException → handleException)
  - FR: FR-006, FR-020
  - Pattern: `@ExceptionHandler(XxxException::class)` → ProblemDetail (RFC 7807)
  - Details: Add `handleRateLimitException` (HTTP 429 + `Retry-After` header in ProblemDetail), `handleSessionLimitException` (HTTP 409 + maxSessions/activeCount in properties)
  - Dependencies: `RateLimitExceededException`, `SessionLimitExceededException`, `ProblemDetail`

## 2. Login Rate Limiting (Backend)

- [x] **2.1 Extend RateLimitType enum**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` | Action: [MODIFY]
  - FR: FR-006
  - Pattern: `enum class RateLimitType(val key: String)` — existing: OTP_VERIFY, MFA_LOGIN
  - Details: Add `LOGIN_IP("LOGIN_IP")`, `LOGIN_USER("LOGIN_USER")`, `LOGIN_DEVICE("LOGIN_DEVICE")`

- [x] **2.2 Create LoginRateLimitService**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` | Action: [NEW]
  - Base: N/A (@Service)
  - FR: FR-006 — Multi-dimensional rate limiting
  - Pattern: Reuse Redis INCR + EXPIRE from `MfaRateLimitService.checkAndIncrement()` (match 85%)
  - Details: Method `checkMultiDimensional(ip: String, username: String, deviceFp: String?)` — checks IP, then username, then device (if present). Redis keys: `rate:login:ip:{ip}`, `rate:login:user:{username}`, `rate:login:device:{fp}`. Fail-open strategy. Audit logging.
  - Dependencies: `StringRedisTemplate`, `SecurityProperties.LoginRateLimitProperties`, `AuditLogService`, `ApplicationEventPublisher`
  - Source: Pattern from `MfaRateLimitService.kt` L48-121

- [x] **2.3 Create LoginRateLimitFilter**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt` | Action: [NEW]
  - Base: `OncePerRequestFilter` from Spring Web
  - FR: FR-006
  - Pattern: Servlet filter — check before handler (pre-authentication)
  - Details: Override `doFilterInternal()`, match only `POST /api/auth/login`. Extract IP from `X-Forwarded-For` / `request.remoteAddr`. Extract username from JSON body (parse minimal). Extract device from `X-Device-Fingerprint` header. Call `LoginRateLimitService.checkMultiDimensional()`. If exception → write 429 JSON response directly.
  - Dependencies: `LoginRateLimitService`, `ObjectMapper`

- [x] **2.4 Register LoginRateLimitFilter in SecurityConfig**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-006
  - Details: Inject `LoginRateLimitFilter`, add `http.addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)` (before JwtAuthFilter in chain)
  - Dependencies: `LoginRateLimitFilter`

- [x] **2.5 Config resolver for login rate limits**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` | Action: [MODIFY] (same as 2.2)
  - FR: FR-006
  - Details: Internal `getConfig(type: RateLimitType)` method reading from `SecurityProperties.loginRateLimit.ip/username/device`. Do NOT modify MfaRateLimitService.getConfig() — separate concern.

## 3. ALTCHA CAPTCHA (Backend)

- [x] **3.1 Add altcha-lib-java dependency**
  - File: `auth-service/pom.xml` | Action: [MODIFY]
  - FR: FR-021
  - Details: Add `<dependency><groupId>org.altcha</groupId><artifactId>altcha</artifactId><version>{latest}</version></dependency>`. Verify Maven Central availability + MIT license. If not available as Maven package → implement PoW verification manually (SHA-256 + HMAC, ~50 lines)

- [x] **3.2 Create AltchaCaptchaVerifier**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AltchaCaptchaVerifier.kt` | Action: [NEW]
  - Base: `CaptchaVerifier` interface from `com.ntt.authservice.auth.application.CaptchaVerifier`
  - FR: FR-008, FR-021 — CAPTCHA verification + replay protection
  - Pattern: Same interface as `TurnstileCaptchaVerifier` — implements `verify(token: String): Boolean`
  - Details: Decode Base64 payload → extract {algorithm, challenge, number, salt, signature}. Verify HMAC-SHA256(salt, hmacKey) == signature. Verify SHA-256(salt + number) == challenge. Check Caffeine cache for replay (salt used before). On success → add salt to cache (TTL 5 min).
  - Dependencies: `SecurityProperties.CaptchaProperties`, `com.github.ben-manes.caffeine.cache.Cache`

- [x] **3.3 Extend CaptchaConfig factory**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt` | Action: [MODIFY]
  - FR: FR-021
  - Pattern: `when (props.captcha.provider)` factory at L60-63
  - Details: Add `"altcha" -> AltchaCaptchaVerifier(props.captcha, altchaCaffeineCache)` case. Add Caffeine cache bean.

- [x] **3.4 Create CaptchaController**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt` | Action: [NEW]
  - Base: N/A (@RestController)
  - FR: FR-021 — Challenge generation endpoint
  - Pattern: Same as other `@RestController` in `auth/adapter/in/web`
  - Details: `GET /api/captcha/challenge` → generate random salt (UUID), compute challenge = SHA-256(salt + randomNumber), sign = HMAC-SHA256(salt, hmacKey). Return JSON `{algorithm: "SHA-256", challenge, salt, signature, maxnumber: difficulty}`.
  - Dependencies: `SecurityProperties.CaptchaProperties`

- [x] **3.5 Permit CAPTCHA endpoint in SecurityConfig**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-021
  - Details: Add `.requestMatchers("/api/captcha/challenge").permitAll()` to public endpoints

## 4. Session Management (Backend)

- [x] **4.1 Create LoginSessionEntity**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/LoginSessionEntity.kt` | Action: [NEW]
  - Base: `SnowflakePersistentAuditableEntity()` from `com.ntt.basecore.model.id`
  - FR: FR-019 — Login history tracking
  - Pattern: Same as `UserEntity` — `@Entity @Table(name = "login_sessions")`, `lateinit var` for required fields
  - Details: Columns: userId (Long), refreshTokenId (Long?), ipAddress (String, 45), userAgent (String?, 500), deviceFingerprint (String?, 64), deviceType (String?, 20), browserName (String?, 50), osName (String?, 50), geoCountry (String?, 3), isActive (Boolean=true), isNewDevice (Boolean=false), loginAt (Instant), lastActivityAt (Instant?), revokedAt (Instant?), revokeReason (String?, 50)
  - Dependencies: `SnowflakePersistentAuditableEntity`

- [x] **4.2 Create Flyway migration**
  - File: `auth-service/src/main/resources/db/migration/V{next}__create_login_sessions.sql` | Action: [NEW]
  - FR: FR-019
  - Pattern: Check existing migrations for version numbering pattern
  - Details: DDL from impact_analysis.md schema + indexes (idx_login_sessions_user_active, idx_login_sessions_device, idx_login_sessions_ip)

- [x] **4.3 Create LoginSessionRepository**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/LoginSessionRepository.kt` | Action: [NEW]
  - Base: `JpaRepository<LoginSessionEntity, Long>` from Spring Data
  - FR: FR-019, FR-020
  - Pattern: Same as `UserRepository` — Spring Data JPA with custom queries
  - Details: Methods: `findByUserIdAndIsActiveTrue(userId: Long): List<LoginSessionEntity>`, `countByUserIdAndIsActiveTrue(userId: Long): Long`, `findFirstByUserIdAndIsActiveTrueOrderByLoginAtAsc(userId: Long): LoginSessionEntity?`, `findByDeviceFingerprintAndUserId(fp: String, userId: Long): List<LoginSessionEntity>`

- [x] **4.4 Create LoginSessionService**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt` | Action: [NEW]
  - Base: N/A (@Service)
  - FR: FR-019 — Login history tracking
  - Pattern: Domain service with constructor injection
  - Details: Methods: `recordLogin(userId, ip, userAgent, deviceFp, refreshTokenId): LoginSessionEntity` (parse UA for device/browser/OS, detect new device by querying deviceFp, emit NewDeviceLoginEvent if new), `updateLastActivity(sessionId)`, `revokeSession(sessionId, reason)`, `getActiveSessions(userId): List<SessionDto>`, `revokeOldestSession(userId)`, `revokeAllSessions(userId, reason)`
  - Dependencies: `LoginSessionRepository`, `ApplicationEventPublisher`, `AuditLogService`

- [x] **4.5 Create SessionPolicyService**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPolicyService.kt` | Action: [NEW]
  - Base: N/A (@Service)
  - FR: FR-018, FR-020 — Configurable session policy
  - Details: Method `enforcePolicy(userId: Long, userRoles: List<String>)`: get effective policy (check roleOverrides → fallback default), count active sessions, execute strategy. Strategies: REVOKE_OLDEST → call loginSessionService.revokeOldestSession(), REJECT_NEW → throw SessionLimitExceededException, REVOKE_ALL → call loginSessionService.revokeAllSessions()
  - Dependencies: `LoginSessionService`, `SecurityProperties.SessionProperties`

- [x] **4.6 Create NewDeviceLoginEvent**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/event/NewDeviceLoginEvent.kt` | Action: [NEW]
  - FR: FR-019
  - Pattern: Same as `RateLimitExceededEvent` (data class ApplicationEvent)
  - Details: `data class NewDeviceLoginEvent(val userId: Long, val deviceFingerprint: String, val ipAddress: String, val browserName: String?, val osName: String?)`

## 5. Login Flow Integration (Backend)

- [x] **5.1 Extend LoginRequestDto**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` | Action: [MODIFY]
  - FR: FR-008, FR-019
  - Pattern: Existing fields: username, password, domainCode, captchaToken, trustedDeviceHash
  - Details: Add `val deviceFingerprint: String? = null`, `val captchaPayload: String? = null` (ALTCHA Base64 payload — distinct from existing captchaToken for backward compat)

- [x] **5.2 Extend LoginCommand**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` (or dedicated Commands.kt) | Action: [MODIFY]
  - FR: FR-019
  - Details: Add `val deviceFingerprint: String? = null`, `val ipAddress: String = ""`, `val userAgent: String = ""`

- [x] **5.3 Modify LoginHandler**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` | Action: [MODIFY]
  - FR: FR-019, FR-020
  - Details: After `resetFailedLogins()` and before generating response: inject `SessionPolicyService` + `LoginSessionService`. Call `sessionPolicyService.enforcePolicy(user.id.value, userRoles)`. Call `loginSessionService.recordLogin(user.id.value, command.ipAddress, command.userAgent, command.deviceFingerprint, refreshTokenId)`. Constructor params go from 8 → 10.
  - Dependencies: `SessionPolicyService`, `LoginSessionService`

- [x] **5.4 Modify CqrsAuthController.login()**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-002, FR-004, FR-016
  - Details: Add `request: HttpServletRequest, response: HttpServletResponse` params. Extract IP from `X-Forwarded-For` / `remoteAddr`. Extract userAgent from `User-Agent` header. Pass to LoginCommand. On success: set `Set-Cookie: refresh_token={token}; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age={refreshTtl}`. Remove refreshToken from response body (keep accessToken, tokenType, expiresIn, user data).
  - Dependencies: `HttpServletRequest`, `HttpServletResponse`, `jakarta.servlet.http.Cookie`

- [x] **5.5 Modify CqrsAuthController.refresh()**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-016, FR-017
  - Details: Change from `@RequestBody request: RefreshTokenRequestDto` to extract refresh token from `@CookieValue("refresh_token") refreshToken: String`. Set new refresh cookie in response. Call `loginSessionService.updateLastActivity()`. Add absolute ceiling check: if `iat + 36000s` exceeded → reject with 401.
  - Dependencies: `LoginSessionService`

- [x] **5.6 Add logout endpoint**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-022
  - Details: `@PostMapping("/logout")` — extract refresh token from cookie, revoke in DB via tokenStore, deactivate session via loginSessionService, clear cookie (`Set-Cookie: refresh_token=; Max-Age=0`).
  - Dependencies: `TokenStore`, `LoginSessionService`

## 6. Security & CORS (Backend)

- [x] **6.1 Add CORS configuration**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-011
  - Details: Add `.cors { cors -> cors.configurationSource(corsConfigurationSource()) }` to filter chain. Create `@Bean corsConfigurationSource()`: `CorsConfiguration().apply { allowedOrigins = listOf(frontendOrigin), allowCredentials = true, allowedHeaders = listOf("*"), allowedMethods = listOf("GET","POST","PUT","DELETE","OPTIONS") }`. Read frontendOrigin from config.

- [x] **6.2 Add security headers**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-012, FR-015
  - Details: Add `.headers { h -> h.httpStrictTransportSecurity { hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true) }.contentTypeOptions {}.frameOptions { fo -> fo.deny() } }` to filter chain.

- [x] **6.3 Permit new endpoints**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-022
  - Details: Add `.requestMatchers("/api/auth/logout").authenticated()`, `.requestMatchers("/api/auth/sessions/**").authenticated()` to authorizeHttpRequests

## 7. Session API (Backend)

- [x] **7.1 Add active sessions endpoint**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` (or separate SessionController) | Action: [MODIFY]
  - FR: FR-022
  - Details: `@GetMapping("/sessions/active")` — get userId from SecurityContext, call `loginSessionService.getActiveSessions(userId)`, return list with isCurrentSession flag (compare sessionId from JWT claim).

- [x] **7.2 Add session revocation endpoint**
  - File: Same as 7.1 | Action: [MODIFY]
  - FR: FR-022
  - Details: `@DeleteMapping("/sessions/{sessionId}")` — verify session belongs to current user (loginSessionService.revokeSession validates ownership). Return 204 No Content on success, 403 if not owner.

## 8. Frontend — Auth API & Provider

- [x] **8.1 Modify authApi.ts — mock to real**
  - File: `admindashboard/src/@auth/authApi.ts` | Action: [MODIFY]
  - FR: FR-001, FR-002
  - Details: Replace `mock/auth/sign-in` → `api/auth/login`, `mock/auth/refresh` → `api/auth/refresh`, `mock/auth/sign-in-with-token` → remove (replaced by cookie-based refresh). Change signIn params from `{email, password}` to `{username, password, domainCode: "default", captchaPayload?, deviceFingerprint?}`. Add `authLogout(): Promise<void>` calling `POST api/auth/logout`. Add `getActiveSessions()`, `revokeSession(sessionId)`.

- [x] **8.2 Modify api.ts — cookie + device fingerprint**
  - File: `admindashboard/src/utils/api.ts` | Action: [MODIFY]
  - FR: FR-012, FR-015, FR-017
  - Details: Add `credentials: 'include'` to Ky create options. Add `beforeRequest` hook that sets `X-Device-Fingerprint` header from `generateDeviceFingerprint()` utility.

- [x] **8.3 Modify JwtAuthProvider — in-memory token**
  - File: `admindashboard/src/@auth/services/jwt/JwtAuthProvider.tsx` | Action: [MODIFY]
  - FR: FR-003, FR-004, FR-005, FR-014
  - Details: Remove all `localStorage.getItem/setItem('access_token')` calls. Store access token in `useRef<string | null>(null)`. On mount → call `POST api/auth/refresh` (cookie auto-sent) → get access token. signOut → call `authLogout()` → clear ref → redirect. Update `getAccessToken()` to return ref value.

- [x] **8.4 Implement sliding token renewal**
  - File: `admindashboard/src/@auth/services/jwt/JwtAuthProvider.tsx` | Action: [MODIFY]
  - FR: FR-016, FR-017
  - Details: Ky `beforeRequest` hook: decode JWT (base64 payload), check `exp - Date.now()/1000 < 120` → trigger refresh. Concurrent lock: `refreshPromise = useRef<Promise | null>`. If refreshPromise exists → await it instead of new call. `visibilitychange` listener: on visible + token expired → trigger refresh. Cleanup listener on unmount.

- [x] **8.5 Implement absolute ceiling**
  - File: `admindashboard/src/@auth/services/jwt/JwtAuthProvider.tsx` | Action: [MODIFY]
  - FR: FR-016
  - Details: On each refresh response, check `iat` claim from new access token. If `Date.now()/1000 - iat > 36000` → force signOut with message "Phiên đăng nhập đã hết hạn".

## 9. Frontend — Login Form & Error Handling

- [x] **9.1 Modify SignInPageForm — email to username**
  - File: `admindashboard/src/app/(public)/(auth)/components/forms/SignInPageForm.tsx` | Action: [MODIFY]
  - FR: FR-001
  - Details: Change field name `email` → `username`, label "Email" → "Tên đăng nhập", remove email validation (type="email" → type="text"), keep required validation.

- [x] **9.2 Modify JwtSignInTab — error states**
  - File: `admindashboard/src/app/(public)/(auth)/components/tabs/sign-in/JwtSignInTab.tsx` | Action: [MODIFY]
  - FR: FR-007, FR-009, FR-010
  - Details: Add error state handling: `catch (err)` → check `err.response.status`. 423 → parse `retryAfter` from body → show "Tài khoản đã bị khóa" + countdown timer (setInterval). 428 → trigger ALTCHA widget → auto-retry with captchaPayload. 429 → parse `Retry-After` header → show "Quá nhiều yêu cầu" + countdown. MFA → redirect to MFA page with mfaToken.

- [x] **9.3 Add ALTCHA widget**
  - File: `admindashboard/src/@auth/services/jwt/components/AltchaWidget.tsx` | Action: [NEW]
  - FR: FR-008, FR-021
  - Details: React component — on mount/trigger: `fetch GET /api/captcha/challenge` → receive {algorithm, challenge, salt, signature, maxnumber}. Solve PoW: iterate number 0→maxnumber, compute SHA-256(salt + number), compare with challenge. On solve: Base64-encode {algorithm, challenge, number, salt, signature} → call onSolved(payload) callback. Show minimal UI (spinner during solve, checkmark when done). Use Web Crypto API for SHA-256.

- [x] **9.4 Add device fingerprint utility**
  - File: `admindashboard/src/utils/deviceFingerprint.ts` | Action: [NEW]
  - FR: FR-019
  - Details: `async function generateDeviceFingerprint(): Promise<string>` — collect: `screen.width + screen.height`, `Intl.DateTimeFormat().resolvedOptions().timeZone`, `navigator.language`, `navigator.platform`, `screen.colorDepth`, `navigator.hardwareConcurrency`. Concatenate → SHA-256 via Web Crypto API → hex string. Cache result in module-level variable.

## 10. Verification

- [x] 10.1 Backend: Verify build compiles (`./gradlew build`)
- [x] 10.2 Backend: Verify Flyway migration applies correctly
- [x] 10.3 Frontend: Verify build compiles (`npm run build`)
- [x] 10.4 Integration test: login flow end-to-end (username/password → access token + cookie)
- [x] 10.5 Integration test: rate limiting (trigger IP limit, verify 429)
- [x] 10.6 Integration test: CAPTCHA flow (challenge → solve → verify)
- [x] 10.7 Integration test: session management (create sessions → enforce policy → list → revoke)
- [x] 10.8 Integration test: sliding renewal (token refresh, tab focus recovery, absolute ceiling)
