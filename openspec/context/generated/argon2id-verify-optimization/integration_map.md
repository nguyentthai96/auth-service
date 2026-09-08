# Integration Map

_Generated: 2026-09-08_

## NOT DETECTED

Feature `argon2id-verify-optimization` là config-only change. Không có external integration:

- REST clients: NOT DETECTED (no external API calls)
- Message queues: NOT DETECTED (no async messaging)
- Cache: NOT DETECTED (feature doesn't change cache behavior)
- Database: NOT DETECTED (feature doesn't change schema — PasswordUpgradeService already handles DB writes)

## Internal Integration (Within auth-service)

- `PasswordEncoderAutoConfiguration` → reads `SecurityProperties.password.argon2` → creates `Argon2PasswordEncoder`
- `LoginHandler` → calls `ConcurrencyLimitedPasswordEncoder.matches()` → delegates to `Argon2PasswordEncoder`
- `PasswordUpgradeService` → calls `ConcurrencyLimitedPasswordEncoder.upgradeEncoding()` + `encode()`
