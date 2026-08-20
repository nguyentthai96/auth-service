# Integration Map

_Generated: 2026-08-22_

## Spring MessageSource (i18n Resolution)

- Client: `DatabaseMessageSource` — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
- Protocol: In-process (Spring MessageSource interface)
- Config: `I18nConfig.kt` — CompositeMessageSource chain: DatabaseMessageSource → ReloadableResourceBundleMessageSource
- Request: `getMessage(code: String, args: Array<Any>?, locale: Locale)` → String
- Response: Localized message string (interpolated)
- Consumers: `AuthControllerAdvice.resolveMessage()`, `CqrsAuthController.messageSource.getMessage()`

## PostgreSQL (i18n_messages table)

- Client: `I18nMessageRepository` — `src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageRepository.kt`
- Protocol: JPA/JDBC
- Entity: `I18nMessageEntity` — `src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageEntity.kt`
- Request: `findByCodeAndLocaleAndIsActiveTrue(code, locale)` → `I18nMessageEntity?`
- Response: `I18nMessageEntity` (code, locale, message, module, is_active, created_at, updated_at)
- Consumer: `DatabaseMessageSource.resolveCode()`

## Caffeine Cache (i18n Message Cache)

- Client: `DatabaseMessageSource` — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
- Protocol: In-process (Caffeine cache)
- Config: `maximumSize=500`, `expireAfterWrite=5 MINUTES`
- Key format: `"{code}:{locale.language}"` (e.g., `"auth.rate_limited:vi"`)
- Purpose: Avoids DB hit per request, transparent cache-aside pattern

## Redis (Rate Limiting, Sessions, OTP)

- Client: `StringRedisTemplate` — Spring Data Redis
- Protocol: Redis protocol
- Config: `RedisConfig.kt` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Purpose: Rate limiting (LoginRateLimitService, MfaRateLimitService), OTP storage (OtpService), anonymous sessions (AnonymousSessionHandler), anti-replay (RedisAntiReplayValidator)
- Note: NOT used for i18n — i18n uses Caffeine in-process cache

## CAPTCHA Provider (External HTTP)

- Client: `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
- Gateway: `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- Protocol: REST (RestTemplate)
- Purpose: CAPTCHA verification (hCaptcha/reCAPTCHA/Altcha)

## SSO Provider (External HTTP)

- Client: `OAuth2TokenExchanger` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`
- Protocol: REST (RestTemplate — OAuth2 token exchange)
- Purpose: SSO identity provider integration

## NOT DETECTED

- Message Queue (Kafka producer) — NOT DETECTED for i18n scope
- External translation service — NOT DETECTED (by design — no Google Translate)
