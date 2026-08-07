# Plan 012: Clean up SSO-only user registration — missing fullName and email validation

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`

## Status

- **Priority**: P3
- **Effort**: S
- **Risk**: LOW
- **Depends on**: 002
- **Category**: bug
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

`SsoAdapter.provisionSsoUser()` (line 129-152) creates a `UserEntity` for JIT-provisioned SSO users with:

```kotlin
this.username = idpUser.email ?: "${provider}_${idpUser.sub}"
this.email = idpUser.email ?: ""    // ← can be empty string
this.passwordHash = SSO_ONLY_MARKER
// fullName is MISSING — but UserEntity has @Column(nullable = false)
```

Problems:
1. **`fullName` not set**: `UserEntity.fullName` is `@Column(nullable = false)` (line 23) and `lateinit` (line 24). If not set, the JPA save will throw `UninitializedPropertyAccessException` at runtime.
2. **Empty email**: Setting `email = ""` violates the `@Column(unique = true)` constraint — the second SSO user without email will get a unique constraint violation.
3. **`active` field**: `user.active = true` is set (line 136) but `active` is inherited from `SnowflakePersistentAuditableEntity` — need to verify the property exists and is accessible.

## Steps

### Step 1: Fix provisionSsoUser

In `SsoAdapter.kt` `provisionSsoUser()`:

```kotlin
private fun provisionSsoUser(idpUser: IdpUserInfo, provider: String): UserEntity {
    val user = UserEntity().apply {
        this.username = idpUser.email ?: "${provider}_${idpUser.sub}"
        this.email = idpUser.email ?: "${provider}_${idpUser.sub}@sso.local"  // placeholder email
        this.passwordHash = SSO_ONLY_MARKER
        this.fullName = idpUser.name ?: idpUser.email ?: "${provider} User"
        this.status = "ACTIVE"
    }
    // ... rest unchanged
}
```

**Verify**: `./gradlew compileKotlin` → BUILD SUCCESSFUL

## Done criteria

- [x] `./gradlew compileKotlin` exits 0
- [x] `fullName` is always set when provisioning SSO users
- [x] `email` is never empty string

## STOP conditions

- `provisionSsoUser()` doesn't match the code excerpt.
- `UserEntity` has changed to allow nullable `fullName`.
