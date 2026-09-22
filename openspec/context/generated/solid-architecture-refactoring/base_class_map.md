# Base Class Map

_Generated: 2026-09-22 | Services: auth-service_

## Controller

- `BaseController` — `shared/web/BaseController.kt`
  - extends: N/A (abstract class)
  - provides: `okResponse()`, `createdResponse()`, `okMessageResponse()`, `requestContext`, `message()`

## Handler (CQRS Command Handlers)

- `CommandHandler<C, R>` — from `eventsourcing-utils` library (external)
  - implements by: `LoginHandler`, `RegisterHandler`, `RefreshTokenHandler`, `SwitchDomainHandler`, `RevokeSessionsHandler`, `UnlockUserHandler`, `SelfServiceUnlockHandler`, `AnonymousSessionHandler`, `RenewAnonymousTokenHandler`

## Query Handler

- `BuildAuthResponseHandler` — `auth/application/query/BuildAuthResponseHandler.kt`

## Port Interfaces

- `UserPort` — `auth/application/port/out/UserPort.kt`
- `EventStorePort` — `auth/application/port/out/EventStorePort.kt`
- `DomainPort` — `auth/application/port/out/DomainPort.kt`
- `OutboxPort` — `auth/application/port/out/OutboxPort.kt`

## Strategy

- `CaptchaStrategy` — `auth/application/CaptchaStrategyRegistry.kt` (interface inside registry file)

## NOT DETECTED

- Factory (no factory pattern classes found)
- Gateway (adapter/out/gateway/ exists but no base gateway class)
