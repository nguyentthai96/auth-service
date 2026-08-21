# DTO Pattern

_Generated: 2026-08-25_

## Request DTO

- RegisterRequestDto — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — annotations: @Valid, @NotBlank, @Email, @Size
- LoginRequestDto — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — annotations: @Valid, @NotBlank
- RefreshTokenRequestDto — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — annotations: @NotBlank
- SwitchDomainRequestDto — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — annotations: @NotBlank
- MfaVerifyRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: @Valid, @NotBlank
- TotpConfirmRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: @Valid, @NotBlank
- MfaResendRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: @Valid, @NotBlank
- MfaSettingsRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: @Valid
- IntrospectionRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt` — annotations: @Valid, @NotBlank
- CreateAnonymousSessionRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` — annotations: none (all optional)
- StoreSessionDataRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` — annotations: @Valid
- SsoCallbackRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt` — annotations: @Valid, @NotBlank
- SsoLinkRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt` — annotations: @Valid
- DeletionRequest — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt` — annotations: @Valid, @Size(max=1000)
- ChangePasswordRequestDto — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt` — annotations: @Valid, @NotBlank, @Size
- ForgotPasswordRequestDto — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt` — annotations: @Valid, @NotBlank, @Email

## Response DTO

- AuthResponse — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt` — data class with `message` field
- SessionResponse — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt` — inline data class
- AnonymousTokenResponse — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- SessionDataResponse — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- IntrospectionResponse — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- RevokeSessionsResponse — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- TotpSetupResponse — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- MfaRequiredResponse — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- MfaSettingsResponse — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`

## DTO Pattern Notes

- Kotlin data classes (immutable by default)
- Jakarta Bean Validation annotations (@Valid, @NotBlank, @Email, @Size)
- No base class inheritance — all standalone data classes
- Request DTOs in `dto/` package or inline in controller files (legacy pattern)
- Response DTOs in `dto/` package or inline in controller files

## NOT DETECTED

- Filter DTOs (no filter/criteria DTOs detected)
- Pagination DTOs (standard Spring Pageable used directly)
