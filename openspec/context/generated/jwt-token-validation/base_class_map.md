# Base Class Map

_Generated: 2025-01-20 | Services: auth-service (auth, rbac, shared modules)_

## Controller

- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
  - extends: N/A (plain @RestController)
  - implements: N/A
  - endpoints: POST /api/auth/introspect, GET /.well-known/jwks.json, POST /api/auth/sessions/{userId}/revoke-all

## Filter (OncePerRequestFilter)

- `JwtAuthFilter` — `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`
  - extends: `OncePerRequestFilter` (Spring Web)
  - implements: N/A
  - scope: Every HTTP request — PEP for JWT validation

- `ServiceAuthFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ServiceAuthFilter.kt`
  - extends: `OncePerRequestFilter`
  - implements: N/A
  - scope: /internal/** paths — service-to-service auth

- `LoginRateLimitFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt`
  - extends: `OncePerRequestFilter`
  - implements: N/A

- `ContentLanguageFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ContentLanguageFilter.kt`
  - extends: `OncePerRequestFilter`
  - implements: N/A

- `ClientMetadataFilter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ClientMetadataFilter.kt`
  - extends: `OncePerRequestFilter`
  - implements: N/A

## Service (Application Layer)

- `JwtService` — `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
  - extends: N/A
  - implements: N/A
  - annotation: @Service
  - responsibilities: JWT generation (RS256/HMAC), token parsing with cascading fallback, JWKS generation

- `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
  - extends: N/A
  - implements: N/A
  - annotation: @Service
  - responsibilities: Three-tier blacklist cache (Caffeine L1 + Redis L2 + DB fallback), circuit breaker

- `ServiceTokenService` — `src/main/kotlin/com/ntt/authservice/auth/application/ServiceTokenService.kt`
  - extends: N/A
  - implements: N/A
  - annotation: @Service
  - responsibilities: Service-to-service JWT generation/validation

## Component (Chain of Responsibility)

- `ClaimValidatorChain` — `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidatorChain.kt`
  - extends: N/A
  - implements: N/A
  - annotation: @Component
  - responsibilities: Orchestrates ClaimValidator beans (fail-fast + collect-all modes)

- `IssuerClaimValidator` — `src/main/kotlin/com/ntt/authservice/auth/application/IssuerClaimValidator.kt`
  - extends: N/A
  - implements: `ClaimValidator`
  - annotation: @Component

- `AudienceClaimValidator` — `src/main/kotlin/com/ntt/authservice/auth/application/AudienceClaimValidator.kt`
  - extends: N/A
  - implements: `ClaimValidator`
  - annotation: @Component

- `TokenTypeClaimValidator` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenTypeClaimValidator.kt`
  - extends: N/A
  - implements: `ClaimValidator`
  - annotation: @Component

## Interface

- `ClaimValidator` — `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidator.kt`
  - type: Interface (Chain of Responsibility contract)
  - methods: `validate(claims: Claims): ClaimValidationResult`

- `TokenStore` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/TokenStore.kt`
  - type: Interface (Outbound port)
  - methods: `saveRefreshToken()`, `findValidRefreshToken()`, `revokeToken()`, `revokeAllForUser()`, `blacklistToken()`

- `DomainEvent` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - type: Interface
  - property: `eventType: String`

## Event Recorder (Helper)

- `TokenEventRecorder` — `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt`
  - extends: N/A
  - implements: N/A
  - annotation: @Component
  - responsibilities: Records token lifecycle events (issuance, revocation, validation failure)

- `EventService` — `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
  - extends: N/A
  - implements: N/A
  - annotation: @Component
  - responsibilities: Transactional event recording (event store + outbox pattern)

## Exception Hierarchy

- `AuthException` — `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: `BusinessException` (base-core)
  - base for all auth exceptions

- `AuthControllerAdvice` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - annotation: @RestControllerAdvice

## NOT DETECTED

- Factory
- Gateway (no external HTTP client classes detected for JWT validation scope)
