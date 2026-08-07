# Plan 011: Add rate limiting to MFA resend and OTP endpoints

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`

## Status

- **Priority**: P3
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

`MfaService.resendOtp()` (line 145-168) generates a new OTP on every call with no rate limiting. An attacker can flood SMS/Email gateway by rapidly calling `POST /api/auth/mfa/resend` — this is a **cost amplification attack** (each OTP triggers a real SMS/Email dispatch).

Additionally, `OtpService.generateOtp()` overwrites the previous OTP on each call (Redis SET overwrites), which means an attacker can also invalidate a user's legitimate OTP by triggering a resend.

## Current state

- `MfaService.resendOtp()` — no rate check
- `OtpService.generateOtp()` — overwrites previous OTP without checking if one exists
- Redis is already available — can add a rate-limit key like `otp:ratelimit:{userId}:{channel}` with TTL=60s

## Steps

### Step 1: Add rate limiting in OtpService

In `OtpService.kt`, add before generating a new OTP:

```kotlin
fun generateOtp(userId: Long, channel: String): String {
    // Rate limit: max 1 OTP per 60 seconds per channel
    val rateLimitKey = "otp:ratelimit:$userId:$channel"
    if (redisTemplate.hasKey(rateLimitKey)) {
        throw MfaCodeInvalidException("Please wait before requesting a new code")
    }

    val code = String.format("%06d", secureRandom.nextInt(1_000_000))
    // ... existing logic ...

    // Set rate limit
    redisTemplate.opsForValue().set(rateLimitKey, "1", Duration.ofSeconds(60))

    return code
}
```

### Step 2: Add resend count limit in MfaService

In `MfaService.resendOtp()`, add max 3 resends per MFA session:

```kotlin
val resendKey = "mfa:resend:$userId"
val resendCount = redisTemplate.opsForValue().increment(resendKey) ?: 1
if (resendCount == 1L) {
    redisTemplate.expire(resendKey, Duration.ofSeconds(securityProperties.mfa.mfaTokenTtlSeconds))
}
if (resendCount > 3) {
    throw MfaMaxAttemptsException("Maximum resend attempts exceeded")
}
```

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

## Done criteria

- [x] `./gradlew compileKotlin` exits 0
- [x] OTP generation rate-limited to 1 per 60 seconds per channel
- [x] MFA resend limited to 3 per session

## STOP conditions

- Redis is not available or `StringRedisTemplate` is not injected in OtpService.
