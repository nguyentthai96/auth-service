# Base Class Map — auth-core-features

> Generated: 2026-08-26
> Scope: `auth-service` (primary candidate service)
> Source: Code scan — grep + file system analysis

---

## 1. Entity Base Classes

| Class | Extends | Path | Notes |
|-------|---------|------|-------|
| `UserEntity` | `SnowflakePersistentAuditableEntity` | `rbac/adapter/out/persistence/entity/UserEntity.kt` | Primary auth entity — MFA fields, trusted device, password |
| `UserIdentityEntity` | `SnowflakeBaseEntity` | `rbac/adapter/out/persistence/entity/UserIdentityEntity.kt` | SSO identity linking (provider + sub claim) |
| `PasswordPolicyEntity` | `SnowflakeBaseEntity` | `rbac/adapter/out/persistence/entity/PasswordPolicyEntity.kt` | Domain-scoped password policy (Passay rules) |
| `PasswordHistoryEntity` | `SnowflakeBaseEntity` | `rbac/adapter/out/persistence/entity/PasswordHistoryEntity.kt` | Password history for reuse prevention |
| `LoginSessionEntity` | `SnowflakePersistentAuditableEntity` | `auth/adapter/out/persistence/entity/LoginSessionEntity.kt` | Login session tracking |
| `CipherKeySessionEntity` | `SnowflakePersistentAuditableEntity` | `auth/adapter/out/persistence/entity/CipherKeySessionEntity.kt` | E2EE key session |
| `AccountDeletionRequestEntity` | `SnowflakePersistentAuditableEntity` | `auth/adapter/out/persistence/entity/AccountDeletionRequestEntity.kt` | Account lifecycle |
| `AccountDataExportEntity` | `SnowflakePersistentAuditableEntity` | `auth/adapter/out/persistence/entity/AccountDataExportEntity.kt` | GDPR data export |
| `VaultAccessLogEntity` | `SnowflakePersistentAuditableEntity` | `auth/adapter/out/persistence/entity/VaultAccessLogEntity.kt` | E2EE vault access audit |
| `EventStoreEntity` | `SnowflakePersistentAuditableEntity` | `auth/adapter/out/persistence/entity/EventStoreEntity.kt` | [NEW] Append-only event store |
| `EventOutboxEntity` | `SnowflakePersistentAuditableEntity` | `auth/adapter/out/persistence/entity/EventOutboxEntity.kt` | [NEW] Transactional outbox for Kafka |
| `ProcessedEventEntity` | `SnowflakePersistentAuditableEntity` | `auth/adapter/out/persistence/entity/ProcessedEventEntity.kt` | [NEW] Consumer-side idempotency |
| `MfaRecoveryCodeEntity` | `SnowflakePersistentAuditableEntity` | `auth/adapter/out/persistence/entity/MfaRecoveryCodeEntity.kt` | [NEW] MFA backup recovery codes |
| `AuditLogEntity` | `SnowflakePersistentAuditableEntity` | `shared/audit/AuditLogEntity.kt` | [NEW] Audit log entry |
| `PermissionEntities` (grouped) | Various base classes | `rbac/adapter/out/persistence/entity/PermissionEntities.kt` | Permission, Action, DomainResource entities |
| `RbacEntities` (grouped) | Various base classes | `rbac/adapter/out/persistence/entity/RbacEntities.kt` | Domain, UserDomain, Group, UserGroup, DomainRole, GroupRole, RolePermission entities |

## 2. Application Service Base Patterns

| Pattern | Class | Path | Notes |
|---------|-------|------|-------|
| `@Service` | `AuthService` | `auth/application/AuthService.kt` | Core auth orchestrator |
| `@Service` | `MfaService` | `auth/application/MfaService.kt` | MFA management |
| `@Service` | `TotpService` | `auth/application/TotpService.kt` | TOTP via dev.samstevens.totp |
| `@Service` | `OtpService` | `auth/application/OtpService.kt` | OTP via Redis |
| `@Service` | `SsoAdapter` | `auth/application/SsoAdapter.kt` | SSO orchestration |
| `@Service` | `PasswordPolicyService` | `auth/application/PasswordPolicyService.kt` | Passay password validation |
| `@Service` | `JwtService` | `auth/application/JwtService.kt` | JWT RS256 + HMAC signing |
| `@Service` | `LoginSessionService` | `auth/application/LoginSessionService.kt` | Login session management |
| `@Service` | `SessionPolicyService` | `auth/application/SessionPolicyService.kt` | Session policy enforcement |
| `@Service` | `AccountLifecycleService` | `auth/application/AccountLifecycleService.kt` | Account deactivation/deletion |
| `@Service` | `ServiceTokenService` | `auth/application/ServiceTokenService.kt` | Service-to-service auth |
| `@Service` | `DomainLookupService` | `auth/application/DomainLookupService.kt` | Domain resolution |
| `@Component` | `EventService` | `auth/application/event/EventService.kt` | [NEW] Event store + outbox recording |
| `@Component` | `TokenEventRecorder` | `auth/application/event/TokenEventRecorder.kt` | [NEW] Token lifecycle event helper |
| `@Component` | `SessionCleanupScheduler` | `auth/application/SessionCleanupScheduler.kt` | Scheduled session cleanup |

## 3. CQRS Command/Handler Pattern

| Command | Handler | Path |
|---------|---------|------|
| `LoginCommand` | `LoginHandler` | `auth/application/command/LoginHandler.kt` |
| `RegisterCommand` | `RegisterHandler` | `auth/application/command/RegisterHandler.kt` |
| `RefreshTokenCommand` | `RefreshTokenHandler` | `auth/application/command/RefreshTokenHandler.kt` |
| `SwitchDomainCommand` | `SwitchDomainHandler` | `auth/application/command/SwitchDomainHandler.kt` |
| `RevokeSessionsCommand` | `RevokeSessionsHandler` | `auth/application/command/RevokeSessionsHandler.kt` |
| `CreateAnonymousSessionCommand` | `AnonymousSessionHandler` | `auth/application/command/AnonymousSessionHandler.kt` |
| `RenewAnonymousTokenCommand` | `RenewAnonymousTokenHandler` | `auth/application/command/RenewAnonymousTokenHandler.kt` |
| — | `TokenGenerator` | `auth/application/command/TokenGenerator.kt` |

## 4. Hexagonal Ports (Outbound)

| Port Interface | Path | Adapters |
|---------------|------|----------|
| `UserPort` | `auth/application/port/out/UserPort.kt` | `UserPersistenceAdapter` |
| `TokenStore` | `auth/application/port/out/TokenStore.kt` | `TokenStorePersistenceAdapter` |
| `DomainPort` | `auth/application/port/out/DomainPort.kt` | `DomainPersistenceAdapter` |
| `EventPublisher` | `auth/application/port/out/EventPublisher.kt` | `KafkaEventPublisher`, `SpringEventPublisher` |
| `CaptchaGateway` | `auth/application/port/out/CaptchaGateway.kt` | `CaptchaGatewayAdapter` |
| `SsoGateway` | `auth/application/port/out/SsoGateway.kt` | `HttpSsoGateway` |
| `EventStorePort` | `auth/application/port/out/EventStorePort.kt` | `EventStorePersistenceAdapter` |
| `OutboxPort` | `auth/application/port/out/OutboxPort.kt` | `OutboxPersistenceAdapter` |

## 5. Controller Base Pattern

All controllers use `@RestController` + `@RequestMapping` pattern. No custom base controller class detected — each controller is standalone.

| Controller | Path | API Prefix |
|-----------|------|------------|
| `AuthController` | `auth/adapter/in/web/AuthController.kt` | `/api/auth` |
| `CqrsAuthController` | `auth/adapter/in/web/CqrsAuthController.kt` | `/api/v2/auth` |
| `MfaController` | `auth/adapter/in/web/MfaController.kt` | `/api/auth/mfa` |
| `SsoController` | `auth/adapter/in/web/SsoController.kt` | `/api/auth/sso` |
| `TokenController` | `auth/adapter/in/web/TokenController.kt` | `/api/auth` |
| `CaptchaController` | `auth/adapter/in/web/CaptchaController.kt` | `/api/auth/captcha` |
| `AdminSessionController` | `auth/adapter/in/web/AdminSessionController.kt` | `/api/admin/sessions` |
| `SessionController` | `auth/adapter/in/web/SessionController.kt` | `/api/auth/sessions` |
| `AccountLifecycleController` | `auth/adapter/in/web/AccountLifecycleController.kt` | `/api/auth/account` |
| `KeyExchangeController` | `auth/adapter/in/web/KeyExchangeController.kt` | `/api/auth/keys` |
| `RateLimitAdminController` | `auth/adapter/in/web/RateLimitAdminController.kt` | `/api/admin/rate-limits` |
| `AnonymousAuthController` | `auth/adapter/in/web/AnonymousAuthController.kt` | `/api/auth/anonymous` |
| `InternalApiController` | `auth/adapter/in/web/InternalApiController.kt` | `/internal/api` |
| `EventStoreController` | `auth/adapter/in/web/EventStoreController.kt` | `/api/events` |

## 6. Domain Model

| Class | Type | Path |
|-------|------|------|
| `User` | Domain entity | `auth/domain/model/User.kt` |
| `AuthToken` | Value object | `auth/domain/model/AuthToken.kt` |
| `UserStatus` | Sealed interface | `auth/domain/model/UserStatus.kt` |
| `TokenIssuanceMetadata` | Value object | `auth/domain/model/TokenIssuanceMetadata.kt` |
| `DomainCode` | Value object | `auth/domain/model/vo/` |
| `Email` | Value object | `auth/domain/model/vo/` |
| `PasswordHash` | Value object | `auth/domain/model/vo/` |
| `UserId` | Value object | `auth/domain/model/vo/` |
| `TokenHasher` | Domain service | `auth/domain/service/TokenHasher.kt` |

## 7. Domain Events

| Event | Path | Notes |
|-------|------|-------|
| `TokenIssuedEvent` | `auth/domain/event/TokenIssuedEvent.kt` | [NEW] Token lifecycle |
| `TokenRevokedEvent` | `auth/domain/event/TokenRevokedEvent.kt` | [NEW] Token revocation |
| `UserRegisteredEvent` | `auth/domain/event/UserRegisteredEvent.kt` | [NEW] User registration |
| `EventEnvelope` | `auth/domain/event/EventEnvelope.kt` | [NEW] CloudEvents-inspired envelope |
| `IssuanceContext` | `auth/domain/event/IssuanceContext.kt` | [NEW] Token issuance context enum |
| `RevocationType` | `auth/domain/event/RevocationType.kt` | [NEW] Token revocation type enum |
