# Base Class Map

_Generated: 2026-08-25 | Services: auth-service_

## Controller

- AuthControllerAdvice — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: BaseControllerAdvice (base-core)
  - implements: N/A
  - injects: LocalValidatorFactoryBean, MessageSource
- CqrsAuthController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
  - injects: LoginHandler, RegisterHandler, RefreshTokenHandler, SwitchDomainHandler, RevokeSessionsHandler, BuildAuthResponseHandler, PasswordPolicyService, LoginSessionService, DomainLookupService, SecurityProperties, MessageSource, JwtService
- AuthController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
  - extends: N/A (standalone @RestController, legacy — disabled when cqrs.enabled=true)
  - implements: N/A
- SessionController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
  - injects: LoginSessionService, MessageSource
- AccountLifecycleController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
  - injects: AccountLifecycleService, MessageSource
- MfaController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
  - injects: MfaService, AuthService
- TokenController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- AnonymousAuthController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- SsoController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- AdminSessionController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- RateLimitAdminController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- CaptchaController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- KeyExchangeController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/KeyExchangeController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- InternalApiController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/InternalApiController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- PolicyController — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
- RbacControllers — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
  - extends: N/A (5 @RestControllers in one file)
  - implements: N/A
- RolePermissionController — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A

## Handler

NOT DETECTED (Handlers are application-layer services, not base-class patterns)

## Factory

NOT DETECTED

## Client / Gateway

- CaptchaClient — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
  - type: REST (declarative HTTP client for CAPTCHA verification)
- HttpCaptchaGateway — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
  - type: REST gateway
- OAuth2TokenExchanger — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/sso/OAuth2TokenExchanger.kt`
  - type: REST (RestTemplate for SSO token exchange)

## Filter (extends OncePerRequestFilter)

- JwtAuthFilter — `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`
- IdempotencyFilter — `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`
- ContentLanguageFilter — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ContentLanguageFilter.kt`
- ClientMetadataFilter — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ClientMetadataFilter.kt`
- LoginRateLimitFilter — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt`
- ServiceAuthFilter — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ServiceAuthFilter.kt`

## i18n (extends AbstractMessageSource)

- DatabaseMessageSource — `src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt`
  - extends: AbstractMessageSource (Spring)
  - cache: Caffeine (maxSize=500, 5-min TTL)
  - repository: I18nMessageRepository
