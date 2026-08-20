# DTO Pattern

_Generated: 2026-08-22_

## Request DTO

- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@field:NotBlank`, `@field:Email`, `@field:Size(min = 8, max = 100)`
  - Fields: username, email, password, fullName, phone?, domainCode, anonymousSessionId?, anonymousToken?

- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@field:NotBlank`
  - Fields: username, password, domainCode?, captchaToken?, trustedDeviceHash?, deviceFingerprint?, captchaPayload?, anonymousSessionId?, anonymousToken?

- `RefreshTokenRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@field:NotBlank`
  - Fields: refreshToken

- `SwitchDomainRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@field:NotBlank`
  - Fields: domainCode

- `ChangePasswordRequestDto` — (referenced in CqrsAuthController, imported from command package)
  - Fields: oldPassword, newPassword

- `ForgotPasswordRequestDto` — (referenced in CqrsAuthController, imported from command package)
  - Fields: email

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - Pattern: `data class` with `companion object { fun from(AuthToken) }`
  - Fields: accessToken, refreshToken?, tokenType, expiresIn, userId, username, activeDomain, roles, permissions, promotedFromAnonymous, dataTransferred?, message? (`@JsonInclude(NON_NULL)`)
  - Note: `message` field is optional — used for i18n success message (FR-005)

- `DataTransferredInfo` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - Fields: itemCount, namespaces, status
  - Purpose: Anonymous session promotion metadata

## Validation Pattern

- Bean Validation (Jakarta): `@field:NotBlank`, `@field:Email`, `@field:Size`
- Controller validation: `@Valid @RequestBody`
- Validator: `LocalValidatorFactoryBean` injected into `BaseControllerAdvice`

## DTO Naming Convention

- Request DTOs: `*RequestDto` (e.g., `LoginRequestDto`, `RegisterRequestDto`)
- Response DTOs: `*Response` (e.g., `AuthResponse`)
- Grouped in files: `RequestDtos.kt`, `AuthResponse.kt`, `AnonymousDtos.kt`, `MfaDtos.kt`, `SsoDtos.kt`, `TokenDtos.kt`
- Location: `auth/adapter/in/web/dto/`

## NOT DETECTED

- Filter DTOs — NOT DETECTED (filters work with raw headers, not DTOs)
- Pagination DTOs — NOT DETECTED for i18n scope
