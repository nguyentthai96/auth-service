# Plan 008: Add MFA and SSO unit tests

> **Executor instructions**: Follow this plan step by step.
>
> **Drift check (run first)**: `git diff --stat 732b0f5..HEAD -- src/test/kotlin/com/ntt/authservice/`

## Status

- **Priority**: P2
- **Effort**: L
- **Risk**: LOW
- **Depends on**: 002
- **Category**: tests
- **Planned at**: commit `732b0f5`, 2026-08-06

## Why this matters

Auth Core Features introduced 4 new services (`MfaService`, `OtpService`, `TotpService`, `PasswordPolicyService`), 3 new controllers (`MfaController`, `SsoController`, `TokenController`), and modified `SsoAdapter`. Only `LoginHandlerTest` exists — zero test coverage for MFA/SSO/Password Policy flows.

The project rule mandates >80% test coverage. Current estimated coverage of new code: ~15%.

## Current state

- Existing tests: `LoginHandlerTest.kt` (4 tests), `UserTest.kt`, `ArchitectureTest.kt`, `AuthControllerIntegrationTest.kt`
- Test pattern: Mockito + JUnit5 with `@ExtendWith(MockitoExtension::class)`, `@DisplayName`, constructor injection mocking via `@Mock`
- Model after: `LoginHandlerTest.kt` for unit test structure

## Steps

### Step 1: Create MfaServiceTest

Create `src/test/kotlin/com/ntt/authservice/auth/application/MfaServiceTest.kt`:

Test cases:
1. `should initiate OTP MFA and return MfaRequired result`
2. `should initiate TOTP MFA and return MfaRequired result`
3. `should throw for unsupported MFA method`
4. `should verify OTP MFA and return AuthResponse`
5. `should verify TOTP MFA and return AuthResponse`
6. `should throw MfaTokenExpiredException for invalid mfaToken`
7. `should setup TOTP and store pending secret in Redis`
8. `should confirm TOTP and persist encrypted secret`
9. `should resend OTP with new mfaToken`
10. `should update MFA settings`
11. `should throw TotpNotSetupException when enabling TOTP without setup`

### Step 2: Create OtpServiceTest

Create `src/test/kotlin/com/ntt/authservice/auth/application/OtpServiceTest.kt`:

Test cases:
1. `should generate 6-digit OTP and store in Redis`
2. `should verify correct OTP and delete keys`
3. `should throw MfaCodeInvalidException for wrong OTP`
4. `should throw MfaMaxAttemptsException after max attempts`
5. `should throw MfaCodeInvalidException for expired OTP`

### Step 3: Create PasswordPolicyServiceTest

Create `src/test/kotlin/com/ntt/authservice/auth/application/PasswordPolicyServiceTest.kt`:

Test cases:
1. `should validate strong password against policy`
2. `should return violations for weak password`
3. `should check password history — reject recently used`
4. `should allow password not in history`
5. `should change password successfully`
6. `should throw InvalidCredentialsException for wrong old password`
7. `should throw PasswordPolicyViolationException for weak new password`
8. `should detect expired password`
9. `should not expire password when maxAgeDays is 0`

### Step 4: Create SsoAdapterTest (after Plan 002)

Create `src/test/kotlin/com/ntt/authservice/auth/application/SsoAdapterTest.kt`:

Test cases:
1. `should handle callback with existing identity`
2. `should JIT provision new SSO user when autoProvision enabled`
3. `should throw SsoUserNotProvisionedException when autoProvision disabled`
4. `should link identity to existing user`
5. `should throw SsoIdentityConflictException for duplicate link`
6. `should unlink identity`
7. `should throw CannotUnlinkLastIdentityException for SSO-only user`

**Verify**: `./gradlew test` → all new and existing tests pass

## Done criteria

- [x] `./gradlew test` exits 0
- [x] At least 30 new test cases across 4 test files
- [x] No existing tests broken

## STOP conditions

- Plan 002 is not done (for SsoAdapterTest).
- Test infrastructure doesn't support Redis mocking — need embedded Redis or Testcontainers.
