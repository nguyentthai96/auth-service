# Error Handling Pattern

_Generated: 2025-01-20_

## Exception Classes (JWT Validation Scope)

- `ClaimValidationException` — `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidationException.kt`
  - extends: `RuntimeException`
  - fields: `validatorName: String`, `message: String`
  - thrown by: `ClaimValidatorChain.validateOrThrow()` on first validation failure
  - caught by: `JwtAuthFilter.doFilterInternal()` L92-106

- `TokenExpiredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.TOKEN_EXPIRED` (AUTH_003)
  - httpStatus: 401 UNAUTHORIZED
  - thrown by: `JwtService.parseToken()` on expired JWT (HMAC path)

- `AuthException` (base) — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `BusinessException` (base-core)
  - properties: `authError: AuthErrorCode`, `message: String`, `httpStatus: HttpStatus`
  - handler: `AuthControllerAdvice.handleAuthException()` → RFC 7807 ProblemDetail

- `ServiceTokenInvalidException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.INVALID_SERVICE_TOKEN` (AUTH_060)
  - httpStatus: 401 UNAUTHORIZED
  - thrown by: `ServiceTokenService.validateServiceToken()`

- `ServiceInsufficientScopeException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.INSUFFICIENT_SCOPE` (AUTH_061)
  - httpStatus: 403 FORBIDDEN

- `ServiceNotRegisteredException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `AuthException`
  - authError: `AuthErrorCode.SERVICE_NOT_REGISTERED` (AUTH_062)
  - httpStatus: 403 FORBIDDEN

## Error Code Format

- Pattern: `AUTH_XXX` — 3-digit numeric code with `AUTH_` prefix
- Enum: `AuthErrorCode` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt`
- Relevant codes for JWT validation:
  - `AUTH_003` — `TOKEN_EXPIRED` — JWT token has expired (401)
  - `AUTH_060` — `INVALID_SERVICE_TOKEN` — Invalid or expired service auth token (401)
  - `AUTH_061` — `INSUFFICIENT_SCOPE` — Service insufficient scope (403)
  - `AUTH_062` — `SERVICE_NOT_REGISTERED` — Service not registered (403)
- Structure: `AuthErrorCode(errorCode, msgCode, description, httpStatus)`
- Bridge: `AuthErrorCode.toErrorCodeBase()` → `ErrorCodeBase` (base-core)

## Error Response Format

- Pattern: RFC 7807 `ProblemDetail`
- Handler: `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - annotation: `@RestControllerAdvice`
  - method: `handleAuthException(AuthException)` → `ResponseEntity<ProblemDetail>`
- I18n: Messages resolved via `MessageSource` using `AuthErrorCode.msgCode` as key

## JWT Validation Error Handling Pattern

- **JwtAuthFilter** follows **fail-safe** pattern:
  - Signature failure → log WARN + record event + `filterChain.doFilter()` (no auth set)
  - Blacklisted → log WARN + record event + return 401 immediately
  - Claim failure → log WARN + record event + `filterChain.doFilter()` (no auth set)
  - Token expired → log DEBUG + `filterChain.doFilter()` (no auth set)
  - Generic exception → log DEBUG + `filterChain.doFilter()` (no auth set)
- **Event recording** is wrapped in try-catch → never blocks request flow
- **Introspection** always returns 200 OK (RFC 7662) — errors result in `active: false`

## NOT DETECTED

- Custom error code for claim validation failure — currently mapped to generic ValidationFailureReason enum, not AuthErrorCode
- Rate limiting error codes specific to JWT validation — handled by separate LoginRateLimitFilter
