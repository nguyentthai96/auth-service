# Plan 005: Fix PasswordPolicyService — BCryptPasswordEncoder instantiation at construction time

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt`

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: MED
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

`PasswordPolicyService` creates its own `BCryptPasswordEncoder` at construction time (line 31):
```kotlin
private val passwordEncoder = BCryptPasswordEncoder(securityProperties.password.bcryptStrength)
```

Meanwhile, `SecurityConfig` already defines a `@Bean fun passwordEncoder(): PasswordEncoder` (line 50-53). This means:
1. **Two encoders exist** — one managed by Spring (SecurityConfig), one unmanaged (PasswordPolicyService).
2. If the bcrypt strength is changed in config, the Spring-managed encoder updates but `PasswordPolicyService`'s doesn't until restart.
3. `AuthService` uses the injected `passwordEncoder` (constructor param), but `PasswordPolicyService` uses its own → potential hash mismatch if strengths differ.

The fix is simple: inject the Spring-managed `PasswordEncoder` via constructor instead of creating a new one.

## Current state

- `PasswordPolicyService.kt` line 31: `private val passwordEncoder = BCryptPasswordEncoder(securityProperties.password.bcryptStrength)`
- `SecurityConfig.kt` line 50-53: `@Bean fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(securityProperties.password.bcryptStrength)`
- `AuthService.kt` line 29: `private val passwordEncoder: PasswordEncoder` — injected via constructor

## Steps

### Step 1: Inject PasswordEncoder into PasswordPolicyService

In `PasswordPolicyService.kt`:

1. Add constructor parameter: `private val passwordEncoder: PasswordEncoder`
2. Add import: `import org.springframework.security.crypto.password.PasswordEncoder`
3. Remove line 31: `private val passwordEncoder = BCryptPasswordEncoder(securityProperties.password.bcryptStrength)`
4. Remove import: `import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

## Done criteria

- [ ] `./gradlew compileKotlin` exits 0
- [ ] `PasswordPolicyService` uses injected `PasswordEncoder`, not its own instance
- [ ] `BCryptPasswordEncoder` import removed from PasswordPolicyService.kt

## STOP conditions

- `PasswordPolicyService.kt` line 31 doesn't match the excerpt.
