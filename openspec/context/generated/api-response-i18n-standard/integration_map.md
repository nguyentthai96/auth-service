# Integration Map

_Generated: 2026-08-27_

## Spring MessageSource (i18n)

- Client: `I18nConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt`
- Protocol: Spring Bean (built-in)
- Type: i18n / Localization
- Chain: `DatabaseMessageSource` (Caffeine cached) → file bundles (`auth-messages*.properties`) → default
- Consumers:
  - `AuthControllerAdvice` (`GlobalExceptionHandler.kt`) — error message resolution
  - `CqrsAuthController` — 5 success message endpoints
  - `SessionController` — 2 success message endpoints
  - `AccountLifecycleController` — 3 success message endpoints
  - `MfaController` — 2 success message endpoints
  - `SsoController` — 2 success message endpoints
  - `AdminSessionController` — 1 success message endpoint
  - `RateLimitAdminController` — 1 success message endpoint
  - `TokenController` — 1 success message endpoint
  - `LoginRateLimitFilter` — 1 rate limit response

## DatabaseMessageSource (DB-backed i18n)

- Client: `DatabaseMessageSource` — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
- Protocol: JPA → PostgreSQL
- Type: DB / Cache
- Cache: Caffeine (maxSize=500, TTL=5min)
- Entity: `I18nMessageEntity` — `src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageEntity.kt`
- Repository: `I18nMessageRepository` — `src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageRepository.kt`

## Caffeine Cache (in-process)

- Client: `DatabaseMessageSource` — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
- Protocol: In-memory
- Type: Cache
- Config: maxSize=500, expireAfterWrite=5min

## Redis (StringRedisTemplate)

- Client: Multiple services (RateLimitService, OTP, sessions, anti-replay)
- Protocol: Redis
- Type: Cache / Rate Limiting
- Note: Not directly i18n-related; rate limiting integration point only

## react-i18next (Frontend i18n)

- Client: `i18n.ts` — `admindashboard/src/@i18n/i18n.ts`
- Protocol: Client-side JS
- Type: Frontend i18n
- Note: Separate from server-side i18n; syncs language setting via `Accept-Language` header

## NOT DETECTED

- External REST API clients (no outbound HTTP calls for i18n)
- Message Queue consumers/producers (for i18n)
- gRPC clients
