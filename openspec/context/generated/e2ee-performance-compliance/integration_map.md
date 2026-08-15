# Integration Map

_Generated: 2026-08-15_

## Redis (Cache + State Store)

- Client: `RedisTemplate<String, Any>` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Protocol: Spring Data Redis
- Usage patterns:
  - `StringRedisTemplate` — OTP storage, rate limit counters, MFA session state
  - `RedisTemplate<String, Any>` — Permission cache L2 (serialized list)
  - Operations: `opsForValue().set()`, `opsForValue().get()`, `delete()`, `expire()`, `hasKey()`, `SETNX` (implicit via `increment`)
  - TTL patterns: 5-minute (MFA session), configurable (rate limit windows)
- Consumers:
  - `MfaService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt` (TOTP setup pending secret, resend counter)
  - `OtpService` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt` (OTP store, verification attempts)
  - `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` (login attempts, lock keys)
  - `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` (MFA attempts, lock keys)
  - `MultiTierPermissionCache` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cache/MultiTierPermissionCache.kt` (L2 permission cache)

## HTTP Client — CAPTCHA Provider

- Client: `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- Protocol: REST (HTTP POST)
- Request: POST to captcha verify URL with token
- Response: `CaptchaVerifyResponse` (same file, L25)

## HTTP Client — SSO Provider

- Client: `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`
- Protocol: REST (HTTP POST/GET — OAuth2 token exchange + user info)
- Request: OAuth2 authorization code exchange
- Response: `SsoTokenResponse` (L25), `SsoUserInfoResponse` (L33)

## HTTP Client Config

- Config: `HttpClientConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/HttpClientConfig.kt`
- Protocol: Spring RestClient builder with connection pooling

## PostgreSQL (JPA)

- Config: `JpaAuditingConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/JpaAuditingConfig.kt`
- Entities:
  - `UserEntity` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/entity/UserEntity.kt`
  - RBAC entities (Domain, Role, Group, Resource, Permission) — `rbac/adapter/out/persistence/entity/`
- Repository pattern: Spring Data JPA repositories

## Cloud KMS (Proposed — NEW)

NOT DETECTED — to be added for E2EE feature

## Kafka/RabbitMQ (Message Queue)

NOT DETECTED

## gRPC

NOT DETECTED — no gRPC dependency in project
