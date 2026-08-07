# Plan 007: Implement password expiry check in login flow

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: MED
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

`PasswordPolicyService.isPasswordExpired()` exists (line 126-133) but is **never called**. Neither `AuthService.login()` nor `LoginHandler.handle()` check if the user's password has expired before issuing tokens. This means the `max_age_days` policy in `password_policies` table is configured but silently ignored — users with expired passwords can log in indefinitely.

Per the SRS (FR-15), password expiry should force users to change their password before accessing the system.

## Current state

- `PasswordPolicyService.isPasswordExpired()` — fully implemented, checks `user.passwordChangedAt` against `policy.maxAgeDays`.
- `AuthService.login()` line 76-130 — no expiry check.
- `LoginHandler.handle()` line 33-83 — no expiry check.
- `PasswordExpiredException` — defined in `AuthCoreExceptions.kt` line 114-120, never thrown.
- `LoginResult` sealed class — only has `Success` and `MfaRequired`. Needs a `PasswordExpired` variant (or throw exception and let controller handle it).

**Design decision**: Throw `PasswordExpiredException` after successful password verification but before issuing tokens. The exception handler returns HTTP 403 with `AUTH_018` error code. Client must redirect to change-password flow.

## Steps

### Step 1: Add password expiry check to AuthService.login()

In `AuthService.kt`, after line 111 (`userRepository.save(user)`) and before the MFA checkpoint (line 114):

```kotlin
// Password expiry check
val userDomains = userDomainRepository.findAllByUserIdAndActiveTrue(user.id!!)
val primaryDomainMembership = userDomains.firstOrNull { it.isPrimary } ?: userDomains.firstOrNull()
if (primaryDomainMembership != null) {
    if (passwordPolicyService.isPasswordExpired(user.id!!, primaryDomainMembership.domainId)) {
        throw PasswordExpiredException()
    }
}
```

Also add `passwordPolicyService` as a constructor parameter if not already present.

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

### Step 2: Add password expiry check to LoginHandler.handle()

In `LoginHandler.kt`, after line 69 (`user.resetFailedLogins()`) and before the MFA checkpoint (line 73):

Add the same check using the domain port to resolve the user's primary domain.

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

## Done criteria

- [x] `./gradlew compileKotlin` exits 0
- [x] `./gradlew test` exits 0
- [x] `PasswordExpiredException` is thrown when password is expired
- [x] `grep -rn "isPasswordExpired" src/main/kotlin/` shows usage in login flow

## STOP conditions

- `AuthService.login()` has already been modified to include expiry check.
- `PasswordPolicyService.isPasswordExpired()` method signature differs from expected.
