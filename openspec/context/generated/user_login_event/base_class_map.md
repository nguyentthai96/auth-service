# Base Class Map

_Generated: 2025-08-21 | Services: auth-service (auth module, shared module)_

## Controller

- CqrsAuthController — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - Injects: `LoginHandler`, `RegisterHandler`, `RefreshTokenHandler`
  - Login endpoint: `POST /api/auth/login`

## Handler (CQRS CommandHandler)

- LoginHandler — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
  - implements: `CommandHandler<LoginCommand, LoginResult>`
  - 12 constructor dependencies (UserPort, DomainPort, TokenStore, CaptchaGateway, PermissionCache, SecurityProperties, TokenGenerator, PasswordPolicyService, LoginRateLimitService, SessionPolicyService, LoginSessionService, SessionPromotionService)
  - @Transactional on `handle()` method

- RegisterHandler — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
  - implements: `CommandHandler<RegisterCommand, AuthResponse>`
  - Injects `EventService` — **reference pattern** for login event integration

- AnonymousSessionHandler — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt`
  - follows LoginHandler pattern — CQRS CommandHandler

- TokenGenerator — `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt`
  - Shared token generation logic — used by LoginHandler, RegisterHandler, RefreshTokenHandler

## Domain Event Classes

- UserLoggedInEvent — `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`
  - implements: `DomainEvent`
  - Fields: userId (Long), domainCode (String)
  - eventType: `USER_LOGGED_IN`
  - ⚠️ Minimal payload — needs enrichment (FR-001)
  - ⚠️ Wrong location — should be in `auth.domain.event` package (FR-002)

- SessionRevokedEvent — `src/main/kotlin/com/ntt/authservice/auth/application/command/AuthDomainEvents.kt`
  - implements: `DomainEvent`
  - eventType: `SESSIONS_REVOKED`

- UserRegisteredEvent — `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`
  - implements: `DomainEvent`
  - Enriched payload (userId, username, email, fullName, phone, domainCode, domainId, status, registrationSource, ipAddress, userAgent)
  - eventType: `iam.user.registered`
  - **Reference pattern** for new UserLoggedInEvent

- EventEnvelope<T> — `src/main/kotlin/com/ntt/authservice/auth/domain/event/EventEnvelope.kt`
  - Generic CloudEvents-inspired wrapper
  - Fields: id (UUID), type, source, specversion, time, correlationId, schemaVersion, data

## Service (Application Layer)

- EventService — `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
  - record() method: wraps DomainEvent in EventEnvelope → event store + outbox (same TX)
  - Dependencies: EventStorePort, OutboxPort, ObjectMapper

- LoginSessionService — `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt`
  - recordLogin(): records login session + device info
  - detectNewDevice(): checks if device is new for user

- LoginRateLimitService — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt`
  - recordFailedAttempt(), resetOnSuccess()

- SessionPromotionService — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt`
  - promoteSession(): promotes anonymous session on login

## Port (Hexagonal)

- DomainEvent (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/DomainEvent.kt`
  - Property: eventType: String

- EventStorePort — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventStorePort.kt`
  - append(), getNextSequenceNumber()

- OutboxPort — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/OutboxPort.kt`
  - insert()

- EventPublisher — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`

## Cache

- AbstractTwoTierCache<K, V> — `src/main/kotlin/com/ntt/authservice/shared/cache/AbstractTwoTierCache.kt`
  - L1 (Caffeine) → L2 (Redis) two-tier cache pattern

## Filter

- LoginRateLimitFilter — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/LoginRateLimitFilter.kt`
  - Pre-handler IP-based rate limiting
  - Runs BEFORE request reaches LoginHandler

- IdempotencyFilter — `src/main/kotlin/com/ntt/authservice/shared/filter/IdempotencyFilter.kt`
  - X-Idempotency-Key header → Redis cached response

## NOT DETECTED

- Factory classes — NOT DETECTED
- Client / Gateway (external HTTP) — NOT DETECTED (CaptchaGateway is port interface only)
