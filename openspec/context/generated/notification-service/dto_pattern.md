# DTO Pattern

_Generated: 2026-09-11_

## Request DTO

- `RegisterRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@NotBlank`, `@Email`, `@Size`
- `LoginRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@NotBlank`
- `RefreshTokenRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
- `SwitchDomainRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
- `ChangePasswordRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@NotBlank`, `@Size`
- `ForgotPasswordRequestDto` — `auth/adapter/in/web/dto/RequestDtos.kt`
- `CreateAnonymousSessionRequest` — `auth/adapter/in/web/dto/AnonymousDtos.kt`
- `StoreSessionDataRequest` — `auth/adapter/in/web/dto/AnonymousDtos.kt`
- `IntrospectionRequest` — `auth/adapter/in/web/dto/TokenDtos.kt`
- `MfaVerifyRequest` — `auth/adapter/in/web/dto/MfaDtos.kt`
- `TotpConfirmRequest` — `auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaResendRequest` — `auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaSettingsRequest` — `auth/adapter/in/web/dto/MfaDtos.kt`
- `SsoCallbackRequest` — `auth/adapter/in/web/dto/SsoDtos.kt`
- `SsoLinkRequest` — `auth/adapter/in/web/dto/SsoDtos.kt`

## Response DTO

- `AuthResponse` — `auth/adapter/in/web/dto/AuthResponse.kt`
- `AnonymousTokenResponse` — `auth/adapter/in/web/dto/AnonymousDtos.kt`
- `SessionDataResponse` — `auth/adapter/in/web/dto/AnonymousDtos.kt`
- `IntrospectionResponse` — `auth/adapter/in/web/dto/TokenDtos.kt`
- `RevokeSessionsResponse` — `auth/adapter/in/web/dto/TokenDtos.kt`
- `DeviceResponse` — `auth/adapter/in/web/dto/DeviceDtos.kt`
- `MfaRequiredResponse` — `auth/adapter/in/web/dto/MfaDtos.kt`
- `TotpSetupResponse` — `auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaSettingsResponse` — `auth/adapter/in/web/dto/MfaDtos.kt`
- `RecoveryCodesResponse` — `auth/adapter/in/web/dto/MfaDtos.kt`
- `RecoveryCodeVerifyRequest` — `auth/adapter/in/web/dto/MfaDtos.kt`

## DTO Pattern Summary

- **Style**: Kotlin `data class` (no base class inheritance)
- **Validation**: Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`, `@Valid`)
- **Grouping**: Multiple related DTOs per file (e.g., `RequestDtos.kt`, `MfaDtos.kt`, `AnonymousDtos.kt`)
- **Naming**: `{Feature}{Action}Request` / `{Feature}{Action}Response`
- **Serialization**: Jackson (default Spring Boot)

## NOT DETECTED

- Filter/Search DTOs
- Pagination DTOs (may be in base-core)
- Base request/response classes (DTOs are standalone data classes)
