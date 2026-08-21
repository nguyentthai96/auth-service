# Base Class Map

_Generated: 2026-08-27 | Services: auth-service_

## Controller

- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
- `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
- `AccountLifecycleController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
- `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
- `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
- `AdminSessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt`
- `RateLimitAdminController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt`
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
- `AnonymousAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt`
- `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
- `InternalApiController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt`
- `EventStoreController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/EventStoreController.kt`
- `KeyExchangeController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/KeyExchangeController.kt`
- `CaptchaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt`
- `PolicyController` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`
- `RolePermissionController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt`
- `DomainController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
- `RoleController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
- `GroupController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
- `ResourceController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
- `PermissionCheckController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`

## ControllerAdvice / Exception Handler

- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - implements: N/A
  - Injects: `MessageSource` for i18n error message resolution

## Filter

- `LoginRateLimitFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt`
  - extends: `OncePerRequestFilter`
  - Injects: `MessageSource` for i18n rate limit error response
- `ClientMetadataFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ClientMetadataFilter.kt`
  - extends: `OncePerRequestFilter`
  - Order: `HIGHEST_PRECEDENCE + 10`
- `ContentLanguageFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ContentLanguageFilter.kt`
  - extends: `OncePerRequestFilter`

## Configuration

- `I18nConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt`
  - provides: `CompositeMessageSource`, `AcceptHeaderLocaleResolver`

## Exception Base

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `BusinessException` (base-core)
  - field: `authError: AuthErrorCode`

## Client / Gateway

NOT DETECTED — auth-service is primarily an inbound API service, no outbound HTTP clients for i18n feature.

## NOT DETECTED

- Factory
- Handler (standalone) — handlers are embedded in CQRS command pattern (not base class hierarchy)
