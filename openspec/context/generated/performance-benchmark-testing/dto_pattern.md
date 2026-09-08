# DTO Pattern

_Generated: 2026-09-08_

## Request DTO

- LoginRequestDto — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: @field:NotBlank
- RegisterRequestDto — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: @field:NotBlank, @field:Size, @field:Email
- RefreshTokenRequestDto — `auth/adapter/in/web/dto/RequestDtos.kt` — annotations: @field:NotBlank
- SwitchDomainRequestDto — `auth/adapter/in/web/dto/RequestDtos.kt`
- ChangePasswordRequestDto — `auth/adapter/in/web/dto/RequestDtos.kt`
- ForgotPasswordRequestDto — `auth/adapter/in/web/dto/RequestDtos.kt`
- MfaVerifyRequest — `auth/adapter/in/web/dto/MfaDtos.kt`
- TotpConfirmRequest — `auth/adapter/in/web/dto/MfaDtos.kt`
- MfaResendRequest — `auth/adapter/in/web/dto/MfaDtos.kt`
- MfaSettingsRequest — `auth/adapter/in/web/dto/MfaDtos.kt`
- IntrospectionRequest — `auth/adapter/in/web/dto/TokenDtos.kt`
- SsoCallbackRequest — `auth/adapter/in/web/dto/SsoDtos.kt`
- CreateAnonymousSessionRequest — `auth/adapter/in/web/dto/AnonymousDtos.kt`
- StoreSessionDataRequest — `auth/adapter/in/web/dto/AnonymousDtos.kt`
- ServiceTokenRequest — `auth/adapter/in/web/InternalApiController.kt`
- DeletionRequest — `auth/adapter/in/web/AccountLifecycleController.kt`
- DomainCreateRequest — `rbac/adapter/in/web/RbacControllers.kt`
- DomainUpdateRequest — `rbac/adapter/in/web/RbacControllers.kt`
- RoleCreateRequest — `rbac/adapter/in/web/RbacControllers.kt`
- GroupCreateRequest — `rbac/adapter/in/web/RbacControllers.kt`
- ResourceCreateRequest — `rbac/adapter/in/web/RbacControllers.kt`
- AssignRoleRequest — `rbac/adapter/in/web/RbacControllers.kt`
- PermissionCheckRequest — `rbac/adapter/in/web/RbacControllers.kt`
- BatchPermissionCheckRequest — `rbac/adapter/in/web/RbacControllers.kt`
- AssignPermissionRequest — `rbac/adapter/in/web/RolePermissionController.kt`
- BulkAssignPermissionRequest — `rbac/adapter/in/web/RolePermissionController.kt`

## Response DTO

- AuthResponse — `auth/adapter/in/web/dto/AuthResponse.kt`
- DeviceResponse — `auth/adapter/in/web/dto/DeviceDtos.kt`
- MfaRequiredResponse — `auth/adapter/in/web/dto/MfaDtos.kt`
- TotpSetupResponse — `auth/adapter/in/web/dto/MfaDtos.kt`
- MfaSettingsResponse — `auth/adapter/in/web/dto/MfaDtos.kt`
- RecoveryCodesResponse — `auth/adapter/in/web/dto/MfaDtos.kt`
- IntrospectionResponse — `auth/adapter/in/web/dto/TokenDtos.kt`
- RevokeSessionsResponse — `auth/adapter/in/web/dto/TokenDtos.kt`
- AnonymousTokenResponse — `auth/adapter/in/web/dto/AnonymousDtos.kt`
- SessionDataResponse — `auth/adapter/in/web/dto/AnonymousDtos.kt`
- SessionResponse — `auth/adapter/in/web/SessionController.kt`
- LockInfoResponse — `auth/adapter/in/web/RateLimitAdminController.kt`
- DomainResponse — `rbac/adapter/in/web/RbacControllers.kt`
- RoleResponse — `rbac/adapter/in/web/RbacControllers.kt`
- GroupResponse — `rbac/adapter/in/web/RbacControllers.kt`
- ResourceResponse — `rbac/adapter/in/web/RbacControllers.kt`
- PermissionCheckResponse — `rbac/adapter/in/web/RbacControllers.kt`
- RolePermissionResponse — `rbac/adapter/in/web/RolePermissionController.kt`

## Pattern

- Convention: `data class` (Kotlin)
- Base: No shared base DTO class (standalone data classes)
- Validation: Bean Validation annotations (@field:NotBlank, @field:Size, @field:Email)
- Location: Inline in controllers OR dedicated `dto/` package

## NOT DETECTED

- Filter DTOs
- Page/Pageable wrapper DTOs
