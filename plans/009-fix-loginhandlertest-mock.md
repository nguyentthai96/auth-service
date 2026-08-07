# Plan 009: Fix LoginHandlerTest — PasswordConfig mock type mismatch

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/test/kotlin/com/ntt/authservice/auth/application/command/LoginHandlerTest.kt`

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: tests
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

`LoginHandlerTest.kt` line 40 declares: `@Mock private lateinit var passwordConfig: SecurityProperties.PasswordConfig`

But the actual class is `SecurityProperties.PasswordProperties` (defined in `SecurityProperties.kt` line 29). `PasswordConfig` does not exist — this is a **compilation error** if the test is run in a clean build.

The test mocks `securityProperties.password` to return this `passwordConfig` mock (lines 83, 137). Since the type doesn't exist, the test cannot compile.

## Current state

- `LoginHandlerTest.kt` line 40: `@Mock private lateinit var passwordConfig: SecurityProperties.PasswordConfig`
- `SecurityProperties.kt` line 29: `data class PasswordProperties(...)` — NOT `PasswordConfig`
- Test lines 83-84, 137-139: `whenever(securityProperties.password).thenReturn(passwordConfig)` and `whenever(passwordConfig.maxFailedAttempts).thenReturn(5)`

## Steps

### Step 1: Fix the type name

In `LoginHandlerTest.kt`:

Change line 40 from:
```kotlin
@Mock private lateinit var passwordConfig: SecurityProperties.PasswordConfig
```
to:
```kotlin
@Mock private lateinit var passwordConfig: SecurityProperties.PasswordProperties
```

**Verify**: `./gradlew test --tests "*LoginHandlerTest*"` → all 4 tests pass

## Done criteria

- [x] `./gradlew test --tests "*LoginHandlerTest*"` exits 0 with 4 tests passing
- [x] `grep -rn "PasswordConfig" src/test/` returns no matches

## STOP conditions

- `SecurityProperties` actually has a `PasswordConfig` inner class (meaning the naming was correct).
