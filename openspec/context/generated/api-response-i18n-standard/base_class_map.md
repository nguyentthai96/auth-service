# Base Class Map

_Generated: 2026-08-22 | Services: auth-service_

## Controller

- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - Annotation: `@RestController`, `@RequestMapping("/api/auth")`, `@ConditionalOnProperty(name = ["app.security.cqrs.enabled"])`
  - Injects: `LoginHandler`, `RegisterHandler`, `RefreshTokenHandler`, `SwitchDomainHandler`, `RevokeSessionsHandler`, `BuildAuthResponseHandler`, `PasswordPolicyService`, `LoginSessionService`, `SecurityProperties`, `MessageSource`, `JwtService`
- `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
  - Annotation: `@RestController`, `@RequestMapping("/api/auth")`
- `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
  - Annotation: `@RestController`, `@RequestMapping("/api/auth/mfa")`
- `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
  - Annotation: `@RestController`, `@RequestMapping("/api/auth/sessions")`
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
  - Annotation: `@RestController`
- `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
  - Annotation: `@RestController`, `@RequestMapping("/api/auth/sso")`
- `CaptchaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt`
  - Annotation: `@RestController`
- `KeyExchangeController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/KeyExchangeController.kt`
  - Annotation: `@RestController`
- `AnonymousAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt`
  - Annotation: `@RestController`
- `AccountLifecycleController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
  - Annotation: `@RestController`
- `AdminSessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt`
  - Annotation: `@RestController`
- `RateLimitAdminController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt`
  - Annotation: `@RestController`

## ControllerAdvice (Exception Handler)

- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - Annotation: `@RestControllerAdvice`
  - Injects: `LocalValidatorFactoryBean`, `MessageSource`
  - Methods: `handleAuthException()`, `extractMessageArgs()`, `resolveMessage()`, `setContentLanguageHeader()`

## Filter

- `ClientMetadataFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ClientMetadataFilter.kt`
  - extends: `OncePerRequestFilter`
  - Annotation: `@Component`, `@Order(HIGHEST_PRECEDENCE + 10)`
  - Purpose: Extract X-App-Version, X-Client-Platform → MDC
- `ContentLanguageFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ContentLanguageFilter.kt`
  - extends: `OncePerRequestFilter`
  - Annotation: `@Component`, `@Order(LOWEST_PRECEDENCE - 10)`
  - Purpose: Set Content-Language response header from LocaleContextHolder
- `LoginRateLimitFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt`
  - extends: `OncePerRequestFilter`
  - Annotation: `@Component`
  - Purpose: Pre-auth rate limiting for POST /api/auth/login

## Handler (CQRS Command Handlers)

- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`
- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt`
- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt`
- `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt`
- `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt`

## Query Handler

- `BuildAuthResponseHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseHandler.kt`

## MessageSource (i18n)

- `DatabaseMessageSource` — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
  - extends: `AbstractMessageSource` (Spring)
  - Purpose: DB-backed MessageSource with Caffeine cache (5-min TTL, maxSize=500)
- `I18nConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt`
  - Annotation: `@Configuration`
  - Provides: `localeResolver()` (AcceptHeaderLocaleResolver), `messageSource()` (CompositeMessageSource)

## Factory

NOT DETECTED

## Client / Gateway

- `CaptchaClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
  - Protocol: HTTP (RestTemplate)
- `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
  - Protocol: HTTP
- `OAuth2TokenExchanger` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`
  - Protocol: HTTP (RestTemplate)
