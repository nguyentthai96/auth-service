# SRS: Anonymous Login Optimization (MAINTENANCE)

[CHANGED] Scope reassessed from EXTEND → MAINTENANCE. Feature is fully implemented. This SRS covers post-implementation hardening: bug fixes, tests, observability.

## 1. Feature Overview

Anonymous Login Optimization feature đã được implement hoàn chỉnh trong codebase (12 classes mới + 10 classes sửa). SRS này mô tả MAINTENANCE scope: sửa bug JTI blacklisting, loại bỏ ThreadLocal smell, thêm Micrometer observability metrics, và bổ sung test coverage.

## 2. Bug Fix Requirements

### FIX-001: JTI Blacklisting Correctness [🔴 BUG — Security]
- **Current behavior**: `LoginHandler` (L160) và `RegisterHandler` (L94) truyền `anonymousJti = ""` vào `SessionPromotionService.promoteSession()`. Kết quả: `TokenBlacklistEntity` được tạo với `tokenJti = ""`, anonymous token thực tế KHÔNG bị blacklist và có thể tái sử dụng sau promotion.
- **Expected behavior**: Anonymous token JTI phải được blacklist chính xác sau promotion. Token phải bị reject khi sử dụng lại (FR-005 satisfaction).
- **Root cause**: `LoginCommand` và `RegisterCommand` không có field cho anonymous token JTI. Controller layer có Authorization header nhưng không propagate JTI qua command.
- **Fix specification**:
  1. Add `val anonymousToken: String? = null` to `LoginRequestDto` and `RegisterRequestDto` — client gửi full anonymous JWT
  2. Add `val anonymousTokenJti: String? = null` to `LoginCommand` and `RegisterCommand`
  3. In `CqrsAuthController.login()` and `.register()`: if `request.anonymousToken != null` → parse via `jwtService.parseAnonymousToken(request.anonymousToken)` → extract `claims.id` as JTI
  4. Pass `anonymousTokenJti` to command → handler passes to `SessionPromotionService`
  5. In `LoginHandler` and `RegisterHandler`: replace `anonymousJti = ""` with `anonymousJti = command.anonymousTokenJti ?: ""`
- **Validation**: 
  - After promotion, `TokenBlacklistEntity.tokenJti` contains actual JTI (not empty string)
  - Anonymous token with that JTI is rejected by `JwtAuthFilter` blacklist check
  - Login/register still succeeds if `anonymousToken` field is null (backward compatible)
- **API change**: New optional field `anonymousToken` in request body. Non-breaking — existing clients unaffected.

### FIX-002: Remove ThreadLocal from RegisterHandler [🟡 SMELL — Architecture]
- **Current behavior**: `RegisterHandler` uses `ThreadLocal<PromotionResult?>` (L35) and exposes `lastPromotionResult` var. `CqrsAuthController.register()` reads this after `handle()`.
- **Expected behavior**: `RegisterHandler` returns promotion result as part of its return type (like `LoginHandler` returns `LoginResult`).
- **Risks of current approach**: ThreadLocal leak in pooled/virtual threads (Project Loom); temporal coupling between handler and controller; violates SRP.
- **Fix specification**:
  1. Create `RegisterResult.kt` sealed class:
     ```kotlin
     sealed class RegisterResult {
         data class Success(
             val authToken: AuthToken,
             val promotionResult: PromotionResult? = null
         ) : RegisterResult()
     }
     ```
  2. Change `RegisterHandler` from `CommandHandler<RegisterCommand, AuthToken>` to `CommandHandler<RegisterCommand, RegisterResult>`
  3. Remove `promotionResultHolder: ThreadLocal<PromotionResult?>` and `lastPromotionResult: PromotionResult?`
  4. Return `RegisterResult.Success(authToken, promotionResult)` from `handle()`
  5. Update `CqrsAuthController.register()` to unwrap `RegisterResult`:
     ```kotlin
     val result = registerHandler.handle(command)
     when (result) {
         is RegisterResult.Success -> {
             val response = AuthResponse.from(result.authToken)
             // Use result.promotionResult for promotion metadata
         }
     }
     ```
- **Validation**:
  - RegisterHandler no longer has ThreadLocal or `lastPromotionResult`
  - Promotion result available via return type, not side channel
  - No temporal coupling between handler and controller
- **⚠️ Assumption**: `CommandHandler<C, R>` from eventsourcing-utils supports sealed class as R type parameter. If not → use `data class RegisterResult(val authToken: AuthToken, val promotionResult: PromotionResult? = null)` instead.

### FIX-003: Observability Metrics [🟡 GAP — Operations]
- **Current behavior**: Zero Micrometer metrics for anonymous session lifecycle. No production monitoring capability.
- **Expected behavior**: Standard Micrometer counters and timers for all anonymous operations, exposed via Spring Boot Actuator (`/actuator/prometheus`).
- **Fix specification**:
  - Inject `io.micrometer.core.instrument.MeterRegistry` into 5 anonymous service classes
  - Add counters and timers as specified:

  | Metric Name | Type | Tags | Class | When |
  |-------------|------|------|-------|------|
  | `auth.anonymous.sessions.created` | Counter | — | `AnonymousSessionHandler` | After successful session creation |
  | `auth.anonymous.sessions.renewed` | Counter | — | `RenewAnonymousTokenHandler` | After successful token renewal |
  | `auth.anonymous.sessions.promoted` | Counter | `status=[SUCCESS,PARTIAL,FAILED,CONFLICT]` | `SessionPromotionService` | After promotion attempt |
  | `auth.anonymous.rate_limited` | Counter | — | `AnonymousRateLimitService` | When rate limit exceeded |
  | `auth.anonymous.data.stored` | Counter | — | `AnonymousSessionDataService` | After successful data store |
  | `auth.anonymous.data.size_exceeded` | Counter | — | `AnonymousSessionDataService` | When size limit exceeded |
  | `auth.anonymous.promotion.duration` | Timer | — | `SessionPromotionService` | Duration of `promoteSession()` |
  | `auth.anonymous.token.generation.duration` | Timer | — | `AnonymousSessionHandler` | Duration of token generation |

- **Implementation pattern** (following Spring Boot conventions):
  ```kotlin
  @Service
  class AnonymousSessionHandler(
      private val jwtService: JwtService,
      private val anonymousRateLimitService: AnonymousRateLimitService,
      private val redisTemplate: StringRedisTemplate,
      private val securityProperties: SecurityProperties,
      private val meterRegistry: MeterRegistry  // ← NEW
  ) : CommandHandler<CreateAnonymousSessionCommand, AnonymousSessionResult> {
  
      override fun handle(command: CreateAnonymousSessionCommand): AnonymousSessionResult {
          // ...existing logic...
          val sample = Timer.start(meterRegistry)
          val result = // thservice/auth/application/SessionPromotionServiceTest.kt`
  - `src/test/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitServiceTest.kt`
- **Test focus areas**:
  - JwtService: anonymous token generation (correct claims), parsing (type validation), invalid token rejection
  - AnonymousSessionDataService: store/read/delete, size limit enforcement, session expiry handling
  - SessionPromotionService: lock acquisition, data transfer, blacklisting, lock release, partial failure handling
  - AnonymousRateLimitService: under limit allow, at limit deny, window expiry reset, Redis failure fail-open
- **Validation**: All unit tests pass. Each public method has at least happy-path + error-path coverage.

## 4. Non-functional Requirements

| NFR-ID | Loại | Yêu cầu | Target | Status |
|--------|------|---------|--------|--------|
| NFR-001 | Performance | Anonymous token generation response time | < 100ms P95 | ✅ Met (existing impl) |
| NFR-002 | Security | Anonymous token creation rate limiting | Max 5/IP/hour | ✅ Met (existing impl) |
| NFR-003 | Reliability | Session promotion atomicity | No partial state on failure | 🔴 FIX-001 (JTI not blacklisted) |
| NFR-004 | Performance | Promotion overhead added to login | < 50ms additional | ✅ Met (existing impl) |
| NFR-005 | Scalability | Concurrent anonymous sessions | 10,000+ simultaneous | ✅ Met (existing impl) |
| NFR-006 | Performance | Session data read/write latency | < 20ms P95 | ✅ Met (existing impl) |
| NFR-007 | Security | Anonymous token scope limitation | ROLE_ANONYMOUS only | ✅ Met (existing impl) |
| NFR-008 | Reliability | Redis failure handling | Fail-open for rate limiting | ✅ Met (existing impl) |
| NFR-009 | Observability | Anonymous session lifecycle metrics | Micrometer counters/timers | 🟡 FIX-003 (not yet) |
| NFR-010 | Quality | Test coverage for anonymous feature | >80% code coverage | 🔴 TEST-001/002 (0% currently) |

## 5. API Changes (FIX-001 only)

### Modified Endpoints

| Method | Path | Change | Breaking? |
|--------|------|--------|-----------|
| `POST` | `/api/auth/login` | Add optional `anonymousToken: String?` to request body | No — field is optional |
| `POST` | `/api/auth/register` | Add optional `anonymousToken: String?` to request body | No — field is optional |

### Request Body Changes

**LoginRequestDto** (after FIX-001):
```json
{
  "username": "string",
  "password": "string",
  "domainCode": "string?",
  "captchaToken": "string?",
  "anonymousSessionId": "string?",
  "anonymousToken": "string?"
}
```

**RegisterRequestDto** (after FIX-001):
```json
{
  "username": "string",
  "email": "string",
  "password": "string",
  "fullName": "string",
  "anonymousSessionId": "string?",
  "anonymousToken": "string?"
}
```

> **Design decision (DD-012)**: Full JWT in request body (not header) — server validates token before trusting JTI. Consistent with existing `anonymousSessionId` field being in body.

## 6. Traceability Matrix

| FR-ID | Original Status | MAINTENANCE Fix | Validation |
|-------|----------------|-----------------|------------|
| FR-001 | ✅ Implemented | FIX-003 (metrics) | Counter increments on create |
| FR-002 | ✅ Implemented | FIX-003 (metrics) | Timer records session init |
| FR-003 | ✅ Implemented | FIX-001 (JTI), FIX-002 (RegisterResult) | JTI correctly blacklisted, no ThreadLocal |
| FR-004 | ✅ Implemented | — | TEST-001 validates transfer |
| FR-005 | 🔴 BUG | FIX-001 (JTI blacklisting) | Correct JTI in token_blacklist |
| FR-006 | ✅ Implemented | FIX-003 (metrics) | Counter increments on store |
| FR-007 | ✅ Implemented | FIX-003 (metrics) | Counter increments on size exceeded |
| FR-008 | ✅ Implemented | FIX-003 (metrics) | Counter increments on renewal |
| FR-009 | ✅ Implemented | FIX-003 (metrics) | Counter increments on rate limit |
| FR-010 | ✅ Implemented | FIX-003 (metrics) | Counter increments on promotion |
| FR-011 | ✅ Implemented | — | TEST-001 validates ROLE_ANONYMOUS |
| FR-012 | ✅ Implemented | — | Config already working |
| FR-013 | ✅ Implemented | FIX-002 (RegisterResult) | Register response includes promotion metadata |

**Coverage: 13/13 FRs addressed (5 with fixes, 8 with tests only)**

## 7. Assumptions

- ⚠️ Assumption: `CommandHandler<C, R>` from eventsourcing-utils supports sealed class as R type parameter — if not, use plain data class for RegisterResult
- ⚠️ Assumption: Spring Boot Actuator + Micrometer available in project (evidenced by `/actuator/**` permitAll in SecurityConfig)
- ⚠️ Assumption: Full JWT string in request body is acceptable (client already has the anonymous token — sending it in body is natural)
- ⚠️ Assumption: Redis testcontainer or embedded Redis available for integration tests

## 8. Open Questions

- ⚠️ OPEN QUESTION: Should `anonymousToken` field in request be the full JWT string? → Recommendation: Yes — server validates token before trusting JTI. Sending only JTI allows spoofing.
- ⚠️ OPEN QUESTION: Should `RegisterHandler` implement `CommandHandler<RegisterCommand, RegisterResult>` or should we use a different pattern? → Recommendation: Change to RegisterResult sealed class, verify eventsourcing-utils accepts sealed class type parameter.
- ⚠️ OPEN QUESTION: What Grafana dashboard panels should be created for anonymous session monitoring? → Recommendation: Out of scope — define metric names consistently, dashboards can be created separately.
