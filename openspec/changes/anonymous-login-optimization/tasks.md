<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: anonymous-login-optimization

> **Type**: EXTEND | **Flow**: Non-Financial | **FRs**: 13 (8 IDEA + 5 ENRICHED)

## Changes

This feature extends the auth-service to support anonymous/guest sessions with the following scope:

**MODIFY** (14 existing files):
- `JwtService.kt` — add `generateAnonymousToken()` + `parseAnonymousToken()` methods (~30 lines)
- `LoginHandler.kt` — inject `SessionPromotionService`, add best-effort promotion block after `recordLogin()`
- `LoginCommand.kt` — add optional `anonymousSessionId: String? = null` field
- `LoginResult.kt` — extend `Success` with `promotionResult: PromotionResult? = null`
- `RegisterCommand.kt` — add optional `anonymousSessionId: String? = null` field
- `RegisterHandler.kt` — inject `SessionPromotionService`, add promotion block after user creation
- `CqrsAuthController.kt` — pass `anonymousSessionId` from DTO → Command, include promotion metadata in response
- `RequestDtos.kt` — add `anonymousSessionId` to `LoginRequestDto` + `RegisterRequestDto`
- `AuthResponse.kt` — add `promotedFromAnonymous: Boolean`, `dataTransferred: DataTransferredInfo?`
- `SecurityConfig.kt` — add `permitAll()` for `/api/v1/auth/anonymous`, `hasRole("ANONYMOUS")` for sub-paths
- `SecurityProperties.kt` — add `AnonymousProperties` nested data class
- `JwtAuthFilter.kt` — add `type=anonymous` branch → `ROLE_ANONYMOUS` authority
- `AuthErrorCode.kt` — add 5 new enum entries (AUTH_040-044)
- `application.yml` — add `app.security.anonymous.*` config block

**NEW** (~15 files):
- `AnonymousAuthController.kt` — REST controller for `/api/v1/auth/anonymous/**`
- `CreateAnonymousSessionCommand.kt` + `AnonymousSessionHandler.kt` — CQRS create session
- `RenewAnonymousTokenCommand.kt` + `RenewAnonymousTokenHandler.kt` — CQRS renew token
- `SessionPromotionService.kt` — promotion orchestration (lock → transfer → blacklist → cleanup)
- `AnonymousSessionDataService.kt` — Redis CRUD for session data with size limits
- `AnonymousRateLimitService.kt` — IP-based rate limiting (INCR+EXPIRE fixed window)
- `AnonymousDtos.kt` — request/response DTOs (AnonymousTokenResponse, StoreSessionDataRequest, etc.)
- `PromotionResult.kt` + `AnonymousSessionResult.kt` — result data classes
- `AnonymousExceptions.kt` — 5 exception classes (AUTH_040-044)

**REUSE** (no modification):
- `TokenBlacklistRepository` + `TokenBlacklistEntity` — anonymous token JTI blacklisting
- `StringRedisTemplate` + `RedisTemplate<String, Any>` — all Redis operations
- `MfaProperties.LimitConfig` — reused for anonymous rate limit config
- `GlobalExceptionHandler` — handles new AuthException subclasses automatically

**DATABASE**: No new tables or migrations. Reuse existing `token_blacklist` table.

**REDIS**: New key namespaces: `anon:session:*`, `anon:data:*`, `anon:lock:*`, `anon:rate:*`

## Phase 1: Foundation — Configuration & Error Codes (Day 1)

- [x] **Task 1: Add AnonymousProperties to SecurityProperties**
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` | Action: [MODIFY]
  - FR: FR-012 — Cấu hình anonymous session properties
  - Base: `SecurityProperties` from `com.ntt.authservice.shared.config`
  - Pattern: Nested data class following `MfaProperties`, `SessionProperties` pattern
  - Dependencies: `MfaProperties.LimitConfig` (reuse for rate limit config)
  - Details:
    - Add `val anonymous: AnonymousProperties = AnonymousProperties()` at line 18 (after `session`)
    - Nested class `AnonymousProperties`:
      - `val tokenTtlSeconds: Long = 3600` (1h)
      - `val sessionTtlSeconds: Long = 86400` (24h)
      - `val maxDataSizeBytes: Long = 65536` (64KB)
      - `val maxRenewals: Int = 24`
      - `val promotedDataTtlSeconds: Long = 604800` (7d)
      - `val rateLimit: MfaProperties.LimitConfig = MfaProperties.LimitConfig(maxAttempts = 5, windowSeconds = 3600, lockSeconds = 0)`

- [x] **Task 2: Add anonymous error codes to AuthErrorCode**
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt` | Action: [MODIFY]
  - FR: FR-009, FR-010, FR-007, FR-008 — Error codes for anonymous feature
  - Base: `AuthErrorCode` enum from `com.ntt.authservice.shared.exception`
  - Pattern: Follow existing enum entry pattern with `(errorCode, msgCode, description, httpStatus)`
  - Details:
    - Add after `E2EE_MAX_DEVICES` (last existing entry) with comment `// --- Anonymous Session Error Codes — AUTH_040~044 ---`
    - `ANONYMOUS_SESSION_EXPIRED(AUTH_040)`, `ANONYMOUS_DATA_LIMIT_EXCEEDED(AUTH_041)`, `ANONYMOUS_PROMOTION_CONFLICT(AUTH_042)`, `ANONYMOUS_RATE_LIMITED(AUTH_043)`, `ANONYMOUS_MAX_RENEWALS(AUTH_044)`

- [x] **Task 3: Create anonymous exception classes**
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/AnonymousExceptions.kt` | Action: [NEW]
  - FR: FR-006, FR-007, FR-008, FR-009, FR-010
  - Base: `AuthException` from `com.ntt.authservice.shared.exception`
  - Pattern: Follow `AuthExceptions.kt` pattern — each class extends `AuthException(authError, message, httpStatus)`
  - Details:
    - `AnonymousSessionExpiredException(sessionId)` → AUTH_040
    - `AnonymousDataLimitExceededException(currentSize, maxSize)` → AUTH_041
    - `AnonymousPromotionConflictException(sessionId)` → AUTH_042
    - `AnonymousRateLimitedException(retryAfterSeconds)` → AUTH_043
    - `AnonymousMaxRenewalsException(maxRenewals)` → AUTH_044

## Phase 2: JWT Service Extension (Day 1-2)

- [x] **Task 4: Extend JwtService with anonymous token methods**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` | Action: [MODIFY]
  - FR: FR-001, FR-008
  - Base: `JwtService` from `com.ntt.authservice.auth.application`
  - Pattern: Mirror `generateMfaToken()` at L122-137 and `parseMfaToken()` at L142-149
  - Details:
    - `fun generateAnonymousToken(sessionId: String): String` — JWT with `type=anonymous`, sub=sessionId
    - `fun parseAnonymousToken(token: String): Claims` — parse + validate type=anonymous

## Phase 3: Core Services (Day 2-3)

- [x] **Task 5: Create AnonymousRateLimitService**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` | Action: [NEW]
  - FR: FR-009 — Rate limit anonymous token creation
  - Base: Pattern from `LoginRateLimitService` (simplified IP-only)
  - Pattern: `@Service`, Redis INCR+EXPIRE fixed window, fail-open

- [x] **Task 6: Create AnonymousSessionDataService**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` | Action: [NEW]
  - FR: FR-004, FR-006, FR-007
  - Pattern: `@Service`, `StringRedisTemplate` operations, SCAN for data discovery

- [x] **Task 7: Create SessionPromotionService**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` | Action: [NEW]
  - FR: FR-003, FR-004, FR-005, FR-010
  - Pattern: Distributed lock + transfer + blacklist + cleanup

- [x] **Task 8: Create PromotionResult and AnonymousSessionResult**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/PromotionResult.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionResult.kt` | Action: [NEW]
  - FR: FR-013, FR-001

## Phase 4: CQRS Handlers (Day 3-4)

- [x] **Task 9: Create CreateAnonymousSessionCommand + AnonymousSessionHandler**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/CreateAnonymousSessionCommand.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` | Action: [NEW]
  - FR: FR-001, FR-002

- [x] **Task 10: Create RenewAnonymousTokenCommand + RenewAnonymousTokenHandler**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenCommand.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` | Action: [NEW]
  - FR: FR-008

- [x] **Task 11: Extend LoginCommand + LoginHandler for promotion**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt` | Action: [MODIFY]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` | Action: [MODIFY]
  - FR: FR-003

- [x] **Task 12: Extend LoginResult with promotionResult**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt` | Action: [MODIFY]
  - FR: FR-013

- [x] **Task 13: Extend RegisterCommand + RegisterHandler for promotion**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt` | Action: [MODIFY]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` | Action: [MODIFY]
  - FR: FR-003

## Phase 5: API Layer — DTOs + Controllers (Day 4-5)

- [x] **Task 14: Create anonymous DTOs**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` | Action: [NEW]
  - FR: FR-001, FR-006, FR-008, FR-013

- [x] **Task 15: Extend LoginRequestDto + RegisterRequestDto**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` | Action: [MODIFY]
  - FR: FR-003

- [x] **Task 16: Extend AuthResponse with promotion metadata**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt` | Action: [MODIFY]
  - FR: FR-013

- [x] **Task 17: Create AnonymousAuthController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt` | Action: [NEW]
  - FR: FR-001, FR-006, FR-008

- [x] **Task 18: Extend CqrsAuthController for promotion response**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-003, FR-013

## Phase 6: Security Layer (Day 5)

- [x] **Task 19: Extend JwtAuthFilter for anonymous token recognition**
  - File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` | Action: [MODIFY]
  - FR: FR-011

- [x] **Task 20: Extend SecurityConfig with anonymous permitAll paths**
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-011

## Phase 7: Verification & Testing (Day 6-8)

- [x] **Task 21: FR traceability verification**
  - Verify all 13 FRs covered:
    - FR-001: Task 4 (JwtService), Task 9 (handler), Task 17 (controller) ✓
    - FR-002: Task 9 (handler Redis init) ✓
    - FR-003: Task 11 (LoginHandler), Task 13 (RegisterHandler), Task 15 (DTOs), Task 18 (controller) ✓
    - FR-004: Task 6 (data service), Task 7 (promotion service) ✓
    - FR-005: Task 7 (promotion service blacklist) ✓
    - FR-006: Task 6 (data service), Task 17 (controller) ✓
    - FR-007: Task 6 (data service size check) ✓
    - FR-008: Task 4 (JwtService), Task 10 (handler), Task 17 (controller) ✓
    - FR-009: Task 5 (rate limit service), Task 9 (handler calls it) ✓
    - FR-010: Task 7 (distributed lock in promotion) ✓
    - FR-011: Task 19 (JwtAuthFilter), Task 20 (SecurityConfig) ✓
    - FR-012: Task 1 (AnonymousProperties config) ✓
    - FR-013: Task 8 (PromotionResult), Task 12 (LoginResult), Task 14 (DTOs), Task 16 (AuthResponse), Task 18 (controller) ✓
  - **Coverage: 13/13 FRs ✓**

- [x] **Task 22: Update application.yml with anonymous config**
  - File: `src/main/resources/application.yml` | Action: [MODIFY]
  - FR: FR-012
  - Added `app.security.anonymous.*` config block with environment variable overrides

- [x] **Task 23: Update GlobalExceptionHandler for anonymous exceptions**
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` | Action: [MODIFY]
  - FR: FR-009, FR-007, FR-008
  - Added ProblemDetail properties and Retry-After header for anonymous rate limit exceptions
