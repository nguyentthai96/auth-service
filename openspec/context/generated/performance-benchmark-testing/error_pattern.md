# Error Handling Pattern

_Generated: 2026-09-08_

## Exception Classes

- AuthException (base) — `shared/exception/AuthExceptions.kt`
  - extends BusinessException (from base-core)
  - carries AuthErrorCode enum

- ResourceNotFoundException — `shared/exception/AuthExceptions.kt`
- DuplicateResourceException — `shared/exception/AuthExceptions.kt`
- PermissionDeniedException — `shared/exception/AuthExceptions.kt`
- WriteNotAllowedException — `shared/exception/AuthExceptions.kt`
- AccountLockedException — `shared/exception/AuthExceptions.kt`
- InvalidCredentialsException — `shared/exception/AuthExceptions.kt`
- TokenExpiredException — `shared/exception/AuthExceptions.kt`
- PolicyEvaluationException — `shared/exception/AuthExceptions.kt`
- EventStorePersistException — `shared/exception/AuthExceptions.kt`
- EventNotFoundException — `shared/exception/AuthExceptions.kt`
- InvalidServiceTokenException — `shared/exception/AuthExceptions.kt`
- InsufficientScopeException — `shared/exception/AuthExceptions.kt`
- ServiceNotRegisteredException — `shared/exception/AuthExceptions.kt`

- ClaimValidationException — `auth/application/ClaimValidationException.kt`
  - extends RuntimeException

- AnonymousSessionExpiredException — `shared/exception/AnonymousExceptions.kt`
- AnonymousDataLimitExceededException — `shared/exception/AnonymousExceptions.kt`
- AnonymousPromotionConflictException — `shared/exception/AnonymousExceptions.kt`
- AnonymousRateLimitedException — `shared/exception/AnonymousExceptions.kt`
- AnonymousMaxRenewalsException — `shared/exception/AnonymousExceptions.kt`

- E2EE exceptions (10 classes) — `shared/exception/CipherExceptions.kt`
  - E2eeTimeSkewException, E2eeReplayDetectedException, etc.

## Error Code Format

- Pattern: `AUTH_XXX` — Example: `AUTH_001`, `AUTH_030`, `AUTH_040`
- Enum: `AuthErrorCode` — `shared/exception/AuthErrorCode.kt`
  - implements ErrorCodeBase (from base-core)
  - fields: errorCode, msgCode, description, httpStatus

## Global Exception Handler

- GlobalExceptionHandler — `shared/exception/GlobalExceptionHandler.kt`
  - @RestControllerAdvice
  - ProblemDetail (RFC 7807) responses
  - I18n message resolution via MessageSource

## NOT DETECTED

- Custom error response wrapper (uses ProblemDetail directly)
