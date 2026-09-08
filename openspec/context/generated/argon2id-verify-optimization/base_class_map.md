# Base Class Map

_Generated: 2026-09-08 | Services: auth-service_

## Controller

NOT DETECTED — Feature không liên quan đến controller layer. Config-only change.

## Handler

- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
  - extends: N/A (implements command handler pattern)
  - implements: N/A
  - Relevance: Primary call point cho `matchesPassword()` — verify path sẽ nhanh hơn

## Factory

NOT DETECTED

## Client / Gateway

NOT DETECTED

## Configuration (Feature-Specific)

- `PasswordEncoderAutoConfiguration` — `src/main/kotlin/com/ntt/authservice/shared/config/PasswordEncoderAutoConfiguration.kt`
  - extends: N/A
  - implements: Spring `@Configuration`
  - Relevance: Reads Argon2 config → creates `Argon2PasswordEncoder` → **TARGET FILE** (reads from YAML)

- `ConcurrencyLimitedPasswordEncoder` — `src/main/kotlin/com/ntt/authservice/shared/config/ConcurrencyLimitedPasswordEncoder.kt`
  - extends: N/A
  - implements: `PasswordEncoder`
  - Relevance: Wraps encoder with Semaphore — no change needed

- `SecurityProperties` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt`
  - extends: N/A
  - implements: `@ConfigurationProperties(prefix = "app.security")`
  - Relevance: Type-safe YAML binding — reads `password.argon2.iterations`

## Service (Application Layer)

- `PasswordUpgradeService` — `src/main/kotlin/com/ntt/authservice/auth/application/PasswordUpgradeService.kt`
  - extends: N/A
  - implements: N/A (Spring `@Service`)
  - Relevance: Handles transparent password rehash — will auto-migrate t=3→t=1

- `PasswordPolicyService` — `src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt`
  - extends: N/A
  - implements: N/A (Spring `@Service`)
  - Relevance: Password history check — benefits from faster verify

## NOT DETECTED

- Abstract base classes (project doesn't use abstract base class pattern)
- AGW Client interfaces
- Repository base classes (feature doesn't interact with repositories directly)
