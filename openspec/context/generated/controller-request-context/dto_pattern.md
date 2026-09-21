# DTO Pattern

_Generated: 2026-09-21_

## Request DTO

- `RegisterRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@NotBlank`, `@Email`, `@Size(min=8, max=100)`
  - Fields: username, email, password, fullName, phone?, domainCode, anonymousSessionId?, anonymousToken?

- `LoginRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@NotBlank`
  - Fields: username, password, domainCode?, captchaToken?, trustedDeviceHash?, deviceFingerprint?, captchaPayload?, anonymousSessionId?, anonymousToken?

- `RefreshTokenRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@NotBlank`
  - Fields: refreshToken

- `SwitchDomainRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@NotBlank`
  - Fields: domainCode

- `ChangePasswordRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@NotBlank(message=...)`, `@Size(min=8)`
  - Fields: oldPassword, newPassword

- `ForgotPasswordRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
  - Annotations: `@NotBlank(message=...)`, `@Email(message=...)`
  - Fields: email

## Response DTO

- `AuthResponse` — `auth/adapter/in/web/dto/AuthResponse.kt`

## Feature-Specific DTOs

- `AnonymousDtos` — `auth/adapter/in/web/dto/AnonymousDtos.kt` (anonymous auth request/response)
- `DeviceDtos` — `auth/adapter/in/web/dto/DeviceDtos.kt` (device management)
- `MfaDtos` — `auth/adapter/in/web/dto/MfaDtos.kt` (MFA setup/verify)
- `SsoDtos` — `auth/adapter/in/web/dto/SsoDtos.kt` (SSO token exchange)
- `TokenDtos` — `auth/adapter/in/web/dto/TokenDtos.kt` (token introspection/revocation)

## DTO Pattern Observations

- All DTOs are Kotlin `data class` (no inheritance from base DTO)
- Validation via Jakarta Bean Validation annotations (`@field:NotBlank`, `@field:Email`, `@field:Size`)
- DTOs grouped by feature domain in single files (e.g., `RequestDtos.kt` for core auth, `MfaDtos.kt` for MFA)
- No `toCommand()` or `toEntity()` extension functions exist — mapping is inline in controllers

## NOT DETECTED

- Base request/response DTO class
- Filter DTOs (pagination/sorting)
- `@Builder` / `@SuperBuilder` pattern (Java/Lombok — not applicable in Kotlin)
