# Plan 010: Harden TotpService — startup validation for TOTP_ENCRYPTION_KEY

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/application/TotpService.kt`

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: MED
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

`TotpService.getEncryptionKey()` (line 98-106) validates `TOTP_ENCRYPTION_KEY` is set and is 32 bytes — but only at **call time** (when a user tries to setup/verify TOTP). If the key is missing or malformed, the first user to try TOTP gets a `500 Internal Server Error` with a raw `IllegalArgumentException`.

Problems:
1. **Fail-slow**: The app starts fine without `TOTP_ENCRYPTION_KEY`. The error surfaces only when a user tries TOTP — hard to catch in CI/CD.
2. **Raw exception**: `IllegalArgumentException` is not mapped by `AuthControllerAdvice` → returns 500 with a stack trace (violates rule: never leak stack traces).
3. **No MFA feature flag**: If MFA is enabled (`app.security.mfa` is configured) but encryption key is missing, the app should fail fast at startup.

## Current state

- `TotpService.kt` line 24: `@Value("\${TOTP_ENCRYPTION_KEY:}") private val encryptionKeyBase64: String`
- `TotpService.kt` lines 98-106: `getEncryptionKey()` with `require()` checks
- `SecurityProperties.kt` line 35-41: `MfaProperties` — has no `encryptionKey` field

## Steps

### Step 1: Add startup validation with @PostConstruct

In `TotpService.kt`, add a `@PostConstruct` validation:

```kotlin
import jakarta.annotation.PostConstruct

@PostConstruct
fun validateEncryptionKey() {
    if (encryptionKeyBase64.isBlank()) {
        log.warn("TOTP_ENCRYPTION_KEY is not set — TOTP MFA will not work. Set this env var to enable TOTP.")
        return
    }
    try {
        val keyBytes = java.util.Base64.getDecoder().decode(encryptionKeyBase64)
        require(keyBytes.size == 32) {
            "TOTP_ENCRYPTION_KEY must be 256 bits (32 bytes) in Base64, got ${keyBytes.size} bytes"
        }
        log.info("TOTP encryption key validated successfully (256-bit AES)")
    } catch (e: IllegalArgumentException) {
        throw IllegalStateException("TOTP_ENCRYPTION_KEY is invalid: ${e.message}", e)
    }
}
```

This logs a warning if the key is missing (allowing the app to start for non-TOTP users) but throws `IllegalStateException` (causing startup failure) if the key exists but is malformed.

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

### Step 2: Wrap getEncryptionKey() with a cleaner exception

Replace the `require()` calls with a throw of a proper domain exception:

```kotlin
private fun getEncryptionKey(): SecretKeySpec {
    if (encryptionKeyBase64.isBlank()) {
        throw MfaCodeInvalidException("TOTP is not configured — contact your administrator")
    }
    val keyBytes = Base64.getDecoder().decode(encryptionKeyBase64)
    return SecretKeySpec(keyBytes, "AES")
}
```

This maps to HTTP 401 via `AuthControllerAdvice` instead of 500.

**Verify**: `./gradlew test` → all pass

## Done criteria

- [x] `./gradlew compileKotlin` exits 0
- [x] `./gradlew test` exits 0
- [x] `TotpService` has `@PostConstruct validateEncryptionKey()` method
- [x] `getEncryptionKey()` throws `MfaCodeInvalidException` (not `IllegalArgumentException`)

## STOP conditions

- `TotpService.kt` code at line 98-106 doesn't match the excerpt.
- There's already a `@PostConstruct` method in TotpService.
