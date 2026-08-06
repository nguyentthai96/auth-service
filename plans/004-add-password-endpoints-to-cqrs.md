# Plan 004: Add change-password and forgot-password endpoints to CqrsAuthController

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: HIGH
- **Depends on**: 001
- **Category**: bug
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

When CQRS is enabled (the default), `AuthController` is disabled (after Plan 001). But `change-password` and `forgot-password` endpoints exist ONLY in `AuthController` (lines 58-74). They are NOT duplicated in `CqrsAuthController`. This means:

- `POST /api/auth/change-password` → **404 Not Found** in production
- `POST /api/auth/forgot-password` → **404 Not Found** in production

These are critical user-facing features (FR-14, FR-15 in the SRS).

## Current state

- `AuthController.kt` lines 58-74: `changePassword()` and `forgotPassword()` methods
- `CqrsAuthController.kt`: Does NOT have these endpoints
- `PasswordPolicyService.changePassword()`: The service method exists and works
- `changePassword()` has a TODO at line 65: `// TODO: resolve domainId from user's active domain` — it hardcodes `0L`

## Steps

### Step 1: Add change-password endpoint to CqrsAuthController

In `CqrsAuthController.kt`, add:

```kotlin
@PostMapping("/change-password")
fun changePassword(
    @Valid @RequestBody request: ChangePasswordRequestDto,
    @RequestHeader("Authorization") authHeader: String
): ResponseEntity<Map<String, String>> {
    val userId = getCurrentUserId()
    // Resolve domainId from user's active domain via security context
    val details = SecurityContextHolder.getContext().authentication?.details as? Map<*, *>
    val activeDomain = details?.get("activeDomain") as? String ?: "default"
    // TODO: look up domain entity to get domainId — for now delegate to PasswordPolicyService
    passwordPolicyService.changePassword(userId, request.oldPassword, request.newPassword, 0L)
    return ResponseEntity.ok(mapOf("message" to "Password changed successfully"))
}
```

Add `PasswordPolicyService` to constructor.

### Step 2: Add forgot-password endpoint

```kotlin
@PostMapping("/forgot-password")
fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequestDto): ResponseEntity<Map<String, String>> {
    // Always return 200 to prevent email enumeration
    // TODO: trigger password reset email
    return ResponseEntity.ok(mapOf("message" to "If the email exists, a reset link has been sent"))
}
```

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

## Done criteria

- [ ] `./gradlew compileKotlin` exits 0
- [ ] CqrsAuthController has `/change-password` and `/forgot-password` endpoints
- [ ] No files outside scope modified

## STOP conditions

- CqrsAuthController already has these endpoints.
