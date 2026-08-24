# DTO Pattern

_Generated: 2025-07-15 (refreshed)_

## auth-service — Request DTOs

- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: `username`, `email`, `password`, `fullName`, `phone`, `domainCode`, `anonymousSessionId`, `anonymousToken`
  - Annotations: `@NotBlank`, `@Email`, `@Size(min=8, max=100)`
  - Pattern: `data class` with Jakarta validation

- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: `username`, `password`, `domainCode`, `captchaToken`, `trustedDeviceHash`, `deviceFingerprint`, `captchaPayload`, `anonymousSessionId`, `anonymousToken`
  - Annotations: `@NotBlank`

- `RefreshTokenRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: `refreshToken`
  - Annotations: `@NotBlank`

- `SwitchDomainRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: `domainCode`
  - Annotations: `@NotBlank`

- MFA DTOs — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Contains: MFA enable/disable/verify request/response DTOs

- SSO DTOs — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
  - Contains: SSO callback/authorize DTOs

- Token DTOs — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - Contains: Token introspection DTOs

- Anonymous DTOs — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - Contains: Anonymous session create/renew DTOs

## auth-service — Response DTOs

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - Fields: `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `userId`, `username`, `activeDomain`, `roles`, `permissions`, `promotedFromAnonymous`, `dataTransferred`, `message`
  - Pattern: `data class` with `companion object { fun from(token: AuthToken) }` factory method
  - JSON: `@JsonInclude(Include.NON_NULL)` for optional fields

## auth-service — CQRS Command DTOs

- `LoginCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt`
- `RegisterCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt`
- `RefreshTokenCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenCommand.kt`
- `CreateAnonymousSessionCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/CreateAnonymousSessionCommand.kt`
- `RenewAnonymousTokenCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenCommand.kt`
- `SwitchDomainCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainCommand.kt`
- `RevokeSessionsCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsCommand.kt`

## auth-service — Result DTOs (Application Layer)

- `LoginResult` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt`
- `RegisterResult` — `src/main/kotlin/com/ntt/authservice/auth/application/RegisterResult.kt`
- `PromotionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/PromotionResult.kt`
- `AnonymousSessionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionResult.kt`

## account-service — DTOs

- `ProfileDtos.kt` — `account-service/src/main/kotlin/com/ntt/accountservice/profile/adapter/in/web/dto/ProfileDtos.kt`
  - Contains: Profile request/response DTOs for CRUD operations

- `DeviceDtos.kt` — `account-service/src/main/kotlin/com/ntt/accountservice/device/adapter/in/web/dto/DeviceDtos.kt`
  - Contains: Device management request/response DTOs

## system-admin-service — DTOs

- NOT DETECTED — No dedicated DTO files found in system-admin-service adapter/in/web packages
  - ⚠️ Assumption: DTOs may be inline in controller files or entity-based responses

## DTO Patterns Observed

| Pattern | Convention | Example |
|---------|-----------|---------|
| Naming (Request) | `<Entity><Action>RequestDto` | `LoginRequestDto`, `RegisterRequestDto` |
| Naming (Response) | `<Entity>Response` | `AuthResponse` |
| Naming (Command) | `<Action>Command` | `LoginCommand`, `RegisterCommand` |
| Naming (Result) | `<Action>Result` | `LoginResult`, `RegisterResult` |
| Type | Kotlin `data class` | All DTOs |
| Validation | Jakarta `@Valid` + Bean Validation | `@NotBlank`, `@Email`, `@Size` |
| Mapping | Companion object `from()` factory | `AuthResponse.from(AuthToken)` |
| Package (web) | `adapter.in.web.dto` | All request/response DTOs |
| Package (app) | `application.command` | CQRS commands |
| Serialization | Jackson module-kotlin | `com.fasterxml.jackson.module:jackson-module-kotlin` |
| Null handling | `@JsonInclude(Include.NON_NULL)` | Optional fields |

## NOT DETECTED

- MapStruct mappers — mapping done via extension functions / companion objects
- Base DTO abstract class — all DTOs are standalone `data class`
- Swagger/OpenAPI `@Schema` annotations — not yet added
- Builder pattern (`@Builder`, `@SuperBuilder`) — Kotlin `data class` with default params instead
- system-admin-service dedicated DTO files
