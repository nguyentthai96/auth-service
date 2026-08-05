# Integration Map

_Generated: 2026-08-05_

## PostgreSQL (JPA/Hibernate)

- Client: Spring Data JPA Repositories
  - `Repositories.kt` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt`
  - `PolicyRepository.kt` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/out/persistence/repository/PolicyRepository.kt`
- Protocol: JPA/JDBC (via `base-data-starter` → HikariCP)
- Connection: `pda_user:pda_secret@localhost:5432/pda_db`
- Entities: 18 JPA entities extending `SnowflakePersistentAuditableEntity` or `SnowflakeBaseEntity`

## Redis

- Client: `StringRedisTemplate`
  - `OtpService.kt` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt`
  - `MfaService.kt` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
- Protocol: Redis (Lettuce driver via Spring Data Redis)
- Usage: OTP storage (TTL-based), MFA pending secrets, token blacklist

## Kafka

- Client: `KafkaTemplate<String, String>`
  - `SsoAdapter.kt` — `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`
- Protocol: Kafka Producer
- Topics: SSO user sync events
- Notes: No Kafka consumer detected in auth-service

## SSO Providers (External REST)

- Client: `SsoAdapter` — `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`
- Protocol: REST/OAuth2 (Google, GitHub, Microsoft)
- HTTP Client: **Inline `RestTemplate()`** — no connection pooling, no timeout config ⚠️
- Request: OAuth2 token exchange
- Response: User profile info

## reCAPTCHA (External REST)

- Client: `CaptchaVerifier` — `src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt`
- Protocol: REST POST to Google reCAPTCHA API
- HTTP Client: **Injected `RestTemplate`** (via `@Bean`)
- Request: CAPTCHA token + secret
- Response: Verification result (success/failure + score)

## NOT DETECTED

- gRPC — No gRPC client or server
- Message Queue Consumer — No `@KafkaListener` in auth-service
- @HttpExchange — No declarative HTTP clients
- WebClient — No reactive HTTP clients
- Caffeine — No in-process cache
