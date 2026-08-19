# Base Class Map: anonymous-login-optimization

> _Generated: 2025-01-20_
> Candidate Service: auth-service (`src/main/kotlin/com/ntt/authservice/`)

---

## 1. Controller Base Classes

| Class | Package | File Path | Notes |
|-------|---------|-----------|-------|
| `CqrsAuthController` | `auth.adapter.in.web` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | CQRS REST controller, `@RestController`, `@RequestMapping("/api/auth")`, `@ConditionalOnProperty(name = ["app.security.cqrs.enabled"])`. Dispatches to LoginHandler, RegisterHandler, etc. |
| `AuthController` | `auth.adapter.in.web` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt` | Legacy controller (pre-CQRS) |
| `SessionController` | `auth.adapter.in.web` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt` | Session management endpoints |
| `TokenController` | `auth.adapter.in.web` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt` | Token introspection, uses `TokenBlacklistRepository` |
| `MfaController` | `auth.adapter.in.web` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt` | MFA verification endpoints |
| `CaptchaController` | `auth.adapter.in.web` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CaptchaController.kt` | CAPTCHA challenge endpoints |

**Pattern**: Controllers are plain `@RestController` classes — no shared base controller class. Each uses constructor injection.

---

## 2. Command Handler Base Classes (CQRS)

| Class | Package | File Path | Interface | Notes |
|-------|---------|-----------|-----------|-------|
| `LoginHandler` | `auth.application.command` | `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` | `CommandHandler<LoginCommand, LoginResult>` | Primary login handler, `@Component`, `@Transactional` |
| `RegisterHandler` | `auth.application.command` | `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` | `CommandHandler<RegisterCommand, AuthToken>` | User registration |
| `RefreshTokenHandler` | `auth.application.command` | `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt` | `CommandHandler<RefreshTokenCommand, AuthToken>` | Token refresh |
| `SwitchDomainHandler` | `auth.application.command` | `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt` | `CommandHandler<SwitchDomainCommand, AuthToken>` | Domain switching |
| `RevokeSessionsHandler` | `auth.application.command` | `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt` | `CommandHandler<RevokeSessionsCommand, Unit>` | Session revocation |

**Interface**: `CommandHandler<C, R>` from `com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler`
**Command Interface**: `Command<R>` from `com.ntt.eventsourcingutils.lib.cqrs.command.Command`

---

## 3. Query Handler Base Classes (CQRS)

| Class | Package | File Path | Notes |
|-------|---------|-----------|-------|
| `BuildAuthResponseHandler` | `auth.application.query` | `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseHandler.kt` | Query handler for auth response building |
| `BuildAuthResponseQuery` | `auth.application.query` | `src/main/kotlin/com/ntt/authservice/auth/application/query/BuildAuthResponseQuery.kt` | Query object |

---

## 4. Service Base Classes

| Class | Package | File Path | Notes |
|-------|---------|-----------|-------|
| `JwtService` | `auth.application` | `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` | `@Service`, RS256 + HMAC fallback, generates access/refresh/MFA tokens. Key methods: `generateAccessToken()`, `generateRefreshToken()`, `generateMfaToken()`, `parseMfaToken()`, `parseToken()` |
| `TokenGenerator` | `auth.application.command` | `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt` | `@Component`, shared token generation logic used by handlers |
| `LoginRateLimitService` | `auth.application` | `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` | `@Service`, multi-dimensional rate limiting (IP, username, device), Redis INCR+EXPIRE, fail-open |
| `MfaRateLimitService` | `auth.application` | `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` | `@Service`, MFA rate limiting, Redis INCR+EXPIRE, fail-open, audit logging |
| `SessionPolicyService` | `auth.application` | `src/main/kotlin/com/ntt/authservice/auth/application/SessionPolicyService.kt` | `@Service`, configurable session policy enforcement, per-role overrides |
| `LoginSessionService` | `auth.application` | `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt` | `@Service`, login session recording |
| `AuthService` | `auth.application` | `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` | `@Service`, legacy auth service (pre-CQRS) |

---

## 5. Security Filter Base Classes

| Class | Package | File Path | Notes |
|-------|---------|-----------|-------|
| `JwtAuthFilter` | `shared.security` | `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` | `@Component`, extends `OncePerRequestFilter`. Parses JWT → checks blacklist → extracts roles/permissions → sets SecurityContext |
| `LoginRateLimitFilter` | `auth.adapter.in.web.filter` | `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt` | Pre-auth rate limit filter |

---

## 6. Exception Base Classes

| Class | Package | File Path | Notes |
|-------|---------|-----------|-------|
| `AuthException` | `shared.exception` | `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt` | Base exception, extends `BusinessException` (base-core), carries `AuthErrorCode` + `HttpStatus` |
| `AuthControllerAdvice` | `shared.exception` | `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` | `@RestControllerAdvice`, extends `BaseControllerAdvice` (base-core), RFC 7807 ProblemDetail |

---

## 7. Persistence Base Classes

| Class | Package | File Path | Notes |
|-------|---------|-----------|-------|
| `VersionedAuditableEntity` | `shared.persistence` | `src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt` | Base JPA entity with audit fields |

---

## 8. Port Interfaces (Hexagonal Architecture)

| Interface | Package | File Path | Notes |
|-----------|---------|-----------|-------|
| `UserPort` | `auth.application.port.out` | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/UserPort.kt` | User persistence port |
| `DomainPort` | `auth.application.port.out` | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/DomainPort.kt` | Domain lookup port |
| `TokenStore` | `auth.application.port.out` | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/TokenStore.kt` | Token persistence port |
| `CaptchaGateway` | `auth.application.port.out` | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt` | CAPTCHA verification port |
| `PermissionCache` | `auth.application.port.out` | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/PermissionCache.kt` | Multi-tier permission cache port (L1 Caffeine + L2 Redis) |
| `SsoGateway` | `auth.application.port.out` | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/SsoGateway.kt` | SSO provider gateway port |
| `EventPublisher` | `auth.application.port.out` | `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt` | Domain event publishing port |
