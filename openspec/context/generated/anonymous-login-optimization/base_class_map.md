# Base Class Map

_Generated: 2025-01-20 | Services: auth-service_

## Controller

- `AnonymousAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt` — `@RestController`, `@RequestMapping("/api/v1/auth/anonymous")`
- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` — `@RestController`, login/register endpoints (promotion integration)
- `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt` — `@RestController`
- `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt` — `@RestController`
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt` — `@RestController`
- `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt` — `@RestController`
- `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt` — `@RestController`
- `CaptchaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt` — `@RestController`
- `AccountLifecycleController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt` — `@RestController`
- `AdminSessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt` — `@RestController`
- `RateLimitAdminController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt` — `@RestController`
- `KeyExchangeController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/KeyExchangeController.kt` — `@RestController`

## Handler (CQRS CommandHandler)

- `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — implements `CommandHandler<CreateAnonymousSessionCommand, AnonymousSessionResult>`
- `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` — implements `CommandHandler<RenewAnonymousTokenCommand, AnonymousSessionResult>`
- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` — implements `CommandHandler<LoginCommand, LoginResult>`
- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` — implements `CommandHandler<RegisterCommand, RegisterResult>`
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt` — implements `CommandHandler<RefreshTokenCommand, AuthToken>`
- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt` — implements `CommandHandler<RevokeSessionsCommand, Int>`
- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt` — implements `CommandHandler<SwitchDomainCommand, AuthToken>`

## Application Service

- `JwtService` — `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` — JWT generation/parsing (RS256 + HMAC fallback), anonymous token methods at lines 157 and 175
- `AnonymousSessionDataService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — Redis CRUD for anonymous session data with namespace isolation and size enforcement
- `AnonymousRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` — IP-based rate limiting with INCR+EXPIRE fixed window pattern
- `SessionPromotionService` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` — Orchestrates anonymous → authenticated promotion with distributed lock
- `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` — Multi-dimensional login rate limiting
- `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` — MFA verification rate limiting
- `SessionPolicyService` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPolicyService.kt` — Session policy enforcement
- `LoginSessionService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt` — Login session tracking
- `SessionCleanupScheduler` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt` — Scheduled session cleanup

## Client / Gateway

- `CaptchaGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt` — CAPTCHA verification port
- `HttpCaptchaGateway` (impl) — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt` — HTTP-based CAPTCHA gateway
- `CaptchaGatewayAdapter` (adapter) — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/gateway/CaptchaGatewayAdapter.kt` — CAPTCHA gateway adapter
- `SsoGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt` — SSO provider port
- `HttpSsoGateway` (impl) — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt` — HTTP-based SSO gateway

## Security Filter

- `JwtAuthFilter` — `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` — extends `OncePerRequestFilter`, handles anonymous (`ROLE_ANONYMOUS`) and authenticated token detection

## Factory

NOT DETECTED — No factory classes found. Token generation uses `TokenGenerator` component.

## NOT DETECTED

- Factory pattern (no `*Factory` classes)
