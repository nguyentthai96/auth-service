# DTO Pattern

_Generated: 2026-08-11_

## Request DTO

- `LoginRequestDto` — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `@Valid`, `@NotBlank`, `@Size`
- `RegisterRequestDto` — same file
- `ChangePasswordRequestDto` — same file
- `ForgotPasswordRequestDto` — same file
- `SwitchDomainRequestDto` — same file

## Response DTO

- `ApiResponse<T>` — `base-core/src/main/kotlin/com/ntt/basecore/domain/web/payload/ApiResponse.kt`
  - fields: `code`, `msgCode`, `message`, `data`, `timestamp`
  - factory: `success()`, `error()`, `errorLang()`
- `AuthResponse` — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - fields: `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `userId`, `username`, `activeDomain`, `roles`, `permissions`

## Error Response DTO

- `ProblemDetail` (Spring built-in) — used by `AuthControllerAdvice`
  - fields: `type`, `title`, `status`, `detail`, `instance` + extensions via `setProperty()`
  - custom extensions: `errorCode`, `retryAfterSeconds`, `dimension`, `maxSessions`, `activeCount`, `lockedUntilAt`

## NOT DETECTED

- Filter DTOs — not used in this feature scope
- Pagination DTOs — not relevant
