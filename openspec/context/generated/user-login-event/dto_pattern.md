# DTO Pattern

_Generated: 2025-08-22_

## Request DTO

- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `@field:NotBlank` (username, password), `@Valid @RequestBody`
  - fields: username, password, domainCode?, captchaToken?, trustedDeviceHash?, deviceFingerprint?, captchaPayload?, anonymousSessionId?, anonymousToken?
  - note: Received by CqrsAuthController.login(), mapped to LoginCommand

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - fields: accessToken, refreshToken?, tokenType, expiresIn, userId, username, activeDomain, roles, permissions, promotedFromAnonymous, dataTransferred?, message?
  - companion: `from(AuthToken)` factory method
  - note: API response for login/register. LoginResult.Success wraps this.

## Command DTO (CQRS)

- `LoginCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt`
  - implements: `Command<LoginResult>` (from `com.ntt.eventsourcingutils.lib.cqrs.command.Command`)
  - fields: username, password, domainCode?, captchaToken?, trustedDeviceHash?, ipAddress?, userAgent?, deviceFingerprint?, anonymousSessionId?, anonymousTokenJti?
  - note: CQRS command — built by CqrsAuthController, processed by LoginHandler. **Key data source for event enrichment**.

## Domain Event (as DTO pattern)

- `UserRegisteredEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`
  - implements: `DomainEvent`
  - fields: userId, username, email, fullName, phone?, domainCode, domainId?, status, registrationSource, ipAddress?, userAgent?
  - eventType: `iam.user.registered`
  - note: **Pattern reference for UserLoggedInEvent** — enriched data class, same DomainEvent interface

- `TokenIssuedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenIssuedEvent.kt`
  - implements: `DomainEvent`
  - fields: userId, username, domainCode, domainId, issuanceContext, accessTokenJti, refreshTokenHash, roles, permissions, accessTokenExpiresAt, refreshTokenExpiresAt, previousRefreshTokenHash?, ipAddress?, userAgent?, issuedAt
  - eventType: `iam.token.issued`
  - note: **Complementary event** — recorded during login but captures token context, not auth context

- `TokenValidationFailedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenValidationFailedEvent.kt`
  - implements: `DomainEvent`
  - eventType: `iam.token.validation-failed`
  - note: **Pattern reference for UserLoginFailedEvent** — failure event with reason enum

- `EventEnvelope<T>` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/EventEnvelope.kt`
  - fields: id (UUID), type, source, specversion, time, correlationId, schemaVersion, data
  - note: CloudEvents-inspired wrapper. All domain events wrapped in this before persistence.

## Result DTO

- `LoginResult` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt`
  - sealed class: `LoginResult.Success(response: AuthResponse, promotionResult?)`, `LoginResult.MfaRequired(mfaToken, method, expiresIn)`
  - note: Return type of LoginHandler.handle()

## NOT DETECTED

- Filter DTOs (no filter/criteria DTO in login flow)
- Paginated response DTOs (not used in login flow)
