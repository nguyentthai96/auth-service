# Plan 001: Fix controller bean conflict — CqrsAuthController and AuthController both register at /api/auth

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: HIGH
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

`CqrsAuthController` uses `@ConditionalOnProperty(name = ["app.security.cqrs.enabled"], havingValue = "true", matchIfMissing = true)` at line 26. `AuthController` has NO `@ConditionalOnProperty`. With `app.security.cqrs.enabled=true` (the default in application.yml line 106), **both controllers** register at `/api/auth` → Spring Boot will fail at startup with a `BeanCreationException: Ambiguous mapping` error for `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`.

This is a **startup-blocking bug** — the application cannot run with CQRS enabled (which is the default).

## Current state

- `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`:
  - Line 12-17: `@RestController @RequestMapping("/api/auth") class AuthController`
  - **No** `@ConditionalOnProperty` annotation. Always registered.
  - Endpoints: `/register`, `/login`, `/change-password`, `/forgot-password`, `/refresh`, `/switch-domain`

- `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`:
  - Line 25-27: `@RestController @RequestMapping("/api/auth") @ConditionalOnProperty(name = ["app.security.cqrs.enabled"], havingValue = "true", matchIfMissing = true)`
  - Active by default. Endpoints: `/register`, `/login`, `/refresh`, `/switch-domain`

- `src/main/resources/application.yml` line 106: `enabled: ${CQRS_ENABLED:true}`

**Overlapping endpoints**: `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/switch-domain`

**Repo convention**: `@ConditionalOnProperty` is already used in this codebase (CqrsAuthController, PermissionChangedConsumer). The convention is `havingValue = "true"/"false"` with `matchIfMissing`.

## Commands you will need

| Purpose   | Command                                  | Expected on success |
|-----------|------------------------------------------|---------------------|
| Build     | `./gradlew compileKotlin`                | BUILD SUCCESSFUL    |
| Test      | `./gradlew test --tests "*ArchitectureTest*"` | all pass       |
| Grep      | `grep -rn "ConditionalOnProperty" src/main/kotlin/` | shows both controllers |

## Scope

**In scope** (the only files you should modify):
- `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`

**Out of scope** (do NOT touch):
- `CqrsAuthController.kt` — its annotation is correct.
- Any controller logic — only add the annotation, nothing else.

## Git workflow

- Branch: `advisor/001-fix-controller-conflict`
- Commit: `fix: add ConditionalOnProperty to AuthController to prevent bean conflict with CqrsAuthController`
- Do NOT push or open a PR unless instructed.

## Steps

### Step 1: Add @ConditionalOnProperty to AuthController

In `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`:

1. Add import: `import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty`
2. Add annotation before `class AuthController`:
```kotlin
@ConditionalOnProperty(name = ["app.security.cqrs.enabled"], havingValue = "false")
```

The resulting class header should be:
```kotlin
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = ["app.security.cqrs.enabled"], havingValue = "false")
class AuthController(
```

This ensures AuthController is ONLY active when CQRS is explicitly disabled (`app.security.cqrs.enabled=false`). When CQRS is enabled (default), only CqrsAuthController is active.

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

### Step 2: Verify no other controller conflict exists

**Verify**: `grep -rn '@RequestMapping("/api/auth")' src/main/kotlin/` → should show exactly 2 files: AuthController.kt and CqrsAuthController.kt, each with mutually exclusive `@ConditionalOnProperty`.

## Test plan

- No new tests needed — this is a bean registration fix.
- Existing `ArchitectureTest` should continue to pass.
- Verification: `./gradlew test --tests "*ArchitectureTest*"` → all pass

## Done criteria

- [x] `./gradlew compileKotlin` exits 0
- [x] `./gradlew test --tests "*ArchitectureTest*"` exits 0
- [x] `AuthController.kt` has `@ConditionalOnProperty(name = ["app.security.cqrs.enabled"], havingValue = "false")`
- [x] `CqrsAuthController.kt` has `@ConditionalOnProperty(name = ["app.security.cqrs.enabled"], havingValue = "true", matchIfMissing = true)` (unchanged)
- [x] No files outside the in-scope list are modified

## STOP conditions

- The code at AuthController.kt line 12-17 doesn't match the excerpt above.
- AuthController already has a `@ConditionalOnProperty` annotation.
- There are more than 2 controllers mapped to `/api/auth`.

## Maintenance notes

- `change-password` and `forgot-password` endpoints exist ONLY in AuthController, not in CqrsAuthController. When CQRS is enabled (default), these endpoints will be **unavailable**. Plan 004 addresses this by moving them to CqrsAuthController or a shared PasswordController.
- Any new controller mapping to `/api/auth` must have the same mutual exclusion pattern.
