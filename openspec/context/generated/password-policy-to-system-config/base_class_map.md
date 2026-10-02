# Base Class Map

_Generated: 2026-10-02 | Services: auth-service, system-admin-service_

## Controller

- `BaseController` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/BaseController.kt`

## Handler

- `LoginHandler` — `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
- `RegisterHandler` — `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`

## Factory / Entity

- `SnowflakeBaseEntity` — `services/auth-service/src/main/kotlin/com/ntt/basecore/model/id/SnowflakeBaseEntity.kt` (used by `PasswordPolicyEntity`, `PasswordHistoryEntity`)
- `SnowflakePersistentAuditableEntity` — `services/system-admin-service/src/main/kotlin/com/ntt/sysadmin/versioning/domain/entity/SystemConfigEntity.kt`

## Client / Gateway

- `KafkaTemplate` — Spring Kafka bean (`KafkaEventPublisher.kt`)
- `StringRedisTemplate` — Spring Data Redis bean (used in cache adapters)

## NOT DETECTED

- BaseCrudDataFactory (N/A — microservice Spring Boot architecture, not Netty MID pattern)
- BaseClientDataFactory (N/A)
