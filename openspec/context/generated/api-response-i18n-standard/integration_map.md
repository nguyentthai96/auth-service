# Integration Map

_Generated: 2026-08-25_

## Spring MessageSource (i18n — primary integration)

- Client: DatabaseMessageSource — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
- Protocol: Spring AbstractMessageSource chain (DB → file bundles)
- Configuration: I18nConfig — `src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt`
- Request: `resolveCode(code: String, locale: Locale)` → `I18nMessageRepository.findByCodeAndLocaleAndIsActiveTrue(code, locale)`
- Response: `MessageFormat` (localized string) or `null` (delegate to parent)
- Cache: Caffeine (maxSize=500, 5-min TTL) — `DatabaseMessageSource.kt:22-25`

## PostgreSQL (i18n_messages table)

- Client: I18nMessageRepository — `src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageRepository.kt`
- Protocol: Spring Data JPA
- Entity: I18nMessageEntity — `src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageEntity.kt`
- Request: `findByCodeAndLocaleAndIsActiveTrue(code, locale)` — single row lookup
- Response: I18nMessageEntity (code, locale, message, module, isActive)

## Caffeine Cache (in-process)

- Client: DatabaseMessageSource — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
- Protocol: In-process (Caffeine library)
- Configuration: maxSize=500, expireAfterWrite=5 minutes
- Key format: `"${code}:${locale.language}"` (e.g., `"auth.invalid_credentials:vi"`)

## Redis (StringRedisTemplate)

- Client: Multiple services (LoginRateLimitService, MfaRateLimitService, OtpService, AnonymousSessionDataService, etc.)
- Protocol: Redis INCR/GET/SET via StringRedisTemplate
- Purpose: Rate limiting, OTP storage, anonymous sessions, anti-replay, idempotency
- Note: Not directly i18n-related — listed for completeness

## REST / HTTP

- Client: CaptchaClient — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
  - Protocol: Spring HTTP Interface (declarative)
  - Purpose: CAPTCHA verification
- Client: HttpCaptchaGateway — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
  - Protocol: REST gateway
- Client: OAuth2TokenExchanger — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`
  - Protocol: RestTemplate (SSO token exchange)

## NOT DETECTED

- Message Queue (Kafka consumer exists but no producer detected for i18n)
- External translation service (not used — server-side bundles only)
