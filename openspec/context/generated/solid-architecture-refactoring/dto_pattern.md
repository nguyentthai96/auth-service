# DTO Pattern

_Generated: 2026-09-22_

## Request DTO

- `RegisterRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@Valid`, `@NotBlank`, `@Email`, `@Size`
- `LoginRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@Valid`, `@NotBlank`
- `RefreshTokenRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
- `SwitchDomainRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@Valid`, `@NotBlank`
- `ChangePasswordRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@Valid`
- `ForgotPasswordRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@Valid`, `@Email`

## Response DTO

- `AuthResponse` — `auth/adapter/in/web/dto/AuthResponse.kt`
- `MfaRequiredResponse` — `auth/adapter/in/web/dto/MfaRequiredResponse.kt`
- `DataTransferredInfo` — `auth/adapter/in/web/dto/DataTransferredInfo.kt`

## Command DTO (CQRS)

- `LoginCommand` — `auth/application/command/LoginHandler.kt` (inline data class)
- `RegisterCommand` — `auth/application/command/RegisterHandler.kt` (inline data class)
- `RefreshTokenCommand` — `auth/application/command/RefreshTokenHandler.kt`
- `SwitchDomainCommand` — `auth/application/command/SwitchDomainHandler.kt`
- `RevokeSessionsCommand` — `auth/application/command/RevokeSessionsHandler.kt`

## Query DTO (CQRS)

- `BuildAuthResponseQuery` — `auth/application/query/BuildAuthResponseQuery.kt`

## Envelope (API Standard)

- `ApiResponse<T>` — from `com.ntt.basecore.domain.web.payload.ApiResponse` (base-core lib)

## Naming Pattern
- Request: `*RequestDto` (Kotlin data class)
- Response: `*Response` (Kotlin data class)
- Command: `*Command` (Kotlin data class)
- Query: `*Query` (Kotlin data class)

## NOT DETECTED

- Filter DTO (no `*Filter` classes found)
- Pagination DTO (using base-core `PageResponse<T>`)
