# Base Class Map

_Generated: 2026-10-02 | Services: auth-service_

## Controller

- `BaseController` — `src/main/kotlin/com/ntt/authservice/shared/web/BaseController.kt`
  - extends: N/A (root abstract class)
  - provides: response helpers
- `AuthenticatedController` — `src/main/kotlin/com/ntt/authservice/shared/web/AuthenticatedController.kt`
  - extends: `BaseController`
  - provides: authenticated user context
- `AdminController` — `src/main/kotlin/com/ntt/authservice/shared/web/AdminController.kt`
  - extends: `AuthenticatedController`
  - provides: requireAdmin() guard

## Handler

- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt`
  - implements: CommandHandler (CQRS pattern)
  - **TARGET: DELETE**
- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
  - implements: CommandHandler
  - **TARGET: MODIFY (remove domainPort)**
- `TokenGenerator` — `src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt`
  - type: Service (generates JWT tokens)
  - **TARGET: MODIFY (remove domain claims)**
- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
  - implements: CommandHandler
  - **TARGET: MODIFY (remove domainCode from events)**

## Factory

NOT DETECTED — project uses Service pattern, not Factory

## Client / Gateway

- `KafkaNotificationGateway` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/notification/KafkaNotificationGateway.kt`
  - type: Kafka producer for notification events
- `KafkaEventPublisher` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/KafkaEventPublisher.kt`
  - type: Kafka event publisher (outbox pattern)

## Exception Handler

- `GlobalExceptionHandler` — `src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt`
  - extends: `BaseControllerAdvice` (base-core)
  - pattern: Bridge `AuthException` → `BusinessException`
