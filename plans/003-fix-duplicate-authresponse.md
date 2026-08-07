# Plan 003: Fix duplicate AuthResponse class — application vs DTO package

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: HIGH
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

There are **two** `AuthResponse` classes:

1. `com.ntt.authservice.auth.application.AuthResponse` — defined inside `AuthService.kt` (lines 297-307) as a data class alongside `RegisterRequest` and `LoginRequest`. Used by `AuthService.generateAuthResponse()`, `AuthService.buildAuthResponseForUser()`, and `MfaService.verifyMfa()`.

2. `com.ntt.authservice.auth.adapter.in.web.dto.AuthResponse` — defined in `dto/AuthResponse.kt` (lines 7-34) with a `companion object fun from(AuthToken)`. Used by `CqrsAuthController` via `AuthResponse.from(authToken)`.

These are **different types** with the same name but in different packages. When `MfaController.verifyMfa()` calls `authService.buildAuthResponseForUser(userId)`, it gets `com.ntt.authservice.auth.application.AuthResponse`. But `CqrsAuthController` expects `com.ntt.authservice.auth.adapter.in.web.dto.AuthResponse`. This will cause compilation errors or ClassCastExceptions depending on which import is used.

Additionally, having DTOs defined in the `application` layer violates Clean Architecture — DTOs belong in the `adapter.in.web.dto` package.

## Current state

- `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` lines 297-307:
  ```kotlin
  data class AuthResponse(
      val accessToken: String,
      val refreshToken: String,
      val tokenType: String = "Bearer",
      val expiresIn: Long,
      val userId: Long,
      val username: String,
      val activeDomain: String,
      val roles: List<String>,
      val permissions: List<String>
  )
  ```

- `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt` lines 7-34:
  ```kotlin
  data class AuthResponse(
      val accessToken: String,
      val refreshToken: String,
      val tokenType: String = "Bearer",
      val expiresIn: Long,
      val userId: Long,
      val username: String,
      val activeDomain: String,
      val roles: List<String>,
      val permissions: List<String>
  ) {
      companion object {
          fun from(token: AuthToken): AuthResponse = ...
      }
  }
  ```

- `AuthService.kt` also contains `RegisterRequest` (line 280) and `LoginRequest` (line 289) — these should also be in the DTO layer.

**Architecture test** (`ArchitectureTest.kt`): Tests that domain doesn't depend on Spring/JPA, but does NOT check that application layer doesn't contain DTOs.

## Commands you will need

| Purpose   | Command                                  | Expected on success |
|-----------|------------------------------------------|---------------------|
| Build     | `./gradlew compileKotlin`                | BUILD SUCCESSFUL    |
| Test      | `./gradlew test`                         | all pass            |
| Grep      | `grep -rn "import.*AuthResponse" src/main/kotlin/` | all should point to dto package |

## Scope

**In scope**:
- `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` — remove inline DTOs, fix imports
- `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt` — fix AuthResponse import
- `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt` — fix AuthResponse import
- `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt` — fix imports if needed

**Out of scope**:
- `dto/AuthResponse.kt` — this is the canonical location, don't change it.
- `CqrsAuthController.kt` — already uses the correct DTO.

## Git workflow

- Branch: `advisor/003-fix-duplicate-authresponse`
- Commit: `refactor: consolidate AuthResponse to dto package, remove duplicate from application layer`
- Do NOT push or open a PR unless instructed.

## Steps

### Step 1: Remove inline DTOs from AuthService.kt

In `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt`:

1. Delete lines 279-307 (the `RegisterRequest`, `LoginRequest`, `AuthResponse` data classes at the bottom of the file).
2. Add import: `import com.ntt.authservice.auth.adapter.in.web.dto.AuthResponse`
3. `RegisterRequest` and `LoginRequest` already exist in `AuthController.kt` (lines 100-131 as `RegisterRequestDto` and `LoginRequestDto`). The service-level DTOs should be simple data classes — create them in `dto/AuthDtos.kt` if they don't exist, or inline them in AuthService as internal representations.

Actually, `AuthService` uses its own `RegisterRequest`/`LoginRequest` which are mapped from the controller DTOs. These are fine as application-layer types. Only `AuthResponse` is the duplicate.

Revised step: Delete only `AuthResponse` (lines 297-307) and fix the import.

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

### Step 2: Fix MfaService AuthResponse import

In `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`:

Line 3: `import com.ntt.authservice.auth.adapter.in.web.dto.AuthResponse` — verify this import is already correct. If it points to `com.ntt.authservice.auth.application.AuthResponse`, change it to the dto package.

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

### Step 3: Fix SsoAdapter AuthResponse import

In `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`:

Line 3: `import com.ntt.authservice.auth.adapter.in.web.dto.AuthResponse` — same verification and fix.

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

### Step 4: Verify all AuthResponse references point to dto package

**Verify**: `grep -rn "import.*AuthResponse" src/main/kotlin/` → all imports should point to `com.ntt.authservice.auth.adapter.in.web.dto.AuthResponse`

## Test plan

- No new tests — this is a refactoring (no behavior change).
- Verification: `./gradlew test` → all pass

## Done criteria

- [x] `./gradlew compileKotlin` exits 0
- [x] `./gradlew test` exits 0
- [x] `grep -rn "class AuthResponse" src/main/kotlin/` returns only one match in `dto/AuthResponse.kt`
- [x] No files outside the in-scope list are modified

## STOP conditions

- `AuthService.kt` doesn't have the inline `AuthResponse` data class (already cleaned up).
- The DTO `AuthResponse` in `dto/AuthResponse.kt` has different fields than the application-layer one.
- Other services depend on `com.ntt.authservice.auth.application.AuthResponse` specifically.

## Maintenance notes

- After this change, the `from(AuthToken)` factory method in `dto.AuthResponse` becomes the canonical way to create responses from domain objects.
- `RegisterRequest` and `LoginRequest` remain in `AuthService.kt` as application-layer types — they serve a different purpose from controller DTOs.
