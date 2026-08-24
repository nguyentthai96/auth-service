# Base Class Map

_Generated: 2025-08-22 | Services: auth-service (auth module)_

## Controller

- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - extends: N/A (direct @RestController)
  - implements: N/A
  - note: CQRS-based auth controller, dispatches to command/query handlers. Contains `login()` method that builds LoginCommand and delegates to LoginHandler.

- `EventStoreController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/EventStoreController.kt`
  - extends: N/A
  - implements: N/A
  - note: Internal API for querying events from event store. GET /api/internal/events/{aggregateType}/{aggregateId}

## Handler (CQRS CommandHandler)

- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
  - extends: N/A
  - implements: `CommandHandler<LoginCommand, LoginResult>` (from `com.ntt.eventsourcingutils.lib.cqrs.command.CommandHandler`)
  - note: **PRIMARY integration point** — handles login command, validates credentials, generates tokens. Currently 12 constructor dependencies. @Transactional.

- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
  - extends: N/A
  - implements: `CommandHandler<RegisterCommand, RegisterResult>`
  - note: **Pattern reference** — demonstrates EventService integration for UserRegisteredEvent recording.

- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`
  - extends: N/A
  - implements: `CommandHandler<RefreshTokenCommand, AuthToken>`

- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt`
  - extends: N/A
  - implements: `CommandHandler<RevokeSessionsCommand, Int>`

- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt`
  - extends: N/A
  - implements: `CommandHandler<SwitchDomainCommand, AuthToken>`

## Helper Service (Event Recording)

- `TokenEventRecorder` — `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt`
  - extends: N/A
  - implements: N/A
  - note: **PRIMARY pattern reference** for LoginEventRecorder. @Component. Injects EventService. Methods: recordIssuance(), recordRevocation(), recordValidationFailure(). Fire-and-forget: try/catch all exceptions, log.warn on failure.

- `EventService` — `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
  - extends: N/A
  - implements: N/A
  - note: Intermediary service for transactional event recording. Creates EventEnvelope, persists to event store + outbox. Generic `record()` method accepts any DomainEvent.

## Factory

NOT DETECTED

## Client / Gateway

- `CaptchaGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt`
  - note: Used by LoginHandler for CAPTCHA verification

## NOT DETECTED

- Factory (no factory classes in auth module — handlers are direct @Component)
