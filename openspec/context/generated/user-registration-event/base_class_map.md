# Base Class Map

_Generated: 2025-08-22 | Services: auth-service (auth module)_

## Controller

- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - Handles POST /api/auth/register, delegates to RegisterHandler

## Handler (CQRS Command Handlers)

- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
  - extends: `CommandHandler<RegisterCommand, RegisterResult>` (from eventsourcing-utils)
  - Purpose: CQRS command handler for user registration
  - **TARGET FOR MODIFICATION** — replace direct EventService.record() with RegistrationEventRecorder delegation

- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
  - extends: `CommandHandler<LoginCommand, LoginResult>`
  - Purpose: CQRS command handler for user login
  - **PATTERN REFERENCE** — uses LoginEventRecorder for fire-and-forget event recording

## Event Recorders (Helper Services — fire-and-forget pattern)

- `LoginEventRecorder` — `src/main/kotlin/com/ntt/authservice/auth/application/event/LoginEventRecorder.kt`
  - @Component, injects EventService
  - Methods: recordLoginSuccess(), recordLoginFailure()
  - **PRIMARY PATTERN REFERENCE** for RegistrationEventRecorder

- `TokenEventRecorder` — `src/main/kotlin/com/ntt/authservice/auth/application/event/TokenEventRecorder.kt`
  - @Component, injects EventService
  - Methods: recordIssuance(), recordRevocation(), recordValidationFailure()
  - **SECONDARY PATTERN REFERENCE**

## Event Service

- `EventService` — `src/main/kotlin/com/ntt/authservice/auth/application/event/EventService.kt`
  - @Component
  - Method: record(aggregateType, aggregateId, event, topic, partitionKey, correlationId)
  - Wraps events in EventEnvelope → EventStorePort.append() + OutboxPort.insert()

## Domain Events (DomainEvent interface implementations)

- `UserRegisteredEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserRegisteredEvent.kt`
  - implements: `DomainEvent`
  - eventType: "iam.user.registered"
  - Fields: userId, username, email, fullName, phone, domainCode, domainId, status, registrationSource, ipAddress, userAgent

- `UserLoggedInEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoggedInEvent.kt`
  - implements: `DomainEvent`
  - eventType: "iam.user.logged_in"

- `UserLoginFailedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/UserLoginFailedEvent.kt`
  - implements: `DomainEvent`
  - eventType: "iam.user.login_failed"
  - **PATTERN REFERENCE** for UserRegistrationFailedEvent
  - Fields: usernameAttempted, userId, failureReason (LoginFailureReason), ipAddress, userAgent, deviceFingerprint, failedAt

- `TokenIssuedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenIssuedEvent.kt`
  - implements: `DomainEvent`
  - eventType: "iam.token.issued"

- `TokenRevokedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenRevokedEvent.kt`
  - implements: `DomainEvent`

- `TokenValidationFailedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenValidationFailedEvent.kt`
  - implements: `DomainEvent`

## Enums (Failure Reason pattern)

- `LoginFailureReason` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/LoginFailureReason.kt`
  - Values: INVALID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_DISABLED, CAPTCHA_REQUIRED, CAPTCHA_FAILED, PASSWORD_EXPIRED, RATE_LIMITED, MFA_REQUIRED, UNKNOWN
  - **PATTERN REFERENCE** for RegistrationFailureReason

- `ValidationFailureReason` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt`

## Event Envelope

- `EventEnvelope<T>` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/EventEnvelope.kt`
  - Generic wrapper: id (UUID), type, source, specversion, time, correlationId, schemaVersion, data

## Outbound Ports

- `EventStorePort` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventStorePort.kt`
  - Methods: append(), getNextSequenceNumber()

- `OutboxPort` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/OutboxPort.kt`
  - Methods: insert()

- `EventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Interface with publish(event: DomainEvent) method
  - Contains DomainEvent base interface

## Adapters (Infrastructure)

- `EventStorePersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/EventStorePersistenceAdapter.kt`
  - implements: `EventStorePort`
  - JPA adapter for event_store table

- `OutboxPersistenceAdapter` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/OutboxPersistenceAdapter.kt`
  - implements: `OutboxPort`
  - JPA adapter for event_outbox table

- `OutboxPoller` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`
  - @Scheduled polling — event_outbox → Kafka

- `KafkaEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - implements: `EventPublisher`
  - Publishes via KafkaTemplate

## NOT DETECTED

- Factory classes — not used in event recording flow
- Client / Gateway — no external HTTP clients in event recording scope
