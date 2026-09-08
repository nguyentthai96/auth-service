# DTO Pattern

_Generated: 2026-09-08_

## NOT DETECTED

Feature `argon2id-verify-optimization` không thay đổi DTOs:

- Request DTOs: NOT DETECTED (no API contract changes)
- Response DTOs: NOT DETECTED (no API contract changes)
- Config DTOs (Type-safe properties):

## Configuration Properties (Relevant)

- `SecurityProperties.PasswordProperties.Argon2Properties` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt`
  - Fields: `saltLength`, `hashLength`, `parallelism`, `memoryCost`, `iterations`
  - Annotations: `@ConfigurationProperties(prefix = "app.security")`
  - Bound to: `application-security.yml` → `app.security.password.argon2`
  - **Change**: `iterations` value changes from 3 → 1 (YAML only, no DTO change)
