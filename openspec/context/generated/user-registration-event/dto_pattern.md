# DTO Pattern

_Generated: 2025-08-22_

## Request DTO

- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@NotBlank`, `@Email`, `@Size(min = 8, max = 100)`
  - Fields: username, email, password, fullName, phone?, domainCode (default "default"), anonymousSessionId?, anonymousToken?

## CQRS Command (Internal DTO equivalent)

- `RegisterCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt`
  - extends: `Command<RegisterResult>` (from eventsourcing-utils)
  - Fields: username, email, password, fullName, phone?, domainCode, anonymousSessionId?, anonymousTokenJti?, ipAddress?, userAgent?, correlationId?
  - Note: ipAddress and userAgent added for event enrichment (FR-001)

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - Fields: accessToken, refreshToken?, tokenType ("Bearer"), expiresIn, userId, username, activeDomain, roles, permissions, promotedFromAnonymous, dataTransferred?, message?
  - Companion: `from(AuthToken)` factory method

## Domain Event DTOs (Event Payloads)

- `UserRegisteredEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`
  - implements: `DomainEvent`
  - Fields: userId, username, email, fullName, phone?, domainCode, domainId?, status, registrationSource, ipAddress?, userAgent?
  - eventType: "iam.user.registered"

- `UserLoginFailedEvent` (PATTERN REFERENCE) — `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoginFailedEvent.kt`
  - implements: `DomainEvent`
  - Fields: usernameAttempted, userId?, failureReason (LoginFailureReason), ipAddress?, userAgent?, deviceFingerprint?, failedAt
  - eventType: "iam.user.login_failed"

## Event Envelope (Generic Wrapper)

- `EventEnvelope<T>` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/EventEnvelope.kt`
  - Fields: id (UUID), type, source ("auth-service"), specversion ("1.0"), time, correlationId, schemaVersion (1), data (T)
  - CloudEvents-inspired structure

## Result Types (Sealed Classes)

- `RegisterResult` — `src/main/kotlin/com/ntt/authservice/auth/application/RegisterResult.kt`
  - Sealed class: Success(authToken, promotionResult?) | Failure(error)

## Validation Annotations Used

- `@NotBlank` — jakarta.validation.constraints
- `@Email` — jakarta.validation.constraints
- `@Size(min, max)` — jakarta.validation.constraints
- `@Valid` — jakarta.validation (on controller parameters)

## NOT DETECTED

- Filter DTOs — not relevant for event recording flow
- Pagination DTOs — not relevant for event recording flow
