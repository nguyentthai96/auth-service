# Error Handling Pattern

_Generated: 2026-09-22_

## Exception Classes

### Base Exception
- `AuthException` — `shared/exception/AuthExceptions.kt`
  - extends: `BusinessException` (from `com.ntt.basecore.exception.BusinessException`)
  - pattern: Bridge — extends BusinessException while wrapping `AuthError` enum

### Auth-specific Exceptions
- `InvalidCredentialsException` — `shared/exception/AuthExceptions.kt`
- `AccountLockedException` — `shared/exception/AuthExceptions.kt`
- `AccountLockedPermanentException` — `shared/exception/AuthExceptions.kt`
- Additional auth exceptions — `shared/exception/AuthCoreExceptions.kt`
  - All extend `AuthException` → `BusinessException`

## Error Code Format

- Pattern: `AuthError` enum → `toErrorCodeBase()` → `ErrorCodeBase` (base-core)
- Enum location: `shared/exception/AuthExceptions.kt` (or related file)

## Global Exception Handler

- `GlobalExceptionHandler` — `shared/exception/GlobalExceptionHandler.kt`
  - `@ControllerAdvice` + `@ExceptionHandler`
  - Overrides base-core's `BusinessException` handler for auth-specific exceptions
  - Returns `ApiResponse` with appropriate HTTP status

## Exception Hierarchy

```
RuntimeException
└── BusinessException (base-core)
    └── AuthException (auth-service bridge)
        ├── InvalidCredentialsException
        ├── AccountLockedException
        ├── AccountLockedPermanentException
        └── ... (AuthCoreExceptions.kt)
```

## NOT DETECTED

- Error code constants (uses enum-based ErrorCodeBase pattern instead)
- ProblemDetail (RFC 7807) — handled by base-core `BaseControllerAdvice`
