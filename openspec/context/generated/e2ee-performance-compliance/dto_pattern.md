# DTO Pattern

_Generated: 2026-08-15_

## Request DTO

- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@field:NotBlank`, `@field:Email`
- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt` — annotations: `@field:NotBlank`, `@field:Email`, `@field:Size`
- `RefreshTokenRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `SwitchDomainRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `MfaVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: `@field:NotBlank`
- `TotpConfirmRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt` — annotations: `@field:NotBlank`
- `MfaResendRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaSettingsRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `SsoCallbackRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `SsoLinkRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `IntrospectionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `PolicyCreateRequest` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt` (inline)
- `DomainCreateRequest` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt` (inline)
- `RoleCreateRequest` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt` (inline)
- `AssignPermissionRequest` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt`

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
- `MfaRequiredResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `TotpSetupResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaSettingsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `IntrospectionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `RevokeSessionsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `SessionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt` (inline)
- `PolicyResponse` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt` (inline)
- `DomainResponse` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt` (inline)
- `RoleResponse` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt` (inline)
- `CaptchaVerifyResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt` (inline)
- `SsoTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt` (inline)
- `SsoUserInfoResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt` (inline)

## DTO Patterns Detected

- **Data class**: All DTOs are Kotlin `data class` (immutable, equals/hashCode/copy)
- **Validation**: Bean Validation annotations (`@field:NotBlank`, `@field:Email`, `@field:Size`)
- **Naming**: `*Request`, `*RequestDto`, `*Response`
- **Location**: Mix of dedicated `dto/` package AND inline in controller files
- **No base class**: DTOs do not extend a common base — standalone data classes

## NOT DETECTED

- Filter DTOs (e.g., pagination filter)
- `@SuperBuilder` pattern (Lombok — not used, project is Kotlin)
- Protobuf DTOs
