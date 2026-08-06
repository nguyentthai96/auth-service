# Plan 006: Add audit logging to MfaService and SsoAdapter

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

`AuditLogService` (line 14) has audit actions defined: `MFA_SETUP`, `MFA_VERIFY_SUCCESS`, `MFA_VERIFY_FAILED`, `SSO_LOGIN`, `SSO_LINK`, `SSO_UNLINK`, `PASSWORD_CHANGED`, `FORCE_LOGOUT` — but **none of these are called** from the actual services. `MfaService` and `SsoAdapter` use `log.info()` for operational logging but don't call `AuditLogService.logEvent()` for security-sensitive events.

This is a compliance gap — security events must be audit-logged for SOC2/ISO27001 compliance. The audit infrastructure exists but is unused.

## Current state

- `AuditLogService.kt`: Fully implemented with `logEvent(userId, action, entityType, entityId, details)`. Logs to structured AUDIT logger.
- `MfaService.kt`: Uses `log.info()` at lines 88, 138, 190 — should also call `auditLogService.logEvent()`.
- `SsoAdapter.kt`: Uses `log.info()` at lines 51, 61, 104, 126 — should also call `auditLogService.logEvent()`.
- `AuthService.kt`: Also uses `log.info()` but doesn't audit — should be addressed but lower priority.

## Steps

### Step 1: Inject AuditLogService into MfaService

Add `private val auditLogService: AuditLogService` to MfaService constructor.

### Step 2: Add audit calls in MfaService

| Location | Action | Details |
|----------|--------|---------|
| `verifyMfa()` line 88 (after success) | `MFA_VERIFY_SUCCESS` | `"method=$method"` |
| `verifyMfa()` catch blocks | `MFA_VERIFY_FAILED` | `"method=$method, reason=${e.message}"` |
| `setupTotp()` line 114 (after setup) | `MFA_SETUP` | `"type=TOTP"` |
| `confirmTotp()` line 138 | `MFA_SETUP` | `"type=TOTP, status=confirmed"` |
| `updateSettings()` line 190 | `MFA_SETUP` | `"enabled=$enabled, method=${user.mfaMethod}"` |

Example:
```kotlin
auditLogService.logEvent(userId, AuditAction.MFA_VERIFY_SUCCESS, "User", userId.toString(), "method=$method")
```

### Step 3: Inject AuditLogService into SsoAdapter and add audit calls

| Location | Action | Details |
|----------|--------|---------|
| `handleCallback()` line 51 (existing identity) | `SSO_LOGIN` | `"provider=$provider, existingIdentity=true"` |
| `handleCallback()` line 61 (JIT provisioned) | `SSO_LOGIN` | `"provider=$provider, jitProvisioned=true"` |
| `linkIdentity()` line 104 | `SSO_LINK` | `"provider=$provider"` |
| `unlinkIdentity()` line 126 | `SSO_UNLINK` | `"provider=$provider"` |

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

## Done criteria

- [ ] `./gradlew compileKotlin` exits 0
- [ ] `grep -rn "auditLogService" src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt` returns matches
- [ ] `grep -rn "auditLogService" src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt` returns matches
- [ ] All `AuditAction` enum values used in at least one `logEvent()` call

## STOP conditions

- `AuditLogService` class doesn't exist or has a different API.
- MfaService/SsoAdapter already inject AuditLogService.
