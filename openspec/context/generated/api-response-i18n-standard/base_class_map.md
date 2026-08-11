# Base Class Map

_Generated: 2026-08-11 | Services: base-core, auth-service_

## Controller

- `BaseController` — `base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseController.kt`
  - extends: N/A (abstract)
  - implements: N/A
- `CqrsAuthController` — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - extends: N/A
  - implements: N/A

## Handler

- `LoginHandler` — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
  - extends: N/A
- `RefreshTokenHandler` — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`
  - extends: N/A

## Controller Advice (Error Handling)

- `BaseControllerAdvice` — `base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt`
  - extends: N/A (abstract)
  - handles: `AccessDeniedException`, `TypeMismatchException`, `NotFoundException`, `BusinessException`, `MethodArgumentNotValidException`, `Throwable`
- `AuthControllerAdvice` — `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice`
  - handles: `AuthException` (returns `ProblemDetail`)

## Response Payload

- `ApiResponse<T>` — `base-core/src/main/kotlin/com/ntt/basecore/domain/web/payload/ApiResponse.kt`
  - Factory: `success()`, `error()`, `errorLang()`
- `AuthResponse` — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`

## Configuration

- `BaseCoreAutoConfiguration` — `base-core/src/main/kotlin/com/ntt/basecore/configuration/BaseCoreAutoConfiguration.kt`
- `BaseCoreServletAutoConfiguration` — `base-core/src/main/kotlin/com/ntt/basecore/configuration/BaseCoreServletAutoConfiguration.kt`

## Factory

NOT DETECTED

## Client / Gateway

NOT DETECTED (within scope of this feature)
