# Base Class Map

_Generated: 2026-08-15 | Services: auth-service_

## Controller

- `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
- `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
- `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
- `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
- `CaptchaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt`
- `RateLimitAdminController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt`
- `PolicyController` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`
- `RbacControllers` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt` (DomainController, RoleController, GroupController, ResourceController, UserRoleController)
- `RolePermissionController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt`

## Handler

NOT DETECTED — project uses service layer directly (no dedicated handler package in production code)

> Note: `LoginHandler` found in test utils but not as standalone handler pattern.

## Factory

NOT DETECTED

## Client / Gateway

- `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`

## Service

- `AuthService` — `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt`
- `MfaService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
- `TotpService` — `src/main/kotlin/com/ntt/authservice/auth/application/TotpService.kt`
- `OtpService` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt`
- `JwtService` — `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
- `AuditLogService` — `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt`
- `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`
- `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt`

## Filter

- `JwtAuthFilter` — `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`

## Cache

- `MultiTierPermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt` (Caffeine L1 + Redis L2)

## Exception Handler

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` (@RestControllerAdvice)
