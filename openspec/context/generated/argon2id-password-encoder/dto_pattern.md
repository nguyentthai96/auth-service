# DTO Pattern

_Generated: 2026-09-08_

## Configuration DTO (Properties)

- SecurityProperties — `shared/config/SecurityProperties.kt` — annotations: `@ConfigurationProperties(prefix = "app.security")`
- PasswordProperties — `shared/config/SecurityProperties.kt:78` — nested data class of SecurityProperties
  - Fields: `bcryptStrength: Int = 12`, `maxFailedAttempts: Int = 3`, `lockDurationMinutes: Int = 15`
  - TO ADD: `algorithm: String`, `argon2: Argon2Properties`
- Argon2Properties — TO BE CREATED — nested data class of PasswordProperties
  - Fields: `saltLength`, `hashLength`, `parallelism`, `memoryCost`, `iterations`

## Request DTO

- NOT DETECTED (no new API request DTOs needed — transparent PasswordEncoder swap)

## Response DTO

- NOT DETECTED (no new API response DTOs needed)

## NOT DETECTED

- Filter DTOs
- Request/Response DTOs specific to password encoding (encoding is internal)
