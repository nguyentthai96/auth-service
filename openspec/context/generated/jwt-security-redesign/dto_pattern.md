# DTO Pattern

_Generated: 2026-09-07_

## Request DTO

- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@field:NotBlank`, `@field:Size`, `@field:Email`
- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@field:NotBlank`, `@field:Size`, `@field:Pattern`
- `RefreshTokenRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `SwitchDomainRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `ChangePasswordRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@field:NotBlank`, `@field:Size`
- `ForgotPasswordRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `IntrospectionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `MfaVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `TotpConfirmRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaResendRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaSettingsRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `RecoveryCodeVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `SsoCallbackRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `SsoLinkRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `DeletionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
- `ServiceTokenRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt`
- `CreateAnonymousSessionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `StoreSessionDataRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
- `SessionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt` (inline data class)
- `IntrospectionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `RevokeSessionsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `MfaRequiredResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `TotpSetupResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaSettingsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `RecoveryCodesResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `AnonymousTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `SessionDataResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `LockInfoResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt` (inline)
- `SsoTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- `SsoUserInfoResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- `CaptchaVerifyResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- `BuildAuthResponseQuery` — `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseQuery.kt`

## DTO Convention

- Pattern: Kotlin `data class` (no Lombok, native Kotlin data class features)
- Validation: `@field:NotBlank`, `@field:Size`, `@field:Pattern`, `@field:Email` (Jakarta Bean Validation)
- Naming: `<Entity><Action>RequestDto` or `<Entity><Action>Response`
- Location: `auth.adapter.in.web.dto` package (dedicated DTO files) or inline in controller files

## NOT DETECTED

- Base request/response superclass (no common `BaseRequest`/`BaseResponse`)
- `@SuperBuilder` pattern (Kotlin data classes don't need it)
