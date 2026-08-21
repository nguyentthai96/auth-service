# Base Class Map

_Generated: 2026-08-26 | Services: auth-service (auth, rbac, shared modules)_

## Controller

- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
  - extends: N/A (plain @RestController)
  - endpoints: POST /api/auth/introspect, GET /.well-known/jwks.json, POST /api/auth/sessions/{userId}/revoke-all
  - dependencies: JwtService, AuthService, TokenBlacklistRepository, MessageSource

## Handler (Command)

- `TokenGenerator` — `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt`
  - extends: N/A (@Component)
  - role: Shared token generation for Login/Register/Refresh
  - dependencies: JwtService, TokenStore, TokenHasher, TokenEventRecorder

- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`
  - extends: N/A (@Component)
  - role: Token rotation — revoke old, generate new pair
  - dependencies: TokenStore, TokenGenerator, TokenEventRecorder

- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt`
  - extends: N/A (@Component)
  - role: Bulk session revocation
  - dependencies: TokenStore

## Service (Application)

- `JwtService` — `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
  - extends: N/A (@Service)
  - role: JWT generation (access/refresh/mfa/anonymous), parsing, JWKS
  - key methods: generateAccessToken(), generateRefreshToken(), parseToken(), getJwks()
  - dependencies: SecurityProperties

- `AuthService` — `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt`
  - extends: N/A
  - role: Core auth orchestration (login, logout, revocation)
  - dependencies: TokenBlacklistRepository, TokenStore, etc.

- `EventService` — `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
  - extends: N/A (@Component)
  - role: Transactional event recording (event store + outbox)
  - dependencies: EventStorePort, OutboxPort, ObjectMapper

- `TokenEventRecorder` — `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt`
  - extends: N/A (@Component)
  - role: Helper for token lifecycle event recording
  - dependencies: EventService

## Filter (Security)

- `JwtAuthFilter` — `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`
  - extends: `OncePerRequestFilter` (Spring)
  - role: JWT validation filter — parse, blacklist check, set SecurityContext
  - dependencies: JwtService, TokenBlacklistRepository

## Client / Gateway

- `HttpSsoGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
  - implements: `SsoGateway`
  - role: SSO provider token exchange
  - protocol: HTTP (WebClient/RestClient)

- `HttpCaptchaGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
  - role: CAPTCHA verification gateway
  - protocol: HTTP

## Port (Interface)

- `TokenStore` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/TokenStore.kt`
  - type: Outbound port (interface)
  - methods: saveRefreshToken(), findValidRefreshToken(), revokeToken(), revokeAllForUser(), blacklistToken()
  - adapter: `TokenStorePersistenceAdapter`

- `EventStorePort` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventStorePort.kt`
  - type: Outbound port (interface)
  - role: Event store persistence

- `OutboxPort` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/OutboxPort.kt`
  - type: Outbound port (interface)
  - role: Transactional outbox for Kafka

## NOT DETECTED

- Factory classes (no `*Factory` found in auth/shared modules — exception: `TinkCipherAlgorithmFactory` in cipher package, unrelated)
- Abstract base controller
