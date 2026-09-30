# Base Class Map

_Generated: 2026-09-30 | Services: base-security-starter, auth-service_

## Filter & Security Auto-Configuration

- `SecurityAutoConfiguration` — `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityAutoConfiguration.kt` — `@AutoConfiguration`, registers `defaultSessionValidationFilter` via `FilterRegistrationBean` at order `OrderConstants.SESSION_VALIDATION` (-1700), `defaultJwtTokenExtractor`, and `DynamicAuthorizationManager`.
- `SecurityProperties` (base-security-starter) — `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityProperties.kt` — `@ConfigurationProperties(prefix = "app.security")`, holds `enabled`, `publicPaths`, `jwtIssuerUri`, `tokenRevocation`.
- `AbstractSessionValidationFilter` — `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/session/AbstractSessionValidationFilter.kt` — abstract servlet filter, intercepts requests, verifies Bearer token, bypasses `publicPaths`, returns 401 if missing Authorization header.
- `DefaultSessionValidationFilter` — `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/session/DefaultSessionValidationFilter.kt` — concrete implementation of `AbstractSessionValidationFilter` delegating token validation to `JwtTokenExtractor`.
- `JwtTokenExtractor` / `DefaultJwtTokenExtractor` — `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/jwt/DefaultJwtTokenExtractor.kt` — parses JWT and extracts claims/token ID.

## Service Security Chain (auth-service)

- `SecurityConfig` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` — `@Configuration`, `@EnableWebSecurity`, configures Spring Security `SecurityFilterChain`, declares `.requestMatchers("/auth/login", ...).permitAll()`, adds `LoginRateLimitFilter`, `JwtAuthFilter`, `ServiceAuthFilter`.
- `JwtAuthFilter` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` — `OncePerRequestFilter` inside Spring Security chain, performs comprehensive token validation with Redis blacklist cache check, `ClaimValidatorChain`, and device fingerprinting.
- `SecurityProperties` (auth-service) — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` — `@ConfigurationProperties(prefix = "app.security")`, contains auth-service specific configurations (JWT keys, password hashing, MFA, captcha, SSO, rate limits).

## Controller

- `CqrsAuthController` — `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` — `@RestController`, `@RequestMapping("/auth")`, `@PostMapping("/login")` endpoint receiving login credentials.
- `BaseController` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/BaseController.kt` — abstract base controller injecting `RequestContext` and `MessageSource`.

## Handler / Pipeline

- `LoginHandler` — `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` — handles `LoginCommand` via `AuthenticationPipeline`.
- `AuthenticationPipeline` — `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/pipeline/AuthenticationPipeline.kt` — executes authentication steps (security pre-checks, credential validation, token generation).
