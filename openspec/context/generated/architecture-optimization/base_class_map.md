# Base Class Map

_Generated: 2026-08-05 | Services: auth-service, base-core_

## Entity Base Classes (base-core)

- `BaseEntity<ID>` — `components/base-core/base-model/src/main/kotlin/com/ntt/basemodel/entity/BaseEntity.kt`
  - extends: N/A (root)
  - implements: N/A
  - Notes: `@MappedSuperclass`, generic ID type

- `AuditableEntity<ID>` — `components/base-core/base-model/src/main/kotlin/com/ntt/basemodel/entity/AuditableEntity.kt`
  - extends: `BaseEntity<ID>`
  - implements: N/A
  - Notes: `@MappedSuperclass`, `createdAt`, `updatedAt`, `createdBy`, `updatedBy`

- `PersistentAuditableEntity<ID>` — `components/base-core/base-model/src/main/kotlin/com/ntt/basemodel/entity/PersistentAuditableEntity.kt`
  - extends: `AuditableEntity<ID>`
  - implements: N/A
  - Notes: `@MappedSuperclass`, soft delete (`deletedAt`)

- `SnowflakeBaseEntity` — `components/base-core/base-model/src/main/kotlin/com/ntt/basemodel/entity/SnowflakeVariants.kt`
  - extends: `BaseEntity<Long>`
  - Notes: Snowflake ID generation

- `SnowflakePersistentAuditableEntity` — `components/base-core/base-model/src/main/kotlin/com/ntt/basemodel/entity/SnowflakeVariants.kt`
  - extends: `PersistentAuditableEntity<Long>`
  - Notes: Snowflake ID + audit + soft delete

## CQRS Infrastructure (base-core — eventsourcing-utils)

- `CommandHandler<C : Command, R>` — `components/eventsourcing-utils/lib/cqrs/CommandHandler.kt`
  - interface: handle(command: C): R

- `QueryHandler<Q : Query, R>` — `components/eventsourcing-utils/lib/cqrs/QueryHandler.kt`
  - interface: handle(query: Q): R

- `CommandBus` — `components/eventsourcing-utils/lib/cqrs/CommandBus.kt`
  - Dispatches commands to handlers

- `QueryBus` — `components/eventsourcing-utils/lib/cqrs/QueryBus.kt`
  - Dispatches queries to handlers

## Controller (auth-service)

- `AuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
- `MfaController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
- `SsoController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt`
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
- `PolicyController` — `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt`
- `RbacControllers` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt`
- `RolePermissionController` — `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RolePermissionController.kt`

## Service / Application

- `AuthService` — `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` (GOD CLASS — 419 lines, 12 deps)
- `JwtService` — `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
- `MfaService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
- `OtpService` — `src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt`
- `TotpService` — `src/main/kotlin/com/ntt/authservice/auth/application/TotpService.kt`
- `PasswordPolicyService` — `src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt`
- `CaptchaVerifier` — `src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt`
- `SsoAdapter` — `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`
- `RbacEngine` — `src/main/kotlin/com/ntt/authservice/rbac/application/RbacEngine.kt` (GOD CLASS — 216 lines)
- `PolicyEvaluator` — `src/main/kotlin/com/ntt/authservice/pbac/application/PolicyEvaluator.kt`
- `AuditLogService` — `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt`

## Exception Handler (base-core)

- `BaseControllerAdvice` — `components/base-core/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt`
  - extends: N/A
  - Notes: 119 lines, handles 6 exception types, RFC 7807

## Handler

NOT DETECTED — No CQRS handlers exist yet (to be created)

## Factory

NOT DETECTED

## Client / Gateway

- `SsoAdapter` — `src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt` (inline RestTemplate)
- `CaptchaVerifier` — `src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt` (injected RestTemplate)
