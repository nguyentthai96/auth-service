# DTO Pattern

_Generated: 2026-08-26_

## Request DTO

- `IntrospectionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - annotations: `@field:NotBlank(message = "Token is required")`
  - fields: `token: String`

- `LoginCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt`
  - type: Command (CQRS pattern)
  - fields: username, password, deviceInfo, ipAddress, etc.

- `RegisterCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt`
  - type: Command

- `RefreshTokenCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenCommand.kt`
  - type: Command

- `RevokeSessionsCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsCommand.kt`
  - type: Command

- `SwitchDomainCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainCommand.kt`
  - type: Command

- `CreateAnonymousSessionCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/CreateAnonymousSessionCommand.kt`
  - type: Command

- `RenewAnonymousTokenCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenCommand.kt`
  - type: Command

- `BuildAuthResponseQuery` — `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseQuery.kt`
  - type: Query (CQRS pattern)

## Response DTO

- `IntrospectionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - fields: active (Boolean), sub, username, roles (List<String>?), permissions (List<String>?), exp (Long?), iat (Long?), iss (String?), jti (String?)
  - pattern: data class with nullable optional fields

- `RevokeSessionsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - fields: revokedCount (Int), userId (Long)

- `LoginResult` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt`
  - type: Application result (not web DTO)

- `RegisterResult` — `src/main/kotlin/com/ntt/authservice/auth/application/RegisterResult.kt`
  - type: Application result

- `AnonymousSessionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionResult.kt`
  - type: Application result

- `PromotionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/PromotionResult.kt`
  - type: Application result

## Domain Model (not DTO, but relevant)

- `AuthToken` — `src/main/kotlin/com/ntt/authservice/auth/domain/model/AuthToken.kt`
  - role: Domain model for auth token pair (access + refresh)

- `User` — `src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt`
  - role: Domain aggregate

- `TokenIssuanceMetadata` — `src/main/kotlin/com/ntt/authservice/auth/domain/model/TokenIssuanceMetadata.kt`
  - role: Metadata for token issuance context

- `RefreshTokenInfo` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/TokenStore.kt`
  - role: Port data class for refresh token info

## Domain Events

- `TokenIssuedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenIssuedEvent.kt`
  - fields: userId, username, domainCode, issuanceContext, accessTokenJti, refreshTokenHash, roles, permissions, etc.

- `TokenRevokedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenRevokedEvent.kt`
  - fields: userId, revocationType, revokedTokenHash, revokedCount, reason, etc.

- `EventEnvelope` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/EventEnvelope.kt`
  - role: CloudEvents-inspired envelope wrapper

- `IssuanceContext` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/IssuanceContext.kt`
  - type: Enum (LOGIN, REGISTRATION, TOKEN_REFRESH, MFA_COMPLETION, SSO)

- `RevocationType` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/RevocationType.kt`
  - type: Enum (ROTATION, LOGOUT, ADMIN_REVOKE, BULK_REVOKE)

## DTO Pattern Summary

- **Convention**: Kotlin `data class` — immutable, no inheritance
- **Annotations**: Jakarta Validation (`@NotBlank`, `@Valid`)
- **Location**: `adapter.in.web.dto` for web DTOs; `application.command` for commands; `application` for results
- **CQRS pattern**: Commands (mutation) in `command/` package, Queries in `query/` package
- **No base DTO class** — each DTO standalone

## NOT DETECTED

- DTO inheritance (extends Base*Request) — not used, Kotlin data classes are final
- `@Getter`, `@Builder`, `@SuperBuilder` — Java Lombok not used (Kotlin data classes)
- Filter DTOs (pagination/sorting) — not found in token-related DTOs
