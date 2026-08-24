# DTO Pattern

_Generated: 2026-08-25_

## Request DTO

- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `data class`
- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `data class`
- `RefreshTokenRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `data class`
- `SwitchDomainRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `data class`
- `ChangePasswordRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `data class`
- `ForgotPasswordRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `data class`
- `MfaVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - annotations: `data class`
- `TotpConfirmRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - annotations: `data class`
- `MfaResendRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - annotations: `data class`
- `MfaSettingsRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - annotations: `data class`
- `RecoveryCodeVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - annotations: `data class`
- `IntrospectionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - annotations: `data class`
- `CreateAnonymousSessionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - annotations: `data class`
- `StoreSessionDataRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - annotations: `data class`
- `SsoCallbackRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
  - annotations: `data class`
- `SsoLinkRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
  - annotations: `data class`
- `ServiceTokenRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt`
  - annotations: `data class`
- `PolicyCreateRequest` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`
  - annotations: `data class`
- `DomainCreateRequest`, `RoleCreateRequest`, `GroupCreateRequest`, `ResourceCreateRequest`, `AssignRoleRequest`, `AddUserToGroupRequest`, `PermissionCheckRequest`, `BatchPermissionCheckRequest` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
  - annotations: `data class`
- `AssignPermissionRequest`, `BulkAssignPermissionRequest` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt`
  - annotations: `data class`
- `DeletionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
  - annotations: `data class`

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - annotations: `data class`
- `MfaRequiredResponse`, `TotpSetupResponse`, `MfaSettingsResponse`, `RecoveryCodesResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - annotations: `data class`
- `IntrospectionResponse`, `RevokeSessionsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - annotations: `data class`
- `AnonymousTokenResponse`, `SessionDataResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - annotations: `data class`
- `SessionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
  - annotations: `data class`
- `LockInfoResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt`
  - annotations: `data class`
- `CaptchaVerifyResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
  - annotations: `data class` (external response model)
- `SsoTokenResponse`, `SsoUserInfoResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
  - annotations: `data class` (external response model)
- `PolicyResponse`, `ConditionResponse` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`
  - annotations: `data class`
- `DomainResponse`, `RoleResponse`, `GroupResponse`, `ResourceResponse`, `PermissionCheckResponse`, `RolePermissionResponse` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`, `RolePermissionController.kt`
  - annotations: `data class`

## Query/Command Objects (CQRS)

- `GetPermissionsQuery` — `src/main/kotlin/com/ntt/authservice/rbac/application/query/GetPermissionsQuery.kt`
- `GetUserRolesQuery` — `src/main/kotlin/com/ntt/authservice/rbac/application/query/GetUserRolesQuery.kt`
- `CheckPermissionQuery` — `src/main/kotlin/com/ntt/authservice/rbac/application/query/CheckPermissionQuery.kt`
- `BuildAuthResponseQuery` — `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseQuery.kt`

## DTO Pattern Summary

- **Convention**: Kotlin `data class` — no Lombok annotations needed
- **Validation**: `spring-boot-starter-validation` in build.gradle.kts (Jakarta Bean Validation)
- **Base class**: None — standalone data classes (no `BaseRequest`, `BaseResponse`)
- **Location**: DTOs co-located with controllers in `adapter/in/web/dto/` or inline in controller files
- **Serialization**: Jackson module kotlin (`com.fasterxml.jackson.module:jackson-module-kotlin`)

## NOT DETECTED

- `@SuperBuilder`, `@Builder`, `@Getter` — not used (Kotlin data class handles all)
- `BaseRequest`, `BaseResponse` — no base DTO classes in auth-service
- Filter DTOs — not detected as separate classes
