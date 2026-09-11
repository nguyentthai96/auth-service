# Base Class Map

_Generated: 2026-09-11 | Services: auth-service (source), account-service (reference)_

## Controller

- `AnonymousAuthController` — `auth/adapter/in/web/AnonymousAuthController.kt` — @RestController
- `MfaController` — `auth/adapter/in/web/MfaController.kt` — @RestController
- `TokenController` — `auth/adapter/in/web/TokenController.kt` — @RestController
- `AccountLifecycleController` — `auth/adapter/in/web/AccountLifecycleController.kt` — @RestController
- `CaptchaController` — `auth/adapter/in/web/CaptchaController.kt` — @RestController
- `PolicyController` — `pbac/adapter/in/web/PolicyController.kt` — @RestController
- `RolePermissionController` — `rbac/adapter/in/web/RolePermissionController.kt` — @RestController
- `RbacControllers` — `rbac/adapter/in/web/RbacControllers.kt` — @RestController (multiple controllers in single file)

## Handler

- `NewDeviceMailHandler` — `auth/application/NewDeviceMailHandler.kt` — @Component, @TransactionalEventListener
- `CheckPermissionHandler` — `rbac/application/query/CheckPermissionHandler.kt` — @Component
- `GetUserRolesHandler` — `rbac/application/query/GetUserRolesHandler.kt` — @Component
- `GetPermissionsHandler` — `rbac/application/query/GetPermissionsHandler.kt` — @Component
- `AnonymousSessionHandler` — `auth/application/command/AnonymousSessionHandler.kt` — @Component
- `RenewAnonymousTokenHandler` — `auth/application/command/RenewAnonymousTokenHandler.kt` — @Component

## Service

- `MailQueueService` — `auth/application/MailQueueService.kt` — @Service (enqueue mail, render template)
- `MailJobScheduler` — `auth/application/MailJobScheduler.kt` — @Component (poll + send SMTP)
- `RbacEngine` — `rbac/application/RbacEngine.kt` — @Service
- `PolicyEvaluator` — `pbac/application/PolicyEvaluator.kt` — @Service

## Persistence Adapter

- `DomainPersistenceAdapter` — `auth/adapter/out/persistence/DomainPersistenceAdapter.kt` — @Component
- `UserPersistenceAdapter` — `auth/adapter/out/persistence/UserPersistenceAdapter.kt` — @Component
- `OutboxPersistenceAdapter` — `auth/adapter/out/persistence/OutboxPersistenceAdapter.kt` — @Component
- `EventStorePersistenceAdapter` — `auth/adapter/out/persistence/EventStorePersistenceAdapter.kt` — @Component
- `TokenStorePersistenceAdapter` — `auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt` — @Component

## Client / Gateway

- `CaptchaGatewayAdapter` — `auth/adapter/out/gateway/CaptchaGatewayAdapter.kt` — @Component
- `CaptchaClient` — `auth/adapter/out/http/CaptchaClient.kt` — HTTP interface
- `KafkaNotificationGateway` — `auth/adapter/out/notification/KafkaNotificationGateway.kt` — @Component
- `LoggingNotificationGateway` — `auth/adapter/out/notification/LoggingNotificationGateway.kt` — @Component
- `KafkaEventPublisher` — `auth/adapter/out/event/KafkaEventPublisher.kt` — @Component
- `SpringEventPublisher` — `auth/adapter/out/event/SpringEventPublisher.kt` — @Component
- `OutboxPoller` — `auth/adapter/out/event/OutboxPoller.kt` — @Component (@Scheduled)
- `OAuth2TokenExchanger` — `auth/adapter/out/sso/OAuth2TokenExchanger.kt` — SSO integration

## Exception Handler

- `GlobalExceptionHandler` — `shared/exception/GlobalExceptionHandler.kt` — extends BaseControllerAdvice (base-core)

## Factory

NOT DETECTED

## NOT DETECTED

- Factory classes
