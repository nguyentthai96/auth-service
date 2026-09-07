# Base Class Map

_Generated: 2026-09-07 | Services: auth-service_

## Entity Base

- `SnowflakePersistentAuditableEntity` — `com.ntt.basecore.model.id.SnowflakePersistentAuditableEntity` (external: base-core)
  - Used by: `LoginSessionEntity`, `EventOutboxEntity`, `EventStoreEntity`, `ProcessedEventEntity`, `MfaRecoveryCodeEntity`, `UserEntity`, all RBAC entities
- `VersionedAuditableEntity` — `src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt`
  - extends: `SnowflakePersistentAuditableEntity`
  - Purpose: Adds `@Version` field for optimistic locking

## Controller

- `SessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt`
  - Pattern: `@RestController` + `@RequestMapping("/api/auth/sessions")` + constructor injection
- `AdminSessionController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt`
  - Pattern: `@RestController` + `@RequestMapping("/api/admin/sessions")`
- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - Pattern: `@RestController` + `@RequestMapping("/api/cqrs/auth")`
- `TokenController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
  - Pattern: `@RestController` + `@RequestMapping("/api/token")`

## Service

- `JwtService` — `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
  - Pattern: `@Service` + constructor injection `SecurityProperties`
- `LoginSessionService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginSessionService.kt`
  - Pattern: `@Service` + constructor injection `LoginSessionRepository`, `ApplicationEventPublisher`
- `TokenBlacklistCacheService` — `src/main/kotlin/com/ntt/authservice/auth/application/TokenBlacklistCacheService.kt`
  - Pattern: `@Service` + Caffeine + Redis + DB fallback

## Handler (CQRS)

- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
  - Pattern: Command handler
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt` (inferred from GitNexus)
  - Pattern: Command handler
- `LogoutHandler` — (inferred from GitNexus process `proc_57_logout`)
  - Pattern: Command handler

## Filter

- `JwtAuthFilter` — `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`
  - extends: `OncePerRequestFilter` (Spring Security)
  - Properties: `jwtService`, `tokenBlacklistRepository`, `log`

## Scheduler

- `SessionCleanupScheduler` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt`
  - Pattern: `@Scheduled(cron)` + `@Transactional`
- `OutboxPoller` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/event/OutboxPoller.kt`
  - Pattern: `@Scheduled(fixedDelay)` + `@Transactional` + `FOR UPDATE SKIP LOCKED`

## Client / Gateway

- `NotificationGateway` (Interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/NotificationGateway.kt`
  - Implementations: `KafkaNotificationGateway`, `LoggingNotificationGateway`
- `SsoProviderClient` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/SsoProviderClient.kt`

## NOT DETECTED

- Factory pattern classes
