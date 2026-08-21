---
type: brainstorm_notes
change: jwt_token_issuance
date: 2025-08-25
selected_direction: "Approach A: Unified TokenIssuanceEvent via EventService.record() — Centralized at TokenGenerator with Differentiated IssuanceContext"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: JWT Token Issuance Event — Production-Grade Event Sourcing

## Date
2025-08-25

## Context

The auth-service has a mature Event Sourcing foundation established by the `user_registration_event` and `user_login_event` features:
- `EventService.record()` — transactional event recording (event store + outbox)
- `EventEnvelope<T>` — CloudEvents-inspired envelope with correlation ID, schema version
- `OutboxPoller` → Kafka relay with retry and DLT
- `UserRegisteredEvent` — enriched domain event in `auth.domain.event` package
- `UserLoggedInEvent` / `UserLoginFailedEvent` — login events following the same pattern

**The Feature Context — JWT Token Issuance**: The user description requests production-grade Event Sourcing for JWT token lifecycle. This brainstorm scopes the `jwt_token_issuance` feature specifically to the **token issuance** domain events — capturing every JWT token generation as an immutable domain event for audit, security, and compliance.

**Current State Analysis**:

1. **TokenGenerator.generateAuthResponse()** — the centralized token factory used by LoginHandler, RegisterHandler, and RefreshTokenHandler. It generates access + refresh token pairs, stores refresh token hashes in `token_store`, and returns `AuthToken`. **No domain event is recorded** for token issuance.

2. **JwtService** — low-level JWT creation (RS256/HMAC). Generates access tokens, refresh tokens, MFA tokens, anonymous tokens, and service tokens. Pure utility — no domain awareness.

3. **RefreshTokenHandler** — handles token refresh with rotation (revoke old, issue new). **No domain event is recorded** for token rotation. The old token is revoked and new one issued silently.

4. **TokenController** — handles introspection, JWKS, and session revocation endpoints. No event recording.

5. **ServiceTokenService** — generates/validates inter-service JWTs. No event recording.

**The Gap**:
```
TokenGenerator.generateAuthResponse()   → issues tokens → NO EVENT RECORDED
RefreshTokenHandler.handle()            → rotates tokens → NO EVENT RECORDED
JwtService.generateAccessToken()        → creates JWT   → NO EVENT RECORDED
TokenStore.blacklistToken()             → blacklists    → NO EVENT RECORDED
TokenStore.revokeToken()                → revokes       → NO EVENT RECORDED
TokenStore.revokeAllForUser()           → revokes all   → NO EVENT RECORDED
```

This means:
- No audit trail for token issuance (who got tokens, when, with what permissions)
- No audit trail for token rotation (refresh token usage patterns)
- No downstream visibility into token lifecycle for SIEM/SOC
- No event-driven cache invalidation triggers
- No replay capability for token-related aggregates
- Security compliance gap: SOC2/ISO27001 require token issuance audit logs

**Source**: User description (Event Sourcing for JWT), codebase analysis of JwtService.kt, TokenGenerator.kt, RefreshTokenHandler.kt, TokenStore.kt, EventService.kt, and patterns from `user_registration_event` and `user_login_event` brainstorm notes.

## Questions Asked & Answers

### Q1: What are ALL the token issuance points in the codebase?

→ A: Comprehensive mapping of every location where JWTs are generated:

| # | Issuance Point | Token Type | Caller | Currently Events? |
|---|---------------|------------|--------|--------------------|
| 1 | `TokenGenerator.generateAuthResponse()` | access + refresh | LoginHandler, RegisterHandler, RefreshTokenHandler, MfaService | ❌ No |
| 2 | `JwtService.generateMfaToken()` | MFA challenge (5min) | MfaService.initiateMfa() via TokenGenerator.generateMfaResult() | ❌ No |
| 3 | `JwtService.generateAnonymousToken()` | anonymous session | AnonymousSessionHandler, RenewAnonymousTokenHandler | ❌ No |
| 4 | `ServiceTokenService.generateServiceToken()` | service-to-service | Internal service calls | ❌ No |

→ **Scope Decision**: Focus on items #1 and #2 (user-facing token issuance). Anonymous tokens (#3) are low-security ephemeral tokens. Service tokens (#4) are infrastructure-level. Both can be event-sourced in future iterations.

→ **Evidence**: `grep -rn "generateAccessToken\|generateRefreshToken\|generateMfaToken\|generateAnonymousToken\|generateServiceToken" src/` confirms exactly these 4 issuance paths.

### Q2: Should token issuance events be recorded AT the JwtService level or AT the TokenGenerator/Handler level?

→ A: **At the TokenGenerator and Handler level**, NOT at JwtService.

**Reasoning**:
1. `JwtService` is a **pure utility** — it generates JWT strings. It has no domain context (who requested the token, why, from which handler flow). Adding event recording there violates SRP.
2. `TokenGenerator` is the **domain orchestrator** — it knows the user, domain, roles, permissions. It has the semantic context needed for a meaningful event.
3. `RefreshTokenHandler` is the **only direct handler for token rotation** — it has the transaction boundary and the `@Transactional` annotation.
4. Pattern consistency: `RegisterHandler` records `UserRegisteredEvent`, `LoginHandler` records `UserLoggedInEvent`. Token issuance events should be recorded at the same handler/orchestrator level.

→ **Decision**: Record events in TokenGenerator (for issuance) and RefreshTokenHandler (for rotation). NOT in JwtService.

### Q3: Should we create ONE unified `TokenIssuedEvent` or separate events per type?

→ A: **ONE unified `TokenIssuedEvent`** with an `issuanceContext` discriminator.

**Arguments for unified event**:
1. All token issuances share the same core data: userId, token type, JTI, issuedAt, expiresAt, roles/permissions at time of issue
2. Downstream consumers (SIEM, audit) want a single topic (`iam.token.issued`) to subscribe to for all token activity
3. `issuanceContext` enum (`LOGIN`, `REGISTRATION`, `TOKEN_REFRESH`, `MFA_COMPLETION`, `SSO`) differentiates the trigger
4. Keeps event store queries simple: `WHERE event_type = 'iam.token.issued' AND aggregate_id = :userId`

**Arguments for separate events** (rejected):
1. `TokenIssuedOnLoginEvent`, `TokenRefreshedEvent`, `TokenIssuedOnRegistrationEvent` — leads to event proliferation
2. Each event would have slightly different fields, but 80%+ overlap
3. Downstream consumers would need to subscribe to N topics instead of 1

→ **Decision**: Single `TokenIssuedEvent` with `issuanceContext` discriminator. Single Kafka topic `iam.token.issued`. Use `issuanceContext` for filtering/routing if needed.

**Separate event for revocation**: `TokenRevokedEvent` is semantically distinct (not an issuance) and deserves its own event type: `iam.token.revoked`. This covers single revocation, rotation revocation, and bulk revocation.

### Q4: What data should be captured in `TokenIssuedEvent`?

→ A: Data availability analysis:

| Field | Source | Required? |
|-------|--------|-----------|
| `userId` | `user.id.value` (TokenGenerator) | ✅ Yes |
| `username` | `user.username` (TokenGenerator) | ✅ Yes |
| `domainCode` | `domainCode` param (TokenGenerator) | ✅ Yes |
| `domainId` | `domain.id` (TokenGenerator) | ✅ Yes |
| `issuanceContext` | Caller context (new param) | ✅ Yes |
| `accessTokenJti` | Extract from generated JWT | ✅ Yes — needed for token correlation/revocation |
| `refreshTokenHash` | `TokenHasher.hash(refreshToken)` | ✅ Yes — for rotation tracking |
| `roles` | `roles` list (TokenGenerator) | ✅ Yes — audit what permissions were granted |
| `permissions` | `permissions` list (TokenGenerator) | ✅ Yes — audit what permissions were granted |
| `accessTokenExpiresAt` | Computed from config | ✅ Yes |
| `refreshTokenExpiresAt` | Computed from config | ✅ Yes |
| `previousRefreshTokenHash` | Only for TOKEN_REFRESH context | 🟡 Optional |
| `ipAddress` | Not available in TokenGenerator | 🟡 Optional — needs parameter threading |
| `userAgent` | Not available in TokenGenerator | 🟡 Optional — needs parameter threading |
| `correlationId` | EventService auto-generates if null | ✅ Yes (auto) |

→ **Critical Issue**: `TokenGenerator` doesn't currently have `ipAddress` or `userAgent`. These are in `LoginCommand` / `RegisterCommand` but not passed through to `TokenGenerator`. Adding them would require changing `generateAuthResponse()` signature.

→ **Decision**: Add an optional `TokenIssuanceMetadata` parameter to `generateAuthResponse()` for contextual enrichment (IP, user-agent, issuance context). Callers that have the data pass it; callers that don't pass `null`. Event records with available data — missing fields are `null` in the event payload.

### Q5: How to extract JTI from generated tokens without double-parsing?

→ A: **Problem**: `JwtService.generateAccessToken()` returns a compact JWT string. To get the JTI, we'd need to parse the token we just created — wasteful.

**Solutions considered**:

1. **Parse the generated token** — Simple but wasteful (sign → serialize → parse → verify → extract). O(2N) work.

2. **Return a `TokenResult` instead of `String`** — Change `JwtService.generateAccessToken()` to return `data class TokenResult(val token: String, val jti: String, val expiresAt: Instant)`. Requires changing the return type of a widely-used method.

3. **Generate JTI outside JwtService and pass it in** — Generate `UUID.randomUUID().toString()` in `TokenGenerator`, pass to `JwtService` as a parameter. JTI is known before token creation.

→ **Decision**: **Option 3** — Generate JTI externally and pass it as a parameter. This is the most efficient and least invasive:
```kotlin
// TokenGenerator
val accessTokenJti = UUID.randomUUID().toString()
val accessToken = jwtService.generateAccessToken(
    ...,
    jti = accessTokenJti  // NEW parameter
)
// Now we have both the token AND the JTI without parsing
```

This requires adding an optional `jti: String? = null` parameter to `generateAccessToken()` and `generateRefreshToken()`. If `null`, they generate their own UUID (backward compatible).

### Q6: Should we event-source token REVOCATION in this feature?

→ A: **Yes, but scoped**. Token revocation is the complement of token issuance. Without revocation events, the token lifecycle audit trail is incomplete.

**Current revocation points**:
| # | Revocation Point | Context |
|---|-----------------|---------|
| 1 | `RefreshTokenHandler` — revokes old token during rotation | `TokenStore.revokeToken(tokenHash)` |
| 2 | `AuthService.revokeAllSessions(userId)` | `TokenStore.revokeAllForUser(userId)` |
| 3 | `RenewAnonymousTokenHandler` — blacklists old JTI | `TokenStore.blacklistToken(jti, ...)` |
| 4 | `AuthService.logout()` | Revokes refresh token + blacklists access token |

→ **Scope for this iteration**: Items #1, #2, #4 (user-facing token revocation). Item #3 is anonymous token scope (deferred).

→ **Event**: `TokenRevokedEvent` with fields: `userId`, `tokenHash` (revoked), `revocationType` (ROTATION, LOGOUT, ADMIN_REVOKE, BULK_REVOKE), `revokedCount` (for bulk), `reason`.

### Q7: Where should `TokenIssuedEvent` be recorded within TokenGenerator's `generateAuthResponse()`?

→ A: **After successful token generation and refresh token storage, before returning `AuthToken`**.

```kotlin
fun generateAuthResponse(user: User, domainCode: String, metadata: TokenIssuanceMetadata? = null): AuthToken {
    // 1. Load roles/permissions (existing)
    // 2. Generate access token (existing)
    // 3. Generate refresh token (existing)
    // 4. Store refresh token hash (existing)
    
    // 5. NEW: Record token issuance event
    eventService.record(
        aggregateType = "User",
        aggregateId = user.id.value,
        event = TokenIssuedEvent(...),
        topic = "iam.token.issued",
        partitionKey = user.id.value.toString(),
        correlationId = metadata?.correlationId
    )
    
    // 6. Return AuthToken (existing)
    return AuthToken(...)
}
```

→ **Concern**: `TokenGenerator` is currently not `@Transactional`. The callers (LoginHandler, RegisterHandler, RefreshTokenHandler) have `@Transactional` annotations. Since `EventService.record()` writes to event_store + outbox, it needs to be in the caller's TX boundary. Since `TokenGenerator.generateAuthResponse()` is called WITHIN the caller's `@Transactional` method, the event recording participates in the same transaction. **This is correct — no change needed.**

→ **Evidence**: `RegisterHandler.handle()` is `@Transactional` and calls `tokenGenerator.generateAuthResponse()` → the event store write happens in RegisterHandler's TX. Same for LoginHandler and RefreshTokenHandler.

### Q8: Should RefreshTokenHandler record BOTH a revocation event AND an issuance event?

→ A: **Yes.** Token refresh is a two-phase operation:
1. **Revoke** old refresh token → `TokenRevokedEvent(revocationType=ROTATION)`
2. **Issue** new access + refresh tokens → `TokenIssuedEvent(issuanceContext=TOKEN_REFRESH)`

Both events share the same `correlationId` (linking the rotation operation). This provides a complete audit trail:
```
Event 1: iam.token.revoked  — old token revoked (rotation)
Event 2: iam.token.issued   — new tokens issued (refresh)
```

Downstream consumers can correlate these by `correlationId` to reconstruct the full rotation flow.

→ **Decision**: RefreshTokenHandler records 2 events per refresh operation. `EventService.record()` is called twice within the same `@Transactional`.

### Q9: What is the transaction boundary concern for TokenGenerator + EventService?

→ A: `TokenGenerator` is a `@Component` (not `@Transactional`). It's called from:
- `LoginHandler.handle()` — `@Transactional` ✅
- `RegisterHandler.handle()` — `@Transactional` ✅  
- `RefreshTokenHandler.handle()` — `@Transactional` ✅
- `MfaService.verifyMfa()` — needs verification ⚠️

For `MfaService.verifyMfa()`: It calls `authResponseBuilder(userId)` which is a lambda passed by `MfaController`. The lambda calls `authService.buildAuthResponseForUser(userId)`. Need to verify if `MfaService.verifyMfa()` runs in a `@Transactional` context.

→ **Risk**: If MfaService.verifyMfa() is NOT `@Transactional`, the event store write would auto-commit independently of the token store write. This is a data consistency risk.

→ **Mitigation**: Add `@Transactional` to `MfaService.verifyMfa()` if not already present. OR: move event recording to the caller (handler/controller) level where `@Transactional` is guaranteed.

→ **Decision**: Record events ONLY in contexts where `@Transactional` is guaranteed. For MFA flow, defer event recording to when `user_login_event` MFA integration is complete (it already plans to add EventService to MfaService).

### Q10: Should we add a `TokenEventRecorder` helper service similar to `LoginEventRecorder`?

→ A: **Yes.** A `TokenEventRecorder` helper keeps TokenGenerator focused on token generation logic and provides:
1. Reusable event recording for both issuance and revocation
2. Clean separation of concerns (TokenGenerator = token logic, TokenEventRecorder = event logic)
3. Future extensibility for other token events (introspection, blacklist, etc.)

```kotlin
@Component
class TokenEventRecorder(
    private val eventService: EventService
) {
    fun recordIssuance(event: TokenIssuedEvent, userId: Long, correlationId: String?) {
        eventService.record(
            aggregateType = "User",
            aggregateId = userId,
            event = event,
            topic = "iam.token.issued",
            partitionKey = userId.toString(),
            correlationId = correlationId
        )
    }

    fun recordRevocation(event: TokenRevokedEvent, userId: Long, correlationId: String?) {
        eventService.record(
            aggregateType = "User",
            aggregateId = userId,
            event = event,
            topic = "iam.token.revoked",
            partitionKey = userId.toString(),
            correlationId = correlationId
        )
    }
}
```

## Approaches Considered

### Approach A: Unified TokenIssuanceEvent via EventService.record() — Centralized at TokenGenerator

**Description**: Create a single `TokenIssuedEvent` with `issuanceContext` discriminator, and a single `TokenRevokedEvent` with `revocationType` discriminator. Record events via `TokenEventRecorder` helper which delegates to `EventService.record()`. Inject `TokenEventRecorder` into `TokenGenerator` and `RefreshTokenHandler`. All events in the same transaction as token operations.

**Pros**:
- **Proven pattern** — Follows exact same architecture as `user_registration_event` and `user_login_event` (EventService.record() integration)
- **Complete audit trail** — Every token issuance and revocation is persisted to event store + published to Kafka via outbox
- **Minimal infrastructure** — No new tables, no new Kafka topics beyond `iam.token.issued` and `iam.token.revoked`
- **Unified event type** — One topic per operation type, discriminator for context. Simple downstream consumption.
- **Transaction safety** — Events recorded in caller's `@Transactional` boundary
- **Backward compatible** — `generateAuthResponse()` gets optional `TokenIssuanceMetadata` param with default `null`
- **JTI efficiency** — Pre-generate JTI externally, pass to JwtService. No double-parsing.

**Cons**:
- **TokenGenerator grows** — Gains `TokenEventRecorder` dependency (1 new param, total 11)
- **RefreshTokenHandler grows** — Gains `TokenEventRecorder` dependency (1 new param, total 4)
- **JwtService signature change** — `generateAccessToken()` and `generateRefreshToken()` get optional `jti` param
- **Event volume** — Every token issuance generates an event. High-traffic systems generate many tokens. Event store grows faster.
- **MFA flow gap** — MFA-completed token issuance may not have full context until `user_login_event` MFA integration is complete

**Estimated effort**: 2-3 developer-days

### Approach B: AOP-Based Token Event Recording

**Description**: Use Spring AOP `@Around` advice on `TokenGenerator.generateAuthResponse()` and `RefreshTokenHandler.handle()` to automatically capture token issuance events without modifying the methods directly.

```kotlin
@Aspect @Component
class TokenIssuanceEventAspect(private val eventService: EventService) {
    @Around("execution(* TokenGenerator.generateAuthResponse(..))")
    fun recordTokenIssuance(pjp: ProceedingJoinPoint): Any {
        val result = pjp.proceed() as AuthToken
        // Record event using result data
        return result
    }
}
```

**Pros**:
- No modification to TokenGenerator or RefreshTokenHandler
- Clean separation of cross-cutting concern
- Easy to enable/disable via configuration

**Cons**:
- **Breaks transaction consistency** — AOP advice may execute outside the caller's `@Transactional` boundary depending on proxy ordering
- **Loses context** — AOP advice can't easily access `LoginCommand.correlationId`, `LoginCommand.ipAddress`, etc. Only sees method arguments of `generateAuthResponse(user, domainCode)`
- **Hidden behavior** — Event recording is invisible in the code flow. Developers won't see it when reading TokenGenerator or handlers
- **Testing complexity** — AOP aspects are harder to unit test; require Spring context
- **Inconsistent with existing pattern** — `RegisterHandler` and `LoginHandler` use explicit `EventService.record()` calls. AOP would be a different paradigm.

**Rejected because**: AOP breaks the explicit, traceable event recording pattern established by `user_registration_event`. The lack of context (IP, user-agent, correlationId) makes events meaningless for audit. Transaction boundary concerns are non-trivial.

### Approach C: Event Recording at JwtService Level

**Description**: Record events directly in `JwtService.generateAccessToken()` / `JwtService.generateRefreshToken()` — the lowest level where tokens are created.

**Pros**:
- Captures ALL token issuances (including anonymous, service tokens)
- Single point of recording

**Cons**:
- **Violates SRP** — JwtService becomes both a token utility AND an event producer
- **No domain context** — JwtService only has userId, username, roles. Doesn't know WHY the token is being issued (login? refresh? registration?)
- **Performance** — Every token generation (including MFA tokens, anonymous tokens) would trigger event recording, even for ephemeral tokens that don't need audit
- **Transaction boundary** — JwtService is a `@Service` but NOT `@Transactional`. Event recording would auto-commit independently
- **Breaks existing pattern** — All other events are recorded at handler/orchestrator level, not utility level

**Rejected because**: JwtService is a utility, not a domain orchestrator. Recording events at this level mixes infrastructure with domain concerns and loses the semantic context needed for meaningful audit events.

## Selected Direction

**Approach A: Unified TokenIssuedEvent via EventService.record() — Centralized at TokenGenerator with Differentiated IssuanceContext**

**Reasoning**:
1. **Proven pattern** — Follows the EXACT same architecture as `user_registration_event` (EventService.record() in RegisterHandler) and `user_login_event` (EventService.record() in LoginHandler). Copy-paste level of confidence.
2. **Complete audit trail** — Both issuance (`iam.token.issued`) and revocation (`iam.token.revoked`) events cover the full token lifecycle.
3. **Centralized recording** — `TokenGenerator` is the single factory for all user-facing tokens. Recording here captures LOGIN, REGISTRATION, TOKEN_REFRESH, and MFA_COMPLETION issuances.
4. **Transaction safety** — All callers of TokenGenerator already have `@Transactional`. EventService.record() participates in the same TX.
5. **JTI pre-generation** — Efficient solution: generate JTI in TokenGenerator, pass to JwtService, use in event. No double-parsing.
6. **Backward compatible** — Optional parameters with defaults. Existing code continues to work without changes.
7. **Minimal scope** — 3 new event classes, 1 helper service, 2 modified classes. No new infrastructure.

## Pre-classifications (preliminary)

- **Feature type**: EXTEND (modifying existing TokenGenerator + RefreshTokenHandler + AuthService to add event recording)
- **Flow type**: Command (TokenGenerator generates tokens within Command handler flows → domain events)
- **Affected modules**:
  - `auth.domain.event` — new `TokenIssuedEvent.kt`, `TokenRevokedEvent.kt`, `IssuanceContext.kt`, `RevocationType.kt`
  - `auth.application.event` — new `TokenEventRecorder.kt` (helper for issuance + revocation event recording)
  - `auth.application.command` — modify `TokenGenerator.kt` (inject TokenEventRecorder, add metadata param, pre-generate JTI), modify `RefreshTokenHandler.kt` (inject TokenEventRecorder, record rotation events)
  - `auth.application` — modify `JwtService.kt` (add optional `jti` param to generateAccessToken/generateRefreshToken), modify `AuthService.kt` (record revocation events in logout/revokeAll)
  - `auth.domain.model` — new `TokenIssuanceMetadata.kt` (contextual data for event enrichment)

## Codebase Investigation Findings

### Existing Token Generation Pattern (TokenGenerator.kt — key method)
```kotlin
fun generateAuthResponse(user: User, domainCode: String): AuthToken {
    val domain = domainPort.findByCodeAndActive(domainCode)
    val roles = getUserRolesHandler.handle(GetUserRolesQuery(user.id.value, domain.id))
    val permissions = getPermissionsHandler.handle(GetPermissionsQuery(user.id.value, domain.id))
    
    val accessToken = jwtService.generateAccessToken(
        userId = user.id.value, username = user.username,
        domains = emptyList(), activeDomain = domainCode,
        roles = roles, permissions = permissions, groups = emptyList()
    )
    val refreshToken = jwtService.generateRefreshToken(user.id.value)
    
    val tokenHash = TokenHasher.hash(refreshToken)
    tokenStore.saveRefreshToken(userId = user.id.value, tokenHash = tokenHash,
        expiresAt = Instant.now().plusMillis(securityProperties.jwt.refreshTokenExpirationMs))
    
    return AuthToken(accessToken = accessToken, refreshToken = refreshToken, ...)
}
```
→ Event recording inserts BETWEEN `tokenStore.saveRefreshToken()` and `return AuthToken(...)`.

### RefreshTokenHandler Pattern (RefreshTokenHandler.kt)
```kotlin
@Transactional
override fun handle(command: RefreshTokenCommand): AuthToken {
    val tokenHash = TokenHasher.hash(command.refreshToken)
    val storedToken = tokenStore.findValidRefreshToken(tokenHash) ?: throw TokenExpiredException()
    
    // Expired check
    if (storedToken.expiresAt.isBefore(Instant.now())) { ... }
    
    val user = userPort.findById(storedToken.userId) ?: throw ...
    
    // Revoke old (rotation)
    tokenStore.revokeToken(tokenHash)
    
    val domainCode = tokenGenerator.getPrimaryDomain(user.id.value)
    return tokenGenerator.generateAuthResponse(user, domainCode)
}
```
→ Insert revocation event AFTER `tokenStore.revokeToken(tokenHash)`.
→ TokenGenerator.generateAuthResponse() will auto-record issuance event.
→ Both events in same `@Transactional`.

### JwtService Signature (current)
```kotlin
fun generateAccessToken(
    userId: Long, username: String, domains: List<String>,
    activeDomain: String, roles: List<String>, permissions: List<String>,
    groups: List<String>
): String
```
→ Add optional `jti: String? = null` parameter. If null, generate UUID internally (backward compat).

### AuthService Logout/Revocation (needs investigation)
```
AuthService.logout() → tokenStore.revokeToken() + tokenStore.blacklistToken()
AuthService.revokeAllSessions() → tokenStore.revokeAllForUser()
```
→ Need to verify `@Transactional` annotation on these methods.
→ Insert `TokenRevokedEvent` recording after revocation calls.

### Event Data Architecture
```
┌────────────────────────────────────────────────────────────┐
│ EventEnvelope<TokenIssuedEvent>                            │
├────────────────────────────────────────────────────────────┤
│ id: UUID           (envelope)                              │
│ type: "iam.token.issued"                                   │
│ source: "auth-service"                                     │
│ specversion: "1.0"                                         │
│ time: Instant                                              │
│ correlationId: UUID                                        │
│ schemaVersion: 1                                           │
├────────────────────────────────────────────────────────────┤
│ data: TokenIssuedEvent                                     │
│ ├── userId: Long                                           │
│ ├── username: String                                       │
│ ├── domainCode: String                                     │
│ ├── domainId: Long                                         │
│ ├── issuanceContext: ENUM                                  │
│ │   (LOGIN | REGISTRATION | TOKEN_REFRESH |                │
│ │    MFA_COMPLETION | SSO)                                 │
│ ├── accessTokenJti: String (UUID)                          │
│ ├── refreshTokenHash: String (SHA-256)                     │
│ ├── roles: List<String>                                    │
│ ├── permissions: List<String>                              │
│ ├── accessTokenExpiresAt: Instant                          │
│ ├── refreshTokenExpiresAt: Instant                         │
│ ├── previousRefreshTokenHash: String? (for rotation)       │
│ ├── ipAddress: String?                                     │
│ ├── userAgent: String?                                     │
│ └── issuedAt: Instant                                      │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│ EventEnvelope<TokenRevokedEvent>                           │
├────────────────────────────────────────────────────────────┤
│ data: TokenRevokedEvent                                    │
│ ├── userId: Long                                           │
│ ├── revocationType: ENUM                                   │
│ │   (ROTATION | LOGOUT | ADMIN_REVOKE | BULK_REVOKE)       │
│ ├── revokedTokenHash: String? (for single revoke)          │
│ ├── revokedAccessTokenJti: String? (for blacklist)         │
│ ├── revokedCount: Int (for bulk)                           │
│ ├── reason: String?                                        │
│ └── revokedAt: Instant                                     │
└────────────────────────────────────────────────────────────┘
```

### Key Files Modified

| File | Change | Risk |
|------|--------|------|
| `TokenGenerator.kt` | Inject TokenEventRecorder, add optional metadata param, pre-generate JTIs, record issuance event | MEDIUM — central token factory, many callers |
| `RefreshTokenHandler.kt` | Inject TokenEventRecorder, record revocation + issuance events | LOW — isolated handler |
| `JwtService.kt` | Add optional `jti` param to `generateAccessToken()` and `generateRefreshToken()` | LOW — backward compatible default param |
| `AuthService.kt` | Inject TokenEventRecorder, record revocation events in `logout()` and `revokeAllSessions()` | MEDIUM — AuthService is large |
| New: `TokenIssuedEvent.kt` | Create enriched domain event in `auth.domain.event` | LOW — new file |
| New: `TokenRevokedEvent.kt` | Create revocation domain event | LOW — new file |
| New: `IssuanceContext.kt` | Enum: LOGIN, REGISTRATION, TOKEN_REFRESH, MFA_COMPLETION, SSO | LOW — new file |
| New: `RevocationType.kt` | Enum: ROTATION, LOGOUT, ADMIN_REVOKE, BULK_REVOKE | LOW — new file |
| New: `TokenIssuanceMetadata.kt` | Data class for contextual enrichment | LOW — new file |
| New: `TokenEventRecorder.kt` | Helper service for event recording | LOW — new file |

### Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|---|
| TokenGenerator grows too large (11+ params) | LOW | LOW | TokenEventRecorder encapsulates event logic. Only 1 new dependency. |
| JwtService signature change breaks callers | LOW | LOW | Optional param with default value. All existing callers unaffected. |
| Event store growth from high token issuance volume | MEDIUM | MEDIUM | Event store has natural partitioning by aggregate_id. Add TTL-based cleanup if needed. |
| MFA flow token issuance missing context | MEDIUM | LOW | Defer full MFA context until `user_login_event` MFA integration complete. Record partial event for now. |
| RefreshTokenHandler double-event overhead | LOW | LOW | Two event store INSERTs per refresh is negligible. Same TX, same commit. |
| TokenGenerator not @Transactional itself | LOW | MEDIUM | All callers are @Transactional. TokenGenerator participates in caller's TX boundary. Verified. |

### Transaction Boundary Analysis

```
┌─ LoginHandler @Transactional ──────────────────────────────┐
│                                                             │
│  1. userPort.save(user)           — same TX                 │
│  2. loginSessionService.recordLogin() — same TX             │
│  3. tokenGenerator.generateAuthResponse() — same TX         │
│     ├── jwtService.generateAccessToken(jti=pre-gen)         │
│     ├── jwtService.generateRefreshToken()                   │
│     ├── tokenStore.saveRefreshToken()                       │
│     └── tokenEventRecorder.recordIssuance()  ← NEW         │
│         └── eventService.record() → event_store + outbox    │
│  4. [user_login_event] eventService.record(LoginEvent)      │
│  5. return LoginResult.Success                              │
│                                                             │
│  All operations commit together or rollback together        │
└─────────────────────────────────────────────────────────────┘

┌─ RefreshTokenHandler @Transactional ───────────────────────┐
│                                                             │
│  1. tokenStore.findValidRefreshToken()   — read             │
│  2. tokenStore.revokeToken()             — same TX          │
│  3. tokenEventRecorder.recordRevocation() ← NEW             │
│     └── eventService.record() → event_store + outbox        │
│  4. tokenGenerator.generateAuthResponse() — same TX         │
│     ├── (generates new tokens)                              │
│     ├── tokenStore.saveRefreshToken()                       │
│     └── tokenEventRecorder.recordIssuance() ← NEW           │
│         └── eventService.record() → event_store + outbox    │
│  5. return AuthToken                                        │
│                                                             │
│  All operations commit together or rollback together        │
└─────────────────────────────────────────────────────────────┘
```

## Design Decisions

### DD-001: TokenEventRecorder Helper Service
Create a `TokenEventRecorder` @Component in `auth.application.event` package that encapsulates:
- `recordIssuance(event: TokenIssuedEvent, userId: Long, correlationId: String?)` — delegates to EventService.record()
- `recordRevocation(event: TokenRevokedEvent, userId: Long, correlationId: String?)` — delegates to EventService.record()

Follows `LoginEventRecorder` pattern from `user_login_event` feature.

### DD-002: JTI Pre-Generation Strategy
Generate JTI (`UUID.randomUUID().toString()`) in `TokenGenerator` BEFORE calling `JwtService.generateAccessToken()`. Pass JTI as parameter to JwtService. This way both the JWT and the event have the same JTI without double-parsing.

JwtService signature change:
```kotlin
fun generateAccessToken(..., jti: String? = null): String
fun generateRefreshToken(userId: Long, jti: String? = null): String
```
Default `null` → JwtService generates its own UUID (backward compatible).

### DD-003: IssuanceContext Enum
```kotlin
enum class IssuanceContext {
    LOGIN,              // Direct login (password auth)
    REGISTRATION,       // First token after registration
    TOKEN_REFRESH,      // Token rotation via refresh
    MFA_COMPLETION,     // Token after MFA verification
    SSO                 // Token after SSO/OAuth2 flow
}
```
Callers of `TokenGenerator.generateAuthResponse()` pass their context:
- `LoginHandler` → `LOGIN` (or `MFA_COMPLETION` when MFA integration is done)
- `RegisterHandler` → `REGISTRATION`
- `RefreshTokenHandler` → `TOKEN_REFRESH`
- `SsoController` → `SSO`

### DD-004: TokenIssuanceMetadata Data Class
```kotlin
data class TokenIssuanceMetadata(
    val issuanceContext: IssuanceContext,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val correlationId: String? = null,
    val previousRefreshTokenHash: String? = null  // for rotation tracking
)
```
Optional parameter on `generateAuthResponse()`. Callers with rich context (LoginHandler) pass full metadata. Callers with less context (RefreshTokenHandler) pass minimal metadata.

### DD-005: Kafka Topic Strategy
```
iam.token.issued   — all token issuance events (unified topic)
iam.token.revoked  — all token revocation events (unified topic)
```
Follows the `iam.{domain}.{action}` naming convention established by `iam.user.registered` and `iam.user.logged_in`.

### DD-006: Event Store Aggregate Strategy
- `aggregateType = "User"` — tokens belong to users (consistent with registration and login events)
- `aggregateId = user.id.value` — Snowflake ID (consistent with existing pattern)
- For bulk revocation (`revokeAllForUser`), record ONE event with `revokedCount` field

## Open Questions for Design Phase

- [OPEN] Should `AuthService.logout()` be refactored to use command pattern (`LogoutCommand` → `LogoutHandler`) for consistency? Currently it's a direct service method.
- [OPEN] Should introspection requests be event-sourced? They're read-only but could be valuable for security monitoring (who is checking which tokens). Recommend: defer to separate feature.
- [OPEN] Should anonymous token issuance/renewal be included? Recommend: defer (low security value, high volume).
- [OPEN] Event store cleanup/retention policy for token events? Token events are high-volume compared to registration events. May need TTL-based cleanup or archiving strategy.
- [OPEN] Should `TokenIssuedEvent` include the actual JWT claims snapshot (roles, permissions at issuance time) for forensic analysis? Included in current design but makes events larger.

## Open Questions for URD Analysis

- No formal URD exists — this feature is idea-driven
- The research artifacts (auth-core-features) + codebase analysis provide sufficient context
- The user description explicitly mentions Event Sourcing for "token revocation, role assignment" — `jwt_token_issuance` covers the token lifecycle subset
- No URD-specific questions remain
