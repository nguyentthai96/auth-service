<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Query", factory: "N/A", feature_type: "EXTEND", transaction_flow: "Query" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: jwt-token-validation

_Generated: 2025-08-26_
_Profile: Query | N/A | EXTEND_
_FRs: 16 (FR-001 → FR-016)_

---

## Task Summary

| # | Task | Action | File | FR |
|---|------|--------|------|-----|
| 1 | SecurityProperties — add blacklist + JWT config | MODIFY | `SecurityProperties.kt` | FR-005,FR-006,FR-010 |
| 2 | ClaimValidationResult + ClaimValidator interface | NEW | `ClaimValidationResult.kt`, `ClaimValidator.kt`, `ClaimValidationException.kt` | FR-004 |
| 3 | IssuerClaimValidator | NEW | `IssuerClaimValidator.kt` | FR-004 |
| 4 | AudienceClaimValidator | NEW | `AudienceClaimValidator.kt` | FR-004,FR-010 |
| 5 | TokenTypeClaimValidator | NEW | `TokenTypeClaimValidator.kt` | FR-004 |
| 6 | ClaimValidatorChain | NEW | `ClaimValidatorChain.kt` | FR-004,FR-009 |
| 7 | TokenBlacklistCacheService | NEW | `TokenBlacklistCacheService.kt` | FR-001,FR-015,FR-016 |
| 8 | ValidationFailureReason + TokenValidationFailedEvent | NEW | `ValidationFailureReason.kt`, `TokenValidationFailedEvent.kt` | FR-012 |
| 9 | TokenEventRecorder — add recordValidationFailure | MODIFY | `TokenEventRecorder.kt` | FR-012 |
| 10 | TokenStorePersistenceAdapter — write-through | MODIFY | `TokenStorePersistenceAdapter.kt` | FR-002,FR-016 |
| 11 | JwtService — clock skew, key rotation, aud claim | MODIFY | `JwtService.kt` | FR-005,FR-006,FR-013 |
| 12 | TokenDtos — RFC 7662 fields | MODIFY | `TokenDtos.kt` | FR-007 |
| 13 | TokenController — cache blacklist, RFC 7662, ETag | MODIFY | `TokenController.kt` | FR-007,FR-008,FR-011 |
| 14 | JwtAuthFilter — cache blacklist, claim chain, events, logging | MODIFY | `JwtAuthFilter.kt` | FR-003,FR-009,FR-012,FR-014 |

---

## Tasks

- [ ] **Task 1: SecurityProperties — add blacklist + JWT configuration properties**
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` | Action: [MODIFY]
  - FR: FR-005 (clock skew), FR-006 (key rotation), FR-010 (audience)
  - Source: existing `SecurityProperties.kt` — `JwtProperties` data class
  - Context: Add to `JwtProperties`: `clockSkewSeconds: Long = 60`, `audience: String = ""`, `previousPublicKeyPath: String = ""`, `previousKeyId: String = ""`. Add new nested class `BlacklistCacheProperties` with: `caffeineTtlSeconds: Long = 30`, `caffeineMaxSize: Long = 10000`, `redisTimeoutMs: Long = 200`, `redisKeyPrefix: String = "token:blacklist:"`, `circuitBreakerThreshold: Int = 5`, `circuitBreakerResetSeconds: Long = 30`. Add new nested class `ValidationEventProperties` with: `recordExpiredEvents: Boolean = false`. Add properties `blacklist: BlacklistCacheProperties = BlacklistCacheProperties()` and `validationEvent: ValidationEventProperties = ValidationEventProperties()` to main `SecurityProperties`.

- [ ] **Task 2: ClaimValidationResult, ClaimValidator interface, ClaimValidationException**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidationResult.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidator.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidationException.kt` | Action: [NEW]
  - FR: FR-004
  - Context: `ClaimValidationResult` data class with `validatorName: String`, `status: ClaimValidationStatus`, `reason: String?`. Enum `ClaimValidationStatus { PASS, FAIL }`. `ClaimValidator` interface with `val name: String` and `fun validate(claims: Claims): ClaimValidationResult`. `ClaimValidationException(val validatorName: String, override val message: String) : RuntimeException(message)`. All in package `com.ntt.authservice.auth.application`.

- [ ] **Task 3: IssuerClaimValidator**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/IssuerClaimValidator.kt` | Action: [NEW]
  - FR: FR-004
  - Source: `SecurityProperties.JwtProperties.issuer`
  - Context: @Component implementing ClaimValidator. Validates `claims.issuer == securityProperties.jwt.issuer`. Returns PASS if match, FAIL with reason "Issuer mismatch: expected={expected}, actual={actual}".

- [ ] **Task 4: AudienceClaimValidator**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AudienceClaimValidator.kt` | Action: [NEW]
  - FR: FR-004, FR-010
  - Source: `SecurityProperties.JwtProperties.audience`
  - Context: @Component implementing ClaimValidator. If `securityProperties.jwt.audience.isBlank()` → always PASS (disabled). Otherwise, check `claims.audience` contains configured value. FAIL if aud claim missing or doesn't contain expected audience.

- [ ] **Task 5: TokenTypeClaimValidator**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/TokenTypeClaimValidator.kt` | Action: [NEW]
  - FR: FR-004
  - Context: @Component implementing ClaimValidator. Extracts `claims["type"] as? String`. Allowed values: null, "access", "anonymous". Rejects "mfa", "refresh" with FAIL reason "Token type '{type}' not allowed as access token".

- [ ] **Task 6: ClaimValidatorChain**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidatorChain.kt` | Action: [NEW]
  - FR: FR-004, FR-009
  - Context: @Component collecting `List<ClaimValidator>` via constructor injection. Methods: `validateOrThrow(claims: Claims)` — iterates validators, on first FAIL throws `ClaimValidationException(validator.name, result.reason)`. `validateAll(claims: Claims): List<ClaimValidationResult>` — runs all validators, returns all results.

- [ ] **Task 7: TokenBlacklistCacheService**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt` | Action: [NEW]
  - FR: FR-001, FR-015, FR-016
  - Source: `TokenBlacklistRepository`, `StringRedisTemplate`, `SecurityProperties`
  - Context: @Service with Caffeine `Cache<String, Boolean>` (programmatic, NOT Spring Cache), `StringRedisTemplate`, `TokenBlacklistRepository`. Method `isBlacklisted(jti: String): Boolean` — L1 Caffeine getIfPresent → L2 Redis EXISTS `token:blacklist:{jti}` → DB `existsByTokenJti`. If found at L2/DB → populate L1. Method `addToBlacklist(jti: String, remainingSeconds: Long)` — write L1 + L2 Redis SET EX. Circuit breaker: `AtomicInteger` consecutive failure counter + `AtomicLong` lastFailureTime. After threshold → skip Redis for reset duration. Redis timeout via `redisTemplate.execute()` with configured timeout. Write retry: 1 retry with 100ms delay on Redis write failure.

- [ ] **Task 8: ValidationFailureReason enum + TokenValidationFailedEvent**
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenValidationFailedEvent.kt` | Action: [NEW]
  - FR: FR-012
  - Source: existing `DomainEvent` interface pattern (see `TokenIssuedEvent`, `TokenRevokedEvent`)
  - Context: `enum class ValidationFailureReason { BLACKLISTED, SIGNATURE_INVALID, AUDIENCE_MISMATCH, TYPE_REJECTED }`. `data class TokenValidationFailedEvent(val reason: ValidationFailureReason, val tokenJti: String?, val ipAddress: String?, val userAgent: String?, val validatorName: String?, val failedAt: Instant = Instant.now()) : DomainEvent { override val eventType = "iam.token.validation-failed" }`. Package: `auth.domain.event`.

- [ ] **Task 9: TokenEventRecorder — add recordValidationFailure method**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt` | Action: [MODIFY]
  - FR: FR-012
  - Source: existing `recordIssuance()` and `recordRevocation()` patterns
  - Context: Add method `recordValidationFailure(event: TokenValidationFailedEvent, correlationId: String?)`. Uses `eventService.record()` with aggregateType="Token", aggregateId=0L (no user context), topic="iam.token.validation-failed", partitionKey=event.tokenJti ?: "unknown". Wrap in try-catch, log warn on failure. Follow exact pattern of `recordIssuance()`.

- [ ] **Task 10: TokenStorePersistenceAdapter — write-through to cache**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt` | Action: [MODIFY]
  - FR: FR-002, FR-016
  - Source: existing `blacklistToken()` method
  - Context: Inject `TokenBlacklistCacheService`. After existing DB save in `blacklistToken()`, call `tokenBlacklistCacheService.addToBlacklist(jti, remainingSeconds)` where `remainingSeconds = Duration.between(Instant.now(), expiresAt).seconds`. Wrap cache write in try-catch (DB is source of truth).

- [ ] **Task 11: JwtService — clock skew, key rotation, aud claim**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` | Action: [MODIFY]
  - FR: FR-005, FR-006, FR-013
  - Source: existing `parseToken()`, `getJwks()`, `generateAccessToken()` methods
  - Context:
    - **FR-005 Clock skew**: In `parseToken()`, add `.clockSkewSeconds(securityProperties.jwt.clockSkewSeconds)` to BOTH RS256 and HMAC parser builders (before `.build()`).
    - **FR-006 Key rotation**: Add `previousKeyPair: KeyPair?` lazy property that loads from `previousPublicKeyPath` when configured. In `parseToken()`: RS256(current) → RS256(previous) → HMAC(legacy). In `getJwks()`: return both keys when previous configured.
    - **FR-013 Audience**: In `generateAccessToken()`, add `.claim("aud", securityProperties.jwt.audience)` when `securityProperties.jwt.audience.isNotBlank()`.

- [ ] **Task 12: TokenDtos — RFC 7662 fields**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt` | Action: [MODIFY]
  - FR: FR-007
  - Context: Add to `IntrospectionResponse`: `tokenType: String? = null`, `scope: String? = null`, `clientId: String? = null`.

- [ ] **Task 13: TokenController — cache blacklist, RFC 7662, ETag**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt` | Action: [MODIFY]
  - FR: FR-007, FR-008, FR-011
  - Source: existing `introspect()` and `jwks()` methods
  - Context:
    - **FR-011**: Replace `TokenBlacklistRepository` with `TokenBlacklistCacheService` in constructor. Use `tokenBlacklistCacheService.isBlacklisted(jti)` in introspect.
    - **FR-007**: Inject `ClaimValidatorChain`. In introspect(), after parse, call `claimValidatorChain.validateAll(claims)` — if any FAIL → active=false. When active=true, populate `tokenType="Bearer"`, `scope=permissions.joinToString(" ")`, `clientId=claims.audience?.firstOrNull()`.
    - **FR-008**: In jwks(), compute ETag from kid(s), check `If-None-Match` header → return 304 if match. Add ETag to response.

- [ ] **Task 14: JwtAuthFilter — cache blacklist, claim chain, events, logging**
  - File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` | Action: [MODIFY]
  - FR: FR-003, FR-009, FR-012, FR-014
  - Source: existing `doFilterInternal()` method
  - Context:
    - **FR-003**: Replace `TokenBlacklistRepository` with `TokenBlacklistCacheService` in constructor. Use `tokenBlacklistCacheService.isBlacklisted(jti)` instead of `tokenBlacklistRepository.existsByTokenJti(jti)`.
    - **FR-009**: After `parseToken()` and before blacklist check, call `claimValidatorChain.validateOrThrow(claims)`. Catch `ClaimValidationException` → log + record event + continue without auth.
    - **FR-012**: Inject `TokenEventRecorder`. On blacklisted token → record `TokenValidationFailedEvent(BLACKLISTED, jti, request.remoteAddr, request.getHeader("User-Agent"))`. On claim validation failure → record event with appropriate reason.
    - **FR-014**: Replace generic `log.debug("JWT validation failed: {}", e.message)` with structured logging: WARN for blacklisted/signature/audience/type failures, DEBUG for expired/other. Include JTI where available. NEVER log raw token.
