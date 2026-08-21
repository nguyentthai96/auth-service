# DTO Pattern

_Generated: 2026-08-25_

## Request DTO

- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: username, password, domainCode, captchaToken?, trustedDeviceHash?
- `RefreshTokenRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `SwitchDomainRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `MfaVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Fields: mfaToken, code
- `TotpConfirmRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaResendRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaSettingsRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `SsoCallbackRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
  - Fields: code, state, provider
- `SsoLinkRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `IntrospectionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `CreateAnonymousSessionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `StoreSessionDataRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - Fields: accessToken, refreshToken, expiresIn, tokenType
- `MfaRequiredResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Fields: mfaToken, method, expiresIn
- `TotpSetupResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Fields: qrUri, secret
- `MfaSettingsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `RecoveryCodesResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `SsoProviderInfoDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `IntrospectionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - Fields: active, sub, username, roles, permissions, exp, iat, iss, jti
- `RevokeSessionsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - Fields: revokedCount
- `AnonymousTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `SessionDataResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `DataTransferredInfo` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`

## DTO Pattern

- **Style**: Kotlin `data class` (immutable, no annotations needed for JSON)
- **Grouping**: Feature-based files — `MfaDtos.kt`, `SsoDtos.kt`, `TokenDtos.kt`, `RequestDtos.kt`, `AnonymousDtos.kt`, `AuthResponse.kt`
- **Naming**: `*Request` for input, `*Response` for output, `*Dto` for transfer objects
- **Validation**: Field-level validation via Kotlin non-null types + `@field:NotBlank` etc. (Spring Validation)
- **Base class**: None — all standalone `data class`

## NOT DETECTED

- Filter DTO (no `*Filter.kt` DTO classes — filters are web filters, not DTO)
- `@Getter` / `@Builder` / `@SuperBuilder` (Java Lombok — not used, Kotlin `data class` instead)
