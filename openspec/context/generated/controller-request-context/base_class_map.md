# Base Class Map

_Generated: 2026-09-21 | Services: auth-service_

## Controller

- NOT DETECTED — Không có base controller class. Tất cả 22 controllers là standalone `@RestController` classes không kế thừa từ base nào.
  - `CqrsAuthController` — `auth/adapter/in/web/CqrsAuthController.kt`
  - `AnonymousAuthController` — `auth/adapter/in/web/AnonymousAuthController.kt`
  - `TokenController` — `auth/adapter/in/web/TokenController.kt`
  - `SessionController` — `auth/adapter/in/web/SessionController.kt`
  - `DeviceController` — `auth/adapter/in/web/DeviceController.kt`
  - `MfaController` — `auth/adapter/in/web/MfaController.kt`
  - `SsoController` — `auth/adapter/in/web/SsoController.kt`
  - `AccountLifecycleController` — `auth/adapter/in/web/AccountLifecycleController.kt`
  - `AdminSessionController` — `auth/adapter/in/web/AdminSessionController.kt`
  - `AdminUnlockController` — `auth/adapter/in/web/AdminUnlockController.kt`
  - `RateLimitAdminController` — `auth/adapter/in/web/RateLimitAdminController.kt`
  - `CaptchaController` — `auth/adapter/in/web/CaptchaController.kt`
  - `InternalApiController` — `auth/adapter/in/web/InternalApiController.kt`
  - `KeyExchangeController` — `auth/adapter/in/web/KeyExchangeController.kt`
  - `EventStoreController` — `auth/adapter/in/web/EventStoreController.kt`
  - `SelfServiceUnlockController` — `auth/adapter/in/web/SelfServiceUnlockController.kt`
  - `DomainController` — `rbac/adapter/in/web/RbacControllers.kt`
  - `RoleController` — `rbac/adapter/in/web/RbacControllers.kt`
  - `GroupController` — `rbac/adapter/in/web/RbacControllers.kt`
  - `ResourceController` — `rbac/adapter/in/web/RbacControllers.kt`
  - `PermissionCheckController` — `rbac/adapter/in/web/RbacControllers.kt`
  - `PolicyController` — `pbac/adapter/in/web/PolicyController.kt`

## Handler

- `Handler<C, R>` (interface) — `com.ntt.eventsourcingutils` (external lib — event sourcing utils)
  - Implemented by: `LoginHandler`, `RegisterHandler`, `RefreshTokenHandler`, etc.
  - Path: `auth/application/command/*.kt` and `auth/application/query/*.kt`

## Factory

- NOT DETECTED — No factory base class found.

## Client / Gateway

- `NotificationGateway` (interface) — `auth/application/port/out/NotificationGateway.kt`
  - Implementation: `NotificationGatewayAdapter` — `auth/adapter/out/NotificationGatewayAdapter.kt`

## Exception Handler (related to Controller layer)

- `AuthControllerAdvice` extends `BaseControllerAdvice` (base-core) — `shared/exception/GlobalExceptionHandler.kt`
  - `BaseControllerAdvice` from `com.ntt.basecore.domain.web.BaseControllerAdvice`

## Filter (related to Controller layer — pre-processing)

- `ClientMetadataFilter` extends `OncePerRequestFilter` — `auth/adapter/in/web/filter/ClientMetadataFilter.kt`
- `ServiceAuthFilter` extends `OncePerRequestFilter` — `auth/adapter/in/web/filter/ServiceAuthFilter.kt`
- `ContentLanguageFilter` extends `OncePerRequestFilter` — `auth/adapter/in/web/filter/ContentLanguageFilter.kt`
- `LoginRateLimitFilter` extends `OncePerRequestFilter` — `auth/adapter/in/web/filter/LoginRateLimitFilter.kt`
- `JwtAuthFilter` extends `OncePerRequestFilter` — `shared/security/JwtAuthFilter.kt`
- `IdempotencyFilter` extends `OncePerRequestFilter` — `shared/filter/IdempotencyFilter.kt`

## NOT DETECTED

- Controller base class (main target of this feature)
- Factory classes
- Abstract handler base class
