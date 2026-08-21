# Design: jwt-token-issuance

_Generated: 2025-08-25_
_Profile: Command | N/A (no factory) | EXTEND_
_Brainstorm Direction: Unified TokenIssuedEvent via EventService.record() — Centralized at TokenGenerator with Differentiated IssuanceContext_

---

## 1. Architecture Overview

### 1.1 Pattern
- **Architecture**: Clean Architecture (Hexagonal) — consistent with existing codebase
- **Package Structure**: follows existing `domain/event`, `domain/model`, `application/event`, `application/command`
- **Event Pattern**: Transactional Outbox (dual-write to event_store + event_outbox in same TX, relay to Kafka via OutboxPoller) — established by `user_registration_event`
- **CQRS**: Command-side only — events recorded within existing Command handler flows (LoginHandler, RegisterHandler, RefreshTokenHandler)

### 1.2 Component Architecture

```text
┌──────────────────────────────────────────────────────────────────────┐
│                      TOKEN ISSUANCE FLOW                              │
│                                                                       │
│  Client → POST /api/auth/login | /register | /auth/refresh            │
│      │                                                                │
│      ▼                                                                │
│  ┌─────────────────────────────────────┐                              │
│  │ LoginHandler / RegisterHandler /     │ @Transactional              │
│  │ RefreshTokenHandler                  │                             │
│  │                                      │                             │
│  │  1. Validate + business logic        │                             │
│  │  2. Create TokenIssuanceMetadata     │ ← NEW                      │
│  │     (issuanceContext, ip, ua, etc.)  │                             │
│  │  3. tokenGenerator.generateAuthResponse(user, domain, metadata)   │
│  │     ├── Pre-generate accessTokenJti  │ ← NEW (UUID)               │
│  │     ├── jwtService.generateAccessToken(jti=accessTokenJti, ...)   │
│  │     ├── jwtService.generateRefreshToken(userId)                   │
│  │     ├── tokenStore.saveRefreshToken()│                             │
│  │     └── tokenEventRecorder.recordIssuance(event, userId, corrId)  │
│  │         ├── EventService.record()    │                             │
│  │         │   ├── INSERT event_store   │ (same TX)                   │
│  │         │   └── INSERT event_outbox  │ (same TX)                   │
│  │         └── try-catch: log error, do NOT propagate                │
│  │  4. Return AuthToken                 │                             │
│  └─────────────────────────────────────┘                              │
│                                                                       │
│  ┌──────────────────────────────────────┐                             │
│  │ RefreshTokenHandler (additional)     │                             │
│  │  BEFORE generateAuthResponse():     │                             │
│  │  tokenStore.revokeToken(oldHash)    │                             │
│  │  tokenEventRecorder.recordRevocation│ ← NEW                      │
│  │    (TokenRevokedEvent(ROEvent + IssuanceContext discriminator | Single event type with enum discriminator vs N separate event classes. Simplifies downstream consumption (1 Kafka topic). |
| DD-004 | TokenIssuanceMetadata optional parameter | Backward compatible — existing callers of `generateAuthResponse(user, domainCode)` unaffected. New callers pass metadata. |
| DD-005 | Try-catch error handling in TokenEventRecorder | Token issuance is primary business operation. Event recording is secondary observability. Failure must NOT block token generation (FR-015). |
| DD-006 | Kafka topic naming: `iam.token.issued`, `iam.token.revoked` | Follows existing `iam.{domain}.{action}` convention from `iam.user.registered`. |

---

## 2. Component Design

### 2.1 New Domain Events

#### auth.domain.event.IssuanceContext (FR-002)

```kotlin
package com.ntt.authservice.auth.domain.event

/**
 * Discriminator for TokenIssuedEvent — identifies the trigger context for token issuance.
 * Used to differentiate login, registration, refresh, MFA, and SSO token issuances
 * within a single unified event type.
 */
enum class IssuanceContext {
    LOGIN,              // Direct login (password auth)
    REGISTRATION,       // First token after user registration
    TOKEN_REFRESH,      // Token rotation via refresh endpoint
    MFA_COMPLETION,     // Token after MFA verification (reserved)
    SSO                 // Token after SSO/OAuth2 flow (reserved)
}
```

#### auth.domain.event.RevocationType (FR-004)

```kotlin
package com.ntt.authservice.auth.domain.event

/**
 * Discriminator for TokenRevokedEvent — identifies the revocation context.
 */
enum class RevocationType {
    ROTATION,           // Old token revoked during refresh (rotation)
    LOGOUT,             // User-initiated logout
    ADMIN_REVOKE,       // Admin-initiated revocation
    BULK_REVOKE         // Revoke all sessions for user
}
```

#### auth.domain.event.TokenIssuedEvent (FR-001)

```kotlin
package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import java.time.Instant

/**
 * Domain event recorded when JWT tokens are issued.
 * Captures full issuance context for audit trail, security monitoring, and compliance.
 *
 * Unified event type with IssuanceContext discriminator — covers LOGIN, REGISTRATION,
 * TOKEN_REFRESH, MFA_COMPLETION, and SSO token issuances.
 *
 * Security: stores refreshTokenHash (SHA-256) NOT raw token; accessTokenJti (UUID) NOT full JWT.
 */
data class TokenIssuedEvent(
    val userId: Long,
    val username: String,
    val domainCode: String,
    val domainId: Long,
    val issuanceContext: IssuanceContext,
    val accessTokenJti: String,
    val refreshTokenHash: String,
    val roles: List<String>,
    val permissions: List<String>,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val previousRefreshTokenHash: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val issuedAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.token.issued"
}
```

#### auth.domain.event.TokenRevokedEvent (FR-003)

```kotlin
package com.ntt.authservice.auth.domain.event

import com.ntt.authservice.auth.application.port.out.DomainEvent
import java.time.Instant

/**
 * Domain event recorded when tokens are revoked.
 * Covers: rotation, logout, admin revoke, and bulk revoke operations.
 *
 * For BULK_REVOKE: revokedCount reflects total tokens revoked; revokedTokenHash is null.
 * For ROTATION/LOGOUT: revokedTokenHash identifies the specific revoked token.
 */
data class TokenRevokedEvent(
    val userId: Long,
    val revocationType: RevocationType,
    val revokedTokenHash: String? = null,
    val revokedAccessTokenJti: String? = null,
    val revokedCount: Int = 1,
    val reason: String? = null,
    val revokedAt: Instant = Instant.now()
) : DomainEvent {
    override val eventType: String = "iam.token.revoked"
}
```

### 2.2 New Domain Model

#### auth.domain.model.TokenIssuanceMetadata (FR-005)

```kotlin
package com.ntt.authservice.auth.domain.model

import com.ntt.authservice.auth.domain.event.IssuanceContext

/**
 * Contextual metadata for token issuance — passed to TokenGenerator.generateAuthResponse()
 * to enrich TokenIssuedEvent with caller context (IP, user-agent, correlationId).
 *
 * Optional parameter with backward compatibility — callers without metadata pass null.
 */
data class TokenIssuanceMetadata(
    val issuanceContext: IssuanceContext,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val correlationId: String? = null,
    val previousRefreshTokenHash: String? = null
)
```

### 2.3 New Application Services

#### auth.application.event.TokenEventRecorder (FR-006, FR-013, FR-015)

```kotlin
package com.ntt.authservice.auth.application.event

import com.ntt.authservice.auth.domain.event.TokenIssuedEvent
import com.ntt.authservice.auth.domain.event.TokenRevokedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Helper service for token lifecycle event recording.
 * Delegates to EventService.record() — encapsulates topic routing and error handling.
 *
 * Error handling (FR-015): All exceptions from EventService.record() are caught and logged.
 * Token issuance/revocation MUST NOT fail due to event recording failure.
 *
 * Follows the helper service pattern — keeps TokenGenerator focused on token logic.
 */
@Component
class TokenEventRecorder(
    private val eventService: EventService
) {

    private val log = LoggerFactory.getLogger(TokenEventRecorder::class.java)

    /**
     * Record a token issuance event.
     * Called from TokenGenerator.generateAuthResponse() after successful token generation.
     */
    fun recordIssuance(event: TokenIssuedEvent, userId: Long, correlationId: String?) {
        try {
            eventService.record(
                aggregateType = "User",
                aggregateId = userId,
                event = event,
                topic = "iam.token.issued",
                partitionKey = userId.toString(),
                correlationId = correlationId
            )
            log.debug(
                "Token issuance event recorded: userId={}, context={}, jti={}, correlationId={}",
                userId, event.issuanceContext, event.accessTokenJti, correlationId
            )
        } catch (e: Exception) {
            log.warn(
                "Failed to record token issuance event: userId={}, context={}, error={}",
                userId, event.issuanceContext, e.message, e
            )
        }
    }

    /**
     * Record a token revocation event.
     * Called from RefreshTokenHandler (ROTATION) and AuthService (LOGOUT, BULK_REVOKE).
     */
    fun recordRevocation(event: TokenRevokedEvent, userId: Long, correlationId: String?) {
        try {
            eventService.record(
                aggregateType = "User",
                aggregateId = userId,
                event = event,
                topic = "iam.token.revoked",
                partitionKey = userId.toString(),
                correlationId = correlationId
            )
            log.debug(
                "Token revocation event recorded: userId={}, type={}, count={}, correlationId={}",
                userId, event.revocationType, event.revokedCount, correlationId
            )
        } catch (e: Exception) {
            log.warn(
                "Failed to record token revocation event: userId={}, type={}, error={}",
                userId, event.revocationType, e.message, e
            )
        }
    }
}
```

### 2.4 Modified Classes

#### JwtService.kt — add optional JTI parameter (FR-007)

```kotlin
// BEFORE (L78-97):
fun generateAccessToken(
    userId: Long, username: String, domains: List<String>,
    activeDomain: String, roles: List<String>, permissions: List<String>,
    groups: List<String>
): String {
    val now = Date()
    val expiry = Date(now.time + securityProperties.jwt.accessTokenExpirationMs)
    val builder = Jwts.builder()
        .subject(userId.toString())
        ...
        .id(UUID.randomUUID().toString())
        ...
}

// AFTER:
fun generateAccessToken(
    userId: Long, username: String, domains: List<String>,
    activeDomain: String, roles: List<String>, permissions: List<String>,
    groups: List<String>,
    jti: String? = null  // NEW — pre-generated JTI for event correlation
): String {
    val now = Date()
    val expiry = Date(now.time + securityProperties.jwt.accessTokenExpirationMs)
    val resolvedJti = jti ?: UUID.randomUUID().toString()  // backward compatible
    val builder = Jwts.builder()
        .subject(userId.toString())
        ...
        .id(resolvedJti)
        ...
}

// BEFORE (L101-113):
fun generateRefreshToken(userId: Long): String {
    ...
    .id(UUID.randomUUID().toString())
    ...
}

// AFTER:
fun generateRefreshToken(userId: Long, jti: String? = null): String {
    ...
    val resolvedJti = jti ?: UUID.randomUUID().toString()
    .id(resolvedJti)
    ...
}
```

#### TokenGenerator.kt — inject TokenEventRecorder, add metadata, pre-gen JTI (FR-008)

```kotlin
// BEFORE constructor (L26-37):
@Component
class TokenGenerator(
    private val userPort: UserPort,
    private val domainPort: DomainPort,
    private val tokenStore: TokenStore,
    private val jwtService: JwtService,
    private val mfaService: MfaService,
    private val passwordEncoder: PasswordEncoder,
    private val securityProperties: SecurityProperties,
    private val getPermissionsHandler: GetPermissionsHandler,
    private val getUserRolesHandler: GetUserRolesHandler,
    private val domainLookupService: DomainLookupService
)

// AFTER constructor:
@Component
class TokenGenerator(
    private val userPort: UserPort,
    private val domainPort: DomainPort,
    private val tokenStore: TokenStore,
    private val jwtService: JwtService,
    private val mfaService: MfaService,
    private val passwordEncoder: PasswordEncoder,
    private val securityProperties: SecurityProperties,
    private val getPermissionsHandler: GetPermissionsHandler,
    private val getUserRolesHandler: GetUserRolesHandler,
    private val domainLookupService: DomainLookupService,
    private val tokenEventRecorder: TokenEventRecorder  // NEW
)

// BEFORE generateAuthResponse (L42-78):
fun generateAuthResponse(user: User, domainCode: String): AuthToken {
    ...
    val accessToken = jwtService.generateAccessToken(...)
    val refreshToken = jwtService.generateRefreshToken(user.id.value)
    val tokenHash = TokenHasher.hash(refreshToken)
    tokenStore.saveRefreshToken(...)
    return AuthToken(...)
}

// AFTER generateAuthResponse:
fun generateAuthResponse(
    user: User,
    domainCode: String,
    metadata: TokenIssuanceMetadata? = null  // NEW — backward compatible
): AuthToken {
    val domain = domainPort.findByCodeAndActive(domainCode)
        ?: throw ResourceNotFoundException("Domain", domainCode)

    val roles = getUserRolesHandler.handle(GetUserRolesQuery(user.id.value, domain.id))
    val permissions = getPermissionsHandler.handle(GetPermissionsQuery(user.id.value, domain.id))

    // Pre-generate JTI for event correlation (DD-002)
    val accessTokenJti = UUID.randomUUID().toString()

    val accessToken = jwtService.generateAccessToken(
        userId = user.id.value,
        username = user.username,
        domains = emptyList(),
        activeDomain = domainCode,
        roles = roles,
        permissions = permissions,
        groups = emptyList(),
        jti = accessTokenJti  // NEW — pass pre-generated JTI
    )

    val refreshToken = jwtService.generateRefreshToken(user.id.value)

    val tokenHash = TokenHasher.hash(refreshToken)
    tokenStore.saveRefreshToken(
        userId = user.id.value,
        tokenHash = tokenHash,
        expiresAt = Instant.now().plusMillis(securityProperties.jwt.refreshTokenExpirationMs)
    )

    // NEW: Record token issuance event (FR-008)
    val issuanceContext = metadata?.issuanceContext ?: IssuanceContext.LOGIN
    tokenEventRecorder.recordIssuance(
        event = TokenIssuedEvent(
            userId = user.id.value,
            username = user.username,
            domainCode = domainCode,
            domainId = domain.id,
            issuanceContext = issuanceContext,
            accessTokenJti = accessTokenJti,
            refreshTokenHash = tokenHash,
            roles = roles,
            permissions = permissions,
            accessTokenExpiresAt = Instant.now().plusMillis(securityProperties.jwt.accessTokenExpirationMs),
            refreshTokenExpiresAt = Instant.now().plusMillis(securityProperties.jwt.refreshTokenExpirationMs),
            previousRefreshTokenHash = metadata?.previousRefreshTokenHash,
            ipAddress = metadata?.ipAddress,
            userAgent = metadata?.userAgent
        ),
        userId = user.id.value,
        correlationId = metadata?.correlationId
    )

    return AuthToken(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = "Bearer",
        expiresIn = securityProperties.jwt.accessTokenExpirationMs / 1000,
        userId = user.id.value,
        username = user.username,
        activeDomain = domainCode,
        roles = roles,
        permissions = permissions
    )
}
```

#### RefreshTokenHandler.kt — inject TokenEventRecorder, record events (FR-009)

```kotlin
// BEFORE constructor (L19-23):
@Component
class RefreshTokenHandler(
    private val tokenStore: TokenStore,
    private val userPort: UserPort,
    private val tokenGenerator: TokenGenerator
)

// AFTER constructor:
@Component
class RefreshTokenHandler(
    private val tokenStore: TokenStore,
    private val userPort: UserPort,
    private val tokenGenerator: TokenGenerator,
    private val tokenEventRecorder: TokenEventRecorder  // NEW
)

// BEFORE handle (L29-47):
@Transactional
override fun handle(command: RefreshTokenCommand): AuthToken {
    val tokenHash = TokenHasher.hash(command.refreshToken)
    val storedToken = tokenStore.findValidRefreshToken(tokenHash) ?: throw TokenExpiredException()
    if (storedToken.expiresAt.isBefore(Instant.now())) {
        tokenStore.revokeToken(tokenHash)
        throw TokenExpiredException()
    }
    val user = userPort.findById(storedToken.userId) ?: throw ...
    tokenStore.revokeToken(tokenHash)
    val domainCode = tokenGenerator.getPrimaryDomain(user.id.value)
    return tokenGenerator.generateAuthResponse(user, domainCode)
}

// AFTER handle:
@Transactional
override fun handle(command: RefreshTokenCommand): AuthToken {
    val tokenHash = TokenHasher.hash(command.refreshToken)
    val storedToken = tokenStore.findValidRefreshToken(tokenHash)
        ?: throw TokenExpiredException()

    if (storedToken.expiresAt.isBefore(Instant.now())) {
        tokenStore.revokeToken(tokenHash)
        throw TokenExpiredException()
    }

    val user = userPort.findById(storedToken.userId)
        ?: throw ResourceNotFoundException("User", storedToken.userId)

    // Revoke old refresh token (rotation)
    tokenStore.revokeToken(tokenHash)

    // NEW: Record revocation event (FR-009)
    val correlationId = UUID.randomUUID().toString()
    tokenEventRecorder.recordRevocation(
        event = TokenRevokedEvent(
            userId = user.id.value,
            revocationType = RevocationType.ROTATION,
            revokedTokenHash = tokenHash,
            reason = "Token rotation during refresh"
        ),
        userId = user.id.value,
        correlationId = correlationId
    )

    val domainCode = tokenGenerator.getPrimaryDomain(user.id.value)

    // NEW: Pass metadata with TOKEN_REFRESH context + shared correlationId
    val metadata = TokenIssuanceMetadata(
        issuanceContext = IssuanceContext.TOKEN_REFRESH,
        previousRefreshTokenHash = tokenHash,
        correlationId = correlationId
    )
    return tokenGenerator.generateAuthResponse(user, domainCode, metadata)
}
```

#### AuthService.kt — inject TokenEventRecorder, record revocation events (FR-010)

```kotlin
// BEFORE constructor (L24-37):
@Service
class AuthService(
    private val userRepository: UserRepository,
    ...
    private val auditLogService: AuditLogService
)

// AFTER constructor:
@Service
class AuthService(
    private val userRepository: UserRepository,
    ...
    private val auditLogService: AuditLogService,
    private val tokenEventRecorder: TokenEventRecorder  // NEW
)

// BEFORE revokeAllSessions (L283-298):
@Transactional
fun revokeAllSessions(userId: Long): Int {
    val user = userRepository.findById(userId).orElseThrow { ... }
    val revokedCount = refreshTokenRepository.revokeAllByUserId(userId)
    auditLogService.logEvent(...)
    log.info("All sessions revoked for userId={}: {} tokens revoked", userId, revokedCount)
    return revokedCount
}

// AFTER revokeAllSessions:
@Transactional
fun revokeAllSessions(userId: Long): Int {
    val user = userRepository.findById(userId).orElseThrow {
        ResourceNotFoundException("User", userId)
    }
    val revokedCount = refreshTokenRepository.revokeAllByUserId(userId)

    // NEW: Record bulk revocation event (FR-010)
    tokenEventRecorder.recordRevocation(
        event = TokenRevokedEvent(
            userId = userId,
            revocationType = RevocationType.BULK_REVOKE,
            revokedCount = revokedCount,
            reason = "All sessions revoked"
        ),
        userId = userId,
        correlationId = null
    )

    auditLogService.logEvent(userId, AuditAction.SESSION_REVOKED,
        entityType = "User", entityId = userId.toString(),
        details = "revokedTokens=$revokedCount")
    log.info("All sessions revoked for userId={}: {} tokens revoked", userId, revokedCount)
    return revokedCount
}
```

#### LoginHandler.kt — pass TokenIssuanceMetadata (FR-011)

```kotlin
// BEFORE (L146):
val authToken = tokenGenerator.generateAuthResponse(user, domainCode)

// AFTER:
val metadata = TokenIssuanceMetadata(
    issuanceContext = IssuanceContext.LOGIN,
    ipAddress = command.ipAddress,
    userAgent = command.userAgent
)
val authToken = tokenGenerator.generateAuthResponse(user, domainCode, metadata)
```

#### RegisterHandler.kt — pass TokenIssuanceMetadata (FR-011)

```kotlin
// BEFORE (L111):
val authToken = tokenGenerator.generateAuthResponse(savedUser, command.domainCode)

// AFTER:
val metadata = TokenIssuanceMetadata(
    issuanceContext = IssuanceContext.REGISTRATION,
    ipAddress = command.ipAddress,
    userAgent = command.userAgent,
    correlationId = command.correlationId
)
val authToken = tokenGenerator.generateAuthResponse(savedUser, command.domainCode, metadata)
```

---

## 3. Database Design

### 3.1 No New Tables

This feature creates **no new database tables**. All token events are stored in the existing `event_store` and `event_outbox` tables created by `user_registration_event`.

### 3.2 Event Payloads (stored in event_store.payload as JSONB)

**TokenIssuedEvent** (in EventEnvelope):
```json
{
  "id": "uuid",
  "type": "iam.token.issued",
  "source": "auth-service",
  "specversion": "1.0",
  "time": "2025-08-25T10:00:00Z",
  "correlationId": "uuid",
  "schemaVersion": 1,
  "data": {
    "userId": 123456789,
    "username": "john.doe",
    "domainCode": "default",
    "domainId": 1,
    "issuanceContext": "LOGIN",
    "accessTokenJti": "uuid",
    "refreshTokenHash": "sha256-hex-64",
    "roles": ["ADMIN", "USER"],
    "permissions": ["READ", "WRITE"],
    "accessTokenExpiresAt": "2025-08-25T10:30:00Z",
    "refreshTokenExpiresAt": "2025-08-26T10:00:00Z",
    "previousRefreshTokenHash": null,
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0...",
    "issuedAt": "2025-08-25T10:00:00Z"
  }
}
```

**TokenRevokedEvent** (in EventEnvelope):
```json
{
  "id": "uuid",
  "type": "iam.token.revoked",
  "source": "auth-service",
  "data": {
    "userId": 123456789,
    "revocationType": "ROTATION",
    "revokedTokenHash": "sha256-hex-64",
    "revokedAccessTokenJti": null,
    "revokedCount": 1,
    "reason": "Token rotation during refresh",
    "revokedAt": "2025-08-25T10:30:00Z"
  }
}
```

---

## 4. Package Structure

```
src/main/kotlin/com/ntt/authservice/auth/
├── domain/
│   ├── event/
│   │   ├── EventEnvelope.kt          (existing)
│   │   ├── UserRegisteredEvent.kt     (existing)
│   │   ├── IssuanceContext.kt         [NEW] ← FR-002
│   │   ├── RevocationType.kt         [NEW] ← FR-004
│   │   ├── TokenIssuedEvent.kt        [NEW] ← FR-001
│   │   └── TokenRevokedEvent.kt       [NEW] ← FR-003
│   └── model/
│       ├── AuthToken.kt               (existing)
│       └── TokenIssuanceMetadata.kt   [NEW] ← FR-005
├── application/
│   ├── event/
│   │   ├── EventService.kt           (existing — no change)
│   │   └── TokenEventRecorder.kt     [NEW] ← FR-006
│   ├── command/
│   │   ├── TokenGenerator.kt          [MODIFY] ← FR-008
│   │   ├── RefreshTokenHandler.kt     [MODIFY] ← FR-009
│   │   ├── LoginHandler.kt            [MODIFY] ← FR-011
│   │   └── RegisterHandler.kt         [MODIFY] ← FR-011
│   ├── JwtService.kt                 [MODIFY] ← FR-007
│   └── AuthService.kt                [MODIFY] ← FR-010
```

---

## 5. Configuration

No new configuration properties required. Feature reuses existing:

```yaml
app:
  event:
    source: auth-service                    # EventService eventSource
    outbox:
      poll-interval-ms: 100               # OutboxPoller interval
      batch-size: 50                       # OutboxPoller batch
      max-retries: 3                       # OutboxPoller max retries
  security:
    jwt:
      access-token-expiration-ms: 900000   # 15 min — used for event expiresAt
      refresh-token-expiration-ms: 86400000 # 24h — used for event expiresAt
```

---

## 6. Transaction Flow (Updated)

### 6.1 Login Flow — Token Issuance

| Step | Component | Action | TX Boundary |
|------|-----------|--------|-------------|
| 1 | Client | POST /api/auth/login | — |
| 2 | CqrsAuthController | Create LoginCommand (ipAddress, userAgent) | — |
| 3 | LoginHandler | Validate credentials, MFA, session policy | @Transactional START |
| 4 | LoginHandler | Create TokenIssuanceMetadata(LOGIN, ip, ua) | Same TX |
| 5 | TokenGenerator | Pre-generate accessTokenJti (UUID) | Same TX |
| 6 | JwtService | generateAccessToken(jti=accessTokenJti, ...) | Same TX |
| 7 | JwtService | generateRefreshToken(userId) | Same TX |
| 8 | TokenStore | saveRefreshToken(tokenHash, expiresAt) | Same TX |
| 9 | TokenEventRecorder | recordIssuance(TokenIssuedEvent(...)) | Same TX |
| 9a | EventService | eventStorePort.append() → INSERT event_store | Same TX |
| 9b | EventService | outboxPort.insert() → INSERT event_outbox (PENDING) | Same TX |
| 10 | TokenGenerator | Return AuthToken | Same TX |
| 11 | — | @Transactional COMMIT | TX END |
| 12 | OutboxPoller | Async poll → relay to Kafka `iam.token.issued` | New TX |

### 6.2 Token Refresh Flow — Revocation + Issuance

| Step | Component | Action | TX Boundary |
|------|-----------|--------|-------------|
| 1 | Client | POST /api/auth/refresh | — |
| 2 | RefreshTokenHandler | Validate old refresh token | @Transactional START |
| 3 | TokenStore | revokeToken(oldTokenHash) | Same TX |
| 4 | TokenEventRecorder | recordRevocation(TokenRevokedEvent(ROTATION)) | Same TX |
| 4a | EventService | INSERT event_store (revocation event) | Same TX |
| 4b | EventService | INSERT event_outbox (revocation event, PENDING) | Same TX |
| 5 | TokenGenerator | generateAuthResponse(user, domain, metadata) | Same TX |
| 5a | TokenGenerator | Pre-generate JTI, generate tokens, save refresh | Same TX |
| 5b | TokenEventRecorder | recordIssuance(TokenIssuedEvent(TOKEN_REFRESH)) | Same TX |
| 6 | RefreshTokenHandler | Return AuthToken | Same TX |
| 7 | — | @Transactional COMMIT (both events + token store atomic) | TX END |
| 8 | OutboxPoller | Relay `iam.token.revoked` + `iam.token.issued` | New TX |

### 6.3 Bulk Revocation Flow

| Step | Component | Action | TX Boundary |
|------|-----------|--------|-------------|
| 1 | Admin | Revoke all sessions for userId | — |
| 2 | AuthService | revokeAllSessions(userId) | @Transactional START |
| 3 | RefreshTokenRepository | revokeAllByUserId(userId) → revokedCount | Same TX |
| 4 | TokenEventRecorder | recordRevocation(TokenRevokedEvent(BULK_REVOKE, count)) | Same TX |
| 5 | AuditLogService | logEvent(SESSION_REVOKED) | Same TX |
| 6 | — | @Transactional COMMIT | TX END |

---

## 7. Error Handling

| Scenario | Behavior | Impact on Token Flow |
|----------|----------|---------------------|
| EventService.record() fails (DB error) | TokenEventRecorder catches exception, logs WARN | Token issuance succeeds — event not recorded |
| EventService.record() fails (serialization) | TokenEventRecorder catches exception, logs WARN | Token issuance succeeds — event not recorded |
| TokenGenerator.generateAuthResponse() fails | Exception propagates normally | No event recorded for failed generation |
| OutboxPoller relay fails | OutboxPoller retries with existing mechanism | Event recorded in DB, Kafka delivery delayed |
| TokenStore.saveRefreshToken() fails | Exception propagates, TX rollback | No event recorded (event recording not reached) |

---

## 8. Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Domain Event class | `<Entity><Action>Event` | `TokenIssuedEvent`, `TokenRevokedEvent` |
| Enum discriminator | `<Context>` | `IssuanceContext`, `RevocationType` |
| Helper service | `<Entity>EventRecorder` | `TokenEventRecorder` |
| Metadata DTO | `<Entity><Action>Metadata` | `TokenIssuanceMetadata` |
| Kafka topic | `iam.<domain>.<action>` | `iam.token.issued`, `iam.token.revoked` |
| Event type field | `iam.<domain>.<action>` | `"iam.token.issued"`, `"iam.token.revoked"` |

---

## 9. FR Traceability

| FR-ID | Design Section | Component | Classification |
|-------|---------------|-----------|----------------|
| FR-001 | 2.1 | `TokenIssuedEvent.kt` | NEW |
| FR-002 | 2.1 | `IssuanceContext.kt` | NEW |
| FR-003 | 2.1 | `TokenRevokedEvent.kt` | NEW |
| FR-004 | 2.1 | `RevocationType.kt` | NEW |
| FR-005 | 2.2 | `TokenIssuanceMetadata.kt` | NEW |
| FR-006 | 2.3 | `TokenEventRecorder.kt` | NEW |
| FR-007 | 2.4 | `JwtService.kt` | MODIFY |
| FR-008 | 2.4 | `TokenGenerator.kt` | MODIFY |
| FR-009 | 2.4 | `RefreshTokenHandler.kt` | MODIFY |
| FR-010 | 2.4 | `AuthService.kt` | MODIFY |
| FR-011 | 2.4 | `LoginHandler.kt`, `RegisterHandler.kt` | MODIFY |
| FR-012 | 6 | OutboxPoller (existing — topic routing) | REUSE |
| FR-013 | 2.3 | `TokenEventRecorder.kt` (logging) | NEW |
| FR-014 | N/A | `EventService.record()` idempotency | REUSE |
| FR-015 | 2.3 | `TokenEventRecorder.kt` (try-catch) | NEW |
