# SRS: jwt-token-validation

_Generated: 2025-08-26_
_Profile: Query | N/A (no factory) | EXTEND_

---

## 1. System Context

- **Feature Name**: JWT Token Validation — Production-Grade Hardening
- **Domain**: Authentication — JWT Token Validation Pipeline
- **Flow**: Query (Token validation is read-only per-request check; introspection is query endpoint)
- **Services**: auth-service (EXTEND)
- **Source**: User Idea (enriched) + Research Analysis + Brainstorm Analysis

---

## 2. Actors

| Actor | Description | Primary Interactions |
|-------|-------------|---------------------|
| Client Application (End User) | Gửi HTTP request với Bearer token → trigger validation trên mỗi request | Authorization header → JwtAuthFilter |
| Hệ thống (auth-service) | Xử lý token validation, blacklist check, claim validation, set SecurityContext | JwtAuthFilter → JwtService → TokenBlacklistCacheService → ClaimValidatorChain |
| Internal Microservice | Gọi introspection endpoint hoặc JWKS endpoint để verify token | POST /api/auth/introspect, GET /.well-known/jwks.json |
| Service Administrator | Trigger key rotation, monitor validation metrics | Config change + restart |
| Infrastructure (Redis, Caffeine, PostgreSQL) | L1/L2 cache cho blacklist, DB fallback | Cache layers |

---

## 3. Functional Requirements

### 3.1 Blacklist Cache Layer

#### FR-001: Two-tier blacklist cache service [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Request received with Bearer token, token parsed successfully, JTI extracted.
- **Action**: Hệ thống tạo `TokenBlacklistCacheService` trong package `auth.application` với method `isBlacklisted(jti: String): Boolean`. Three-level lookup order: (1) L1 Caffeine cache → if found return true, (2) L2 Redis `EXISTS token:blacklist:{jti}` → if found, populate L1 cache, return true, (3) DB `tokenBlacklistRepository.existsByTokenJti(jti)` → if found, populate L1 + L2, return true. Return false nếu not found ở tất cả tiers.
- **Validation Rules**:
  - L1 Caffeine TTL = 30 seconds (configurable via `app.security.blacklist.caffeine-ttl-seconds`)
  - L2 Redis TTL = remaining token lifetime (calculated from token expiry)
  - Redis key pattern: `token:blacklist:{jti}`
  - Redis lookup timeout: 200ms (configurable via `app.security.blacklist.redis-timeout-ms`)
  - Redis unavailable → log warning, skip to DB fallback (fail-open)
- **Error Codes**: N/A (internal service, no user-facing errors)
- **Existing Code**: `TokenBlacklistRepository.existsByTokenJti()` — DB query per request (bottleneck to be wrapped)
- **Classification**: NEW

#### FR-002: Blacklist cache write-through on revocation [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Token revocation triggered (via `TokenStore.blacklistToken()`).
- **Action**: Khi token bị blacklist, hệ thống write đồng thời vào: (1) DB via `tokenBlacklistRepository.save()` (existing), (2) Redis `SET token:blacklist:{jti} "1" EX {remaining_seconds}`, (3) L1 Caffeine `put(jti, true)`. Redis write failure → log warning, continue (fail-safe — DB is source of truth).
- **Validation Rules**:
  - All 3 tiers MUST receive entry on write path
  - Redis key TTL = `(token.expiresAt - now)` in seconds; if ≤0, skip Redis write
  - Retry on Redis write failure: 1 retry with 100ms delay (FR-016)
  - Caffeine write is synchronous and cannot fail
- **Error Codes**: N/A (internal)
- **Existing Code**: `TokenStorePersistenceAdapter.blacklistToken()` (L53-61) — only writes DB
- **Classification**: MODIFY (TokenStorePersistenceAdapter)

#### FR-003: JwtAuthFilter migrate to cache-based blacklist check [IDEA]
- **Actor**: Hệ thống
- **Precondition**: `JwtAuthFilter` is processing a request with valid JWT.
- **Action**: Modify `JwtAuthFilter` constructor to inject `TokenBlacklistCacheService` instead of `TokenBlacklistRepository`. Replace `tokenBlacklistRepository.existsByTokenJti(jti)` (L48-51) with `tokenBlacklistCacheService.isBlacklisted(jti)`.
- **Validation Rules**:
  - Behavior MUST remain identical — only performance changes
  - If `jti` is null → skip blacklist check (existing behavior preserved)
  - If blacklisted → return 401, do NOT continue filter chain (existing behavior preserved)
- **Error Codes**: HTTP 401 Unauthorized (existing, unchanged)
- **Existing Code**: `JwtAuthFilter` (L48-51): `tokenBlacklistRepository.existsByTokenJti(jti)`
- **Classification**: MODIFY

#### FR-015: Redis timeout handling for cache lookup [ENRICHED]
- **Actor**: Hệ thống
- **Precondition**: `TokenBlacklistCacheService.isBlacklisted()` calling Redis L2 layer.
- **Action**: Redis operations in `TokenBlacklistCacheService` MUST have configurable timeout (`app.security.blacklist.redis-timeout-ms`, default 200ms). On timeout → log warning with JTI (no sensitive data), skip to DB fallback. On connection failure → same behavior.
- **Validation Rules**:
  - Timeout applies to both `EXISTS` (read) and `SET` (write) operations
  - Circuit breaker: after 5 consecutive Redis failures within 60s → skip Redis for 30s (configurable)
  - Thread-safe: all Caffeine and Redis operations must be thread-safe
- **Error Codes**: N/A (internal, fail-open)
- **Existing Code**: N/A (new requirement)
- **Classification**: NEW (part of TokenBlacklistCacheService)

#### FR-016: Retry mechanism for cache write failures [ENRICHED]
- **Actor**: Hệ thống
- **Precondition**: `TokenBlacklistCacheService` or `TokenStorePersistenceAdapter` writing to Redis on token revocation.
- **Action**: On Redis write failure during blacklist write-through: retry 1 time with 100ms delay. On persistent failure → log error, continue (DB write is already committed and is source of truth).
- **Validation Rules**:
  - Max 1 retry (total 2 attempts)
  - Delay 100ms between attempts
  - MUST NOT block the calling thread beyond timeout + retry delay
- **Error Codes**: N/A
- **Existing Code**: N/A
- **Classification**: NEW (part of TokenBlacklistCacheService)

### 3.2 Claim Validation Pipeline

#### FR-004: Claim validation pipeline (chain of responsibility) [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Token parsed successfully, `Claims` object available.
- **Action**: Create `ClaimValidator` interface in `auth.application` with method `validate(claims: Claims): ClaimValidationResult`. Create implementations:
  - `IssuerClaimValidator`: validates `iss == SecurityProperties.jwt.issuer`. Fails if mismatch.
  - `AudienceClaimValidator`: if `SecurityProperties.jwt.audience` is non-empty, validates `aud` claim contains the audience value. Skips if audience is empty (backward compatible, FR-010).
  - `TokenTypeClaimValidator`: validates `type` claim ∈ {null, "access", "anonymous"}. Rejects "mfa", "refresh" — these token types should not be used as access tokens.
  
  Create `ClaimValidatorChain` @Component that collects all `ClaimValidator` beans via constructor injection. Exposes:
  - `validateOrThrow(claims: Claims)`: fail-fast — throws `ClaimValidationException` on first failure (used in JwtAuthFilter hot path)
  - `validateAll(claims: Claims): List<ClaimValidationResult>`: collect-all — returns all results (used in introspection diagnostic path)
- **Validation Rules**:
  - Validators execute in registration order (Spring bean ordering)
  - `ClaimValidationResult` contains: `validatorName: String`, `status: ClaimValidationStatus` (PASS/FAIL), `reason: String?`
  - `ClaimValidationStatus` is an enum: PASS, FAIL
  - Fail-fast mode throws custom `ClaimValidationException(validatorName, reason)` (unchecked)
- **Error Codes**: N/A (validation failures handled by filter/controller, not user-facing error codes)
- **Existing Code**: Issuer validation implicit in JJWT parser. Type validation inline at JwtAuthFilter L57-77.
- **Classification**: NEW

#### FR-009: JwtAuthFilter integrate ClaimValidatorChain [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Token parsed successfully in JwtAuthFilter.
- **Action**: After `jwtService.parseToken(token)` and before blacklist check, call `claimValidatorChain.validateOrThrow(claims)`. If validation throws `ClaimValidationException` → log debug message with validator name + reason, continue without authentication (consistent with existing `catch (e: Exception)` behavior at L88-90).
- **Validation Rules**:
  - Existing behavior preserved — claim validation is defense-in-depth, not a new rejection path
  - Token type branching (anonymous vs authenticated) at L57-77 MUST still work after claim validation passes
  - `ClaimValidationException` caught alongside `JwtException` in existing catch block
- **Error Codes**: N/A (filter continues without auth on failure)
- **Existing Code**: `JwtAuthFilter.doFilterInternal()` (L30-96)
- **Classification**: MODIFY

### 3.3 JWT Configuration Enhancements

#### FR-005: Clock skew tolerance configuration [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Token being parsed by `JwtService.parseToken()`.
- **Action**: Add property `clockSkewSeconds: Long = 60` to `SecurityProperties.JwtProperties`. Apply in `JwtService.parseToken()` via JJWT `Jwts.parser().clockSkewSeconds(clockSkewSeconds)` for both RS256 and HMAC parsers.
- **Validation Rules**:
  - Default 60s per RFC 8725 recommendation
  - Configurable via `app.security.jwt.clock-skew-seconds`
  - Applied to BOTH RS256 and HMAC parser instances
  - Clock skew tolerance applies to `exp` and `nbf` claims
- **Error Codes**: N/A (misconfiguration → standard token validation errors)
- **Existing Code**: `JwtService.parseToken()` (L193-221) — no clock skew configured
- **Classification**: MODIFY

#### FR-006: JWKS key rotation — dual-key overlap [IDEA]
- **Actor**: Service Administrator
- **Precondition**: Administrator has generated new RSA key pair and updated configuration.
- **Action**: Add properties to `SecurityProperties.JwtProperties`:
  - `previousPublicKeyPath: String = ""` — path to previous RSA public key PEM
  - `previousKeyId: String = ""` — kid for previous key
  
  Modify `JwtService`:
  - Load previous key pair at startup when `previousPublicKeyPath` is configured
  - `getJwks()` returns both current and previous keys (2 entries in `keys` array)
  - `parseToken()` tries: RS256(current) → RS256(previous) → HMAC(legacy) fallback
- **Validation Rules**:
  - During overlap: JWKS contains 2 keys, validation accepts both kid values
  - Post-overlap: remove `previousPublicKeyPath` config → restart → single key in JWKS
  - Signing ALWAYS uses current key
  - Overlap period SHOULD exceed max token lifetime (7d for refresh tokens)
- **Error Codes**: N/A (startup failure if key file not found → Spring Boot fails to start)
- **Existing Code**: `JwtService.getJwks()` (L249-261) — single key. `keyPair` (L31-39) — singleton lazy load.
- **Classification**: MODIFY

#### FR-010: Audience claim configuration [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Application configured with `app.security.jwt.audience` property.
- **Action**: Add property `audience: String = ""` to `SecurityProperties.JwtProperties`. When non-empty: (1) `AudienceClaimValidator` validates `aud` claim contains the value (FR-004), (2) `JwtService.generateAccessToken()` adds `aud` claim (FR-013). When empty → skip audience validation entirely (backward compatible).
- **Validation Rules**:
  - Default empty string → disabled (feature flag pattern)
  - Enable via `app.security.jwt.audience=auth-service`
  - MUST be backward compatible — existing tokens without `aud` claim work when feature is disabled
- **Error Codes**: N/A
- **Existing Code**: `SecurityProperties.JwtProperties` — no audience property
- **Classification**: MODIFY

#### FR-013: Add aud claim to token generation [ENRICHED]
- **Actor**: Hệ thống
- **Precondition**: `SecurityProperties.jwt.audience` is non-empty.
- **Action**: Modify `JwtService.generateAccessToken()` to add `.claim("aud", securityProperties.jwt.audience)` when `securityProperties.jwt.audience.isNotBlank()`.
- **Validation Rules**:
  - Only added when audience is configured (non-empty)
  - Added to access tokens only (refresh tokens don't need audience)
  - Consistent with `AudienceClaimValidator` expectation
- **Error Codes**: N/A
- **Existing Code**: `JwtService.generateAccessToken()` (L82-100) — no aud claim
- **Classification**: MODIFY

### 3.4 Introspection & JWKS Enhancements

#### FR-007: Introspection endpoint upgrade RFC 7662 [IDEA]
- **Actor**: Internal Microservice
- **Precondition**: Internal service calls `POST /api/auth/introspect` with token.
- **Action**: Modify `TokenController.introspect()` to:
  1. Use `TokenBlacklistCacheService` instead of `TokenBlacklistRepository`
  2. Validate claims via `ClaimValidatorChain.validateAll()` — if any validator fails, `active=false`
  3. Add RFC 7662 fields to `IntrospectionResponse`: `tokenType: String?` ("Bearer"), `scope: String?` (comma-separated permissions), `clientId: String?` (from aud claim if present)
- **Validation Rules**:
  - Always returns HTTP 200 OK (RFC 7662 requirement)
  - `active=false` for: invalid token, expired token, revoked/blacklisted token, claim validation failed
  - RFC 7662 fields are nullable — only populated when `active=true`
  - `scope` derived from permissions list joined by space (RFC 7662 format)
- **Error Codes**: N/A (always 200 OK per RFC 7662)
- **Existing Code**: `TokenController.introspect()` (L27-48), `IntrospectionResponse` (L13-23) — missing RFC 7662 fields
- **Classification**: MODIFY

#### FR-008: JWKS endpoint ETag support [IDEA]
- **Actor**: Internal Microservice
- **Precondition**: Client calls `GET /.well-known/jwks.json`.
- **Action**: `TokenController.jwks()` generates ETag header based on SHA-256 hash of kid(s) in JWKS response. If `If-None-Match` request header matches current ETag → return 304 Not Modified. Otherwise return full JWKS response with ETag.
- **Validation Rules**:
  - ETag value: `"kid:{kid1}:{kid2}"` or `"kid:{kid}"` (hash of kid values)
  - `Cache-Control: max-age=86400, public` maintained (existing)
  - ETag changes when key rotation occurs (kid changes)
- **Error Codes**: N/A
- **Existing Code**: `TokenController.jwks()` (L50-54) — has `CacheControl` but no ETag
- **Classification**: MODIFY

#### FR-011: TokenController use cache-based blacklist [IDEA]
- **Actor**: Hệ thống
- **Precondition**: `TokenController.introspect()` checking blacklist status.
- **Action**: Modify `TokenController` constructor to inject `TokenBlacklistCacheService` instead of `TokenBlacklistRepository`. Use `tokenBlacklistCacheService.isBlacklisted(jti)` in `introspect()`.
- **Validation Rules**:
  - Consistent abstraction — controller does not directly call JPA repository
  - Behavior unchanged — only performance improvement
- **Error Codes**: N/A
- **Existing Code**: `TokenController` (L22) — injects `TokenBlacklistRepository` directly
- **Classification**: MODIFY

### 3.5 Audit & Logging

#### FR-012: Validation failure event recording [IDEA]
- **Actor**: Hệ thống
- **Precondition**: Token validation fails with suspicious reason (not routine expiration).
- **Action**: Create `TokenValidationFailedEvent` domain event in `auth.domain.event` implementing `DomainEvent`. Payload: `reason: ValidationFailureReason` (enum: BLACKLISTED, SIGNATURE_INVALID, AUDIENCE_MISMATCH, TYPE_REJECTED), `tokenJti: String?`, `ipAddress: String?`, `userAgent: String?`, `validatorName: String?`, `failedAt: Instant`.
  
  Add `recordValidationFailure()` to `TokenEventRecorder` following existing pattern.
  
  Record events in `JwtAuthFilter` when: token is blacklisted (FR-003 check), claim validation fails with AUDIENCE_MISMATCH or TYPE_REJECTED reason. NOT recorded for: expired tokens (routine), issuer mismatch (configurable future).
- **Validation Rules**:
  - Default: record only BLACKLISTED, SIGNATURE_INVALID, AUDIENCE_MISMATCH, TYPE_REJECTED
  - Configurable: `app.security.validation.record-expired-events: false`
  - Event topic: `iam.token.validation-failed`
  - Error handling: try-catch, NEVER fail request due to event recording failure
  - Uses `EventService.record()` via `TokenEventRecorder` — same transactional outbox pattern
- **Error Codes**: N/A (internal event)
- **Existing Code**: `TokenEventRecorder` — has `recordIssuance()` and `recordRevocation()`. `TokenValidationFailedEvent` does not exist.
- **Classification**: NEW (event) + MODIFY (TokenEventRecorder)

#### FR-014: Structured logging for validation failures [ENRICHED]
- **Actor**: Hệ thống
- **Precondition**: Token validation fails in `JwtAuthFilter`.
- **Action**: Replace generic `log.debug("JWT validation failed: {}", e.message)` with structured logging: log level based on failure type (DEBUG for expired, WARN for blacklisted/tampered), include JTI (if available), validator name, failure reason. No sensitive token content logged.
- **Validation Rules**:
  - NEVER log raw token string
  - Log JTI (UUID, non-sensitive) for correlation
  - DEBUG: expired, clock_skew, issuer_mismatch
  - WARN: blacklisted, signature_invalid, audience_mismatch, type_rejected
- **Error Codes**: N/A
- **Existing Code**: `JwtAuthFilter` L88-90: `log.debug("JWT validation failed: {}", e.message)`
- **Classification**: MODIFY

---

## 4. Non-Functional Requirements

| ID | Category | Requirement | Target |
|----|----------|-------------|--------|
| NFR-001 | Performance | Blacklist check latency (L1 hit) | < 0.1ms |
| NFR-002 | Performance | Blacklist check latency (L2 Redis hit) | < 5ms |
| NFR-003 | Performance | Blacklist check latency (DB fallback) | < 10ms |
| NFR-004 | Performance | Claim validation overhead per request | < 0.5ms |
| NFR-005 | Availability | Redis failure must not block request processing | Circuit breaker + DB fallback |
| NFR-006 | Security | Max acceptance window for revoked token | < 30 seconds (L1 TTL) |
| NFR-007 | Security | Clock skew tolerance | 60 seconds (configurable) |
| NFR-008 | Compatibility | Existing tokens without aud claim | Must work (audience disabled by default) |
| NFR-009 | Compliance | Suspicious validation failures recorded | Event sourced to Kafka |
| NFR-010 | Scalability | Cache layers independent per instance | L1 per-process, L2 shared Redis |

---

## 5. Data Model

### New Configuration Properties

```yaml
app:
  security:
    jwt:
      clock-skew-seconds: 60          # FR-005
      audience: ""                     # FR-010, FR-013 (empty = disabled)
      previous-public-key-path: ""     # FR-006
      previous-key-id: ""             # FR-006
    blacklist:
      caffeine-ttl-seconds: 30        # FR-001
      caffeine-max-size: 10000        # FR-001
      redis-timeout-ms: 200           # FR-015
      redis-key-prefix: "token:blacklist:" # FR-001
      circuit-breaker-threshold: 5    # FR-015
      circuit-breaker-reset-seconds: 30 # FR-015
    validation:
      record-expired-events: false    # FR-012
```

### New Domain Event

```kotlin
// TokenValidationFailedEvent
data class TokenValidationFailedEvent(
    val reason: ValidationFailureReason,
    val tokenJti: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val validatorName: String?,
    val failedAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.token.validation-failed"
}

enum class ValidationFailureReason {
    BLACKLISTED, SIGNATURE_INVALID, AUDIENCE_MISMATCH, TYPE_REJECTED
}
```

---

## 6. Interface Contracts

### Modified Endpoint: POST /api/auth/introspect

**Request**: unchanged (`IntrospectionRequest`)

**Response** (RFC 7662 enhanced):
```json
{
  "active": true,
  "sub": "123",
  "username": "user@example.com",
  "roles": ["ADMIN"],
  "permissions": ["user:read", "user:write"],
  "exp": 1693000000,
  "iat": 1692999000,
  "iss": "auth-service",
  "jti": "uuid-123",
  "tokenType": "Bearer",
  "scope": "user:read user:write",
  "clientId": "auth-service"
}
```

### Existing Endpoint: GET /.well-known/jwks.json

**Response** (dual-key during rotation):
```json
{
  "keys": [
    { "kty": "RSA", "kid": "auth-service-key-2", "alg": "RS256", "use": "sig", "n": "...", "e": "..." },
    { "kty": "RSA", "kid": "auth-service-key-1", "alg": "RS256", "use": "sig", "n": "...", "e": "..." }
  ]
}
```
**Headers**: `Cache-Control: max-age=86400, public`, `ETag: "kid:auth-service-key-2:auth-service-key-1"`

---

## 7. Dependencies

| Component | Type | Package | Usage |
|-----------|------|---------|-------|
| `JwtService` | Service (injected) | `auth.application` | Token parsing, JWKS, key rotation |
| `TokenBlacklistRepository` | JPA Repository | `rbac.adapter.out.persistence.repository` | DB fallback for blacklist |
| `SecurityProperties` | Configuration | `shared.config` | All configurable properties |
| `StringRedisTemplate` | Spring Bean | `org.springframework.data.redis.core` | Redis L2 cache operations |
| `Caffeine` | Library | `com.github.benmanes.caffeine` | L1 local cache |
| `EventService` | Service (injected) | `auth.application.event` | Event recording via transactional outbox |
| `TokenEventRecorder` | Service (injected) | `auth.application.event` | Token lifecycle event recording |
| `DomainEvent` | Interface | `auth.application.port.out` | Event contract |

---

## 8. Acceptance Criteria

| FR | Acceptance Criteria |
|----|-------------------|
| FR-001 | `TokenBlacklistCacheService.isBlacklisted()` checks L1 → L2 → DB in order. L1 hit returns without Redis/DB call. |
| FR-002 | `blacklistToken()` writes to all 3 tiers. Redis failure logged, DB write succeeds. |
| FR-003 | `JwtAuthFilter` uses `TokenBlacklistCacheService`. No direct `TokenBlacklistRepository` injection. |
| FR-004 | `ClaimValidatorChain` contains IssuerClaimValidator + AudienceClaimValidator + TokenTypeClaimValidator. validateOrThrow() fails fast. validateAll() collects all. |
| FR-005 | `parseToken()` accepts tokens expired up to 60s (default clock skew). |
| FR-006 | `getJwks()` returns 2 keys when previousPublicKeyPath configured. `parseToken()` accepts tokens signed by either key. |
| FR-007 | `introspect()` response contains `tokenType`, `scope`, `clientId` when active=true. |
| FR-008 | JWKS endpoint returns ETag header. Conditional request with matching ETag returns 304. |
| FR-009 | `JwtAuthFilter` calls `claimValidatorChain.validateOrThrow()`. Claim failure → no auth, request continues. |
| FR-010 | `audience=""` → no audience validation. `audience="auth-service"` → validates aud claim. |
| FR-011 | `TokenController` uses `TokenBlacklistCacheService` instead of `TokenBlacklistRepository`. |
| FR-012 | Blacklisted token presentation triggers `TokenValidationFailedEvent` recording. |
| FR-013 | `generateAccessToken()` includes `aud` claim when `audience` property is non-empty. |
| FR-014 | Blacklisted token → WARN log with JTI. Expired token → DEBUG log. No raw token logged. |
| FR-015 | Redis timeout 200ms → fallback to DB. Circuit breaker opens after 5 failures. |
| FR-016 | Redis write retry 1x with 100ms delay. Persistent failure → logged, DB write intact. |
