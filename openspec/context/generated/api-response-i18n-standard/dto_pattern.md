# DTO Pattern

_Generated: 2026-08-27_

## Request DTO

- `MfaVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: `@Valid`, `@RequestBody`
- `TotpConfirmRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: `@Valid`, `@RequestBody`
- `MfaResendRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: `@Valid`, `@RequestBody`
- `MfaSettingsRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: `@Valid`, `@RequestBody`
- `RecoveryCodeVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `SsoCallbackRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt` — annotations: `@Valid`, `@RequestBody`
- `SsoLinkRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt` — annotations: `@Valid`, `@RequestBody`
- `IntrospectionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt` — annotations: `@Valid`, `@RequestBody`
- `CreateAnonymousSessionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `StoreSessionDataRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` — annotations: `@Valid`, `@RequestBody`
- `ServiceTokenRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt` (inline data class)
- Request DTOs in: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
- `TotpSetupResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaRequiredResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaSettingsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `SsoProviderInfoDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `IntrospectionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `AnonymousTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `SessionDataResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `LockInfoResponse`, `LockDetail` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt` (inline data class)

## DTO Pattern Notes

- DTOs are Kotlin `data class` — no lombok annotations needed
- Grouped by feature in dedicated `*Dtos.kt` files
- Validation annotations: `@Valid`, `@NotBlank`, `@NotNull`, `@Size`
- Some DTOs defined inline in controller files (e.g., `ServiceTokenRequest`, `LockInfoResponse`)
- Many action endpoints return `Map<String, Any?>` with `message` field (i18n) instead of typed DTOs
- No base class for Request/Response DTOs — standalone data classes

## NOT DETECTED

- `@SuperBuilder` / `@Builder` — not used (Kotlin data class handles this)
- `@Getter` / `@Setter` — not used (Kotlin properties)
- Base DTO class (e.g., `BaseRequest`, `BaseResponse`) — not used
- Filter DTOs — not detected as standalone pattern
