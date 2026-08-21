# Service Structure — auth-core-features

> Generated: 2026-08-26
> Scope: `auth-service` (primary candidate service)
> Source: Code scan — directory listing + grep analysis

---

## 1. Package Structure

### auth/ — Core authentication module
```
auth/
├── adapter/
│   ├── in/
│   │   ├── kafka/
│   │   │   └── PermissionChangedConsumer.kt
│   │   └── web/
│   │       ├── AccountLifecycleController.kt
│   │       ├── AdminSessionController.kt
│   │       ├── AnonymousAuthController.kt
│   │       ├── AuthController.kt
│   │       ├── CaptchaController.kt
│   │       ├── CqrsAuthController.kt
│   │       ├── EventStoreController.kt          [NEW]
│   │       ├── InternalApiController.kt
│   │       ├── KeyExchangeController.kt
│   │       ├── MfaController.kt
│   │       ├── RateLimitAdminController.kt
│   │       ├── SessionController.kt
│   │       ├── SsoController.kt
│   │       ├── TokenController.kt
│   │       ├── dto/
│   │       │   ├── AnonymousDtos.kt
│   │       │   ├── AuthResponse.kt
│   │       │   ├── MfaDtos.kt
│   │       │   ├── RequestDtos.kt
│   │       │   ├── SsoDtos.kt
│   │       │   └── TokenDtos.kt
│   │       └── filter/
│   │           ├── ClientMetadataFilter.kt
│   │           ├── ContentLanguageFilter.kt
│   │           ├── LoginRateLimitFilter.kt
│   │           └── ServiceAuthFilter.kt
│   └── out/
│       ├── cache/                               (empty)
│       ├── cipher/
│       │   ├── HmacFieldSigningServiceImpl.kt
│       │   ├── InMemoryCipherKeySessionRepository.kt  [NEW]
│       │   ├── RedisAntiReplayValidator.kt
│       │   ├── RedisCipherKeySessionResolver.kt
│       │   └── TinkCipherAlgorithmFactory.kt
│       ├── event/
│       │   ├── KafkaEventPublisher.kt
│       │   ├── OutboxPoller.kt                  [NEW]
│       │   └── SpringEventPublisher.kt
│       ├── gateway/
│       │   └── CaptchaGatewayAdapter.kt
│       ├── http/
│       │   ├── CaptchaClient.kt
│       │   ├── HttpCaptchaGateway.kt
│       │   ├── HttpSsoGateway.kt
│       │   └── SsoProviderClient.kt
│       ├── persistence/
│       │   ├── DomainPersistenceAdapter.kt
│       │   ├── EventStorePersistenceAdapter.kt  [NEW]
│       │   ├── OutboxPersistenceAdapter.kt      [NEW]
│       │   ├── TokenStorePersistenceAdapter.kt
│       │   ├── UserPersistenceAdapter.kt
│       │   ├── entity/
│       │   │   ├── AccountDataExportEntity.kt
│       │   │   ├── AccountDeletionRequestEntity.kt
│       │   │   ├── CipherKeySessionEntity.kt
│       │   │   ├── EventOutboxEntity.kt         [NEW]
│       │   │   ├── EventStoreEntity.kt          [NEW]
│       │   │   ├── LoginSessionEntity.kt
│       │   │   ├── MfaRecoveryCodeEntity.kt     [NEW]
│       │   │   ├── ProcessedEventEntity.kt      [NEW]
│       │   │   └── VaultAccessLogEntity.kt
│       │   ├── mapper/
│       │   │   └── UserEntityMapper.kt
│       │   └── repository/
│       │       ├── AccountDataExportRepository.kt
│       │       ├── AccountDeletionRequestRepository.kt
│       │       ├── CipherKeySessionJpaRepository.kt
│       │       ├── EventOutboxJpaRepository.kt  [NEW]
│       │       ├── EventStortorePort.kt                [NEW]
│   │       ├── OutboxPort.kt                    [NEW]
│   │       ├── SsoGateway.kt
│   │       ├── TokenStore.kt
│   │       └── UserPort.kt
│   └── query/
│       └── BuildAuthResponseQuery.kt (+ Handler)
└── domain/
    ├── event/
    │   ├── EventEnvelope.kt                     [NEW]
    │   ├── IssuanceContext.kt                   [NEW]
    │   ├── RevocationType.kt                    [NEW]
    │   ├── TokenIssuedEvent.kt                  [NEW]
    │   ├── TokenRevokedEvent.kt                 [NEW]
    │   └── UserRegisteredEvent.kt               [NEW]
    ├── model/
    │   ├── AuthToken.kt
    │   ├── TokenIssuanceMetadata.kt             [NEW]
    │   ├── User.kt
    │   ├── UserStatus.kt
    │   └── vo/ (DomainCode, Email, PasswordHash, UserId)
    └── service/
        └── TokenHasher.kt
```

### rbac/ — Role-Based Access Control
```
rbac/
├── adapter/
│   ├── in/
│   │   └── web/
│   │       ├── RbacControllers.kt (5 @RestController: DomainController, GroupController, UserController, UserDomainController, RolePermissionManagementController)
│   │       └── RolePermissionController.kt
│   └── out/
│       └── persistence/
│           ├── entity/
│           │   ├── PasswordHistoryEntity.kt
│           │   ├── PasswordPolicyEntity.kt
│           │   ├── PermissionEntities.kt
│           │   ├── RbacEntities.kt
│           │   ├── UserEntity.kt
│           │   └── UserIdentityEntity.kt
│           ├── mapper/
│           │   └── UserEntityMapper.kt
│           └── repository/
│               └── Repositories.kt (16 JpaRepository interfaces)
└── application/ (not listed — services may be in rbac module)
```

### shared/ — Cross-cutting concerns
```
shared/
├── audit/
│   ├── AuditLogEntity.kt                        [NEW]
│   ├── AuditLogRepository.kt                    [NEW]
│   └── AuditLogService.kt
├── cache/                                        (empty)
├── config/
│   ├── HttpClientConfig.kt
│   ├── I18nConfig.kt
│   ├── JacksonConfig.kt
│   ├── JpaAuditingConfig.kt
│   ├── KafkaConfig.kt
│   ├── ObservabilityConfig.kt                    [NEW]
│   ├── OutboxProperties.kt                       [NEW]
│   ├── RedisConfig.kt
│   ├── RedisLuaScriptConfig.kt                   [NEW]
│   ├── SecurityConfig.kt
│   └── SecurityProperties.kt
├── exception/
│   ├── AnonymousExceptions.kt
│   ├── AuthCoreExceptions.kt
│   ├── AuthErrorCode.kt (52 error codes)
│   ├── AuthExceptions.kt
│   ├── CipherExceptions.kt
│   └── GlobalExceptionHandler.kt
├── filter/
│   └── IdempotencyFilter.kt
├── i18n/
│   ├── DatabaseMessageSource.kt
│   ├── I18nMessageEntity.kt
│   └── I18nMessageRepository.kt
├── persistence/
│   └── VersionedAuditableEntity.kt
└── security/
    └── JwtAuthFilter.kt
```

### pbac/ — Policy-Based Access Control
```
pbac/
├── adapter/
│   └── in/
│       └── web/
│           └── PolicyController.kt
└── application/ (policy evaluation services)
```

## 2. Layer Summary

| Layer | Package Pattern | Count | Status |
|-------|----------------|-------|--------|
| Controller (Inbound) | `adapter/in/web/` | 14 auth + 6 rbac + 1 pbac = 21 | FOUND |
| Kafka Consumer (Inbound) | `adapter/in/kafka/` | 1 | FOUND |
| Handler/Command | `application/command/` | 8 command + 8 handler = 16 | FOUND |
| Query | `application/query/` | 2 | FOUND |
| Application Service | `application/` | 24+ | FOUND |
| Port (Outbound) | `application/port/out/` | 8 interfaces | FOUND |
| Persistence Adapter | `adapter/out/persistence/` | 5 adapters | FOUND |
| HTTP Adapter | `adapter/out/http/` | 4 classes | FOUND |
| Event Adapter | `adapter/out/event/` | 3 classes | FOUND |
| Cipher Adapter | `adapter/out/cipher/` | 5 classes | FOUND |
| Gateway Adapter | `adapter/out/gateway/` | 1 class | FOUND |
| SSO Adapter | `adapter/out/sso/` | 1 class | FOUND |
| Entity | `adapter/out/persistence/entity/` | 9 auth + 6 rbac + 1 audit = 16 files | FOUND |
| Repository | `adapter/out/persistence/repository/` | 9 auth + 16 rbac + 1 audit = 26 | FOUND |
| Domain Model | `domain/model/` | 5 + vo/ | FOUND |
| Domain Event | `domain/event/` | 6 | FOUND |
| Domain Service | `domain/service/` | 1 | FOUND |
| DTO | `adapter/in/web/dto/` | 6 files | FOUND |
| Filter | `adapter/in/web/filter/` + `shared/filter/` | 5 | FOUND |
| Config | `shared/config/` | 11 files | FOUND |
| Exception | `shared/exception/` | 6 files | FOUND |
| Audit | `shared/audit/` | 3 files | FOUND |
| i18n | `shared/i18n/` | 3 files | FOUND |

## 3. Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Entity | `{Name}Entity.kt` | `UserEntity.kt`, `LoginSessionEntity.kt` |
| Repository | `{Name}Repository.kt` or `{Name}JpaRepository.kt` | `LoginSessionRepository.kt`, `EventStoreJpaRepository.kt` |
| Controller | `{Name}Controller.kt` | `AuthController.kt`, `MfaController.kt` |
| Service | `{Name}Service.kt` | `AuthService.kt`, `MfaService.kt` |
| Command | `{Name}Command.kt` | `LoginCommand.kt`, `RegisterCommand.kt` |
| Handler | `{Name}Handler.kt` | `LoginHandler.kt`, `RegisterHandler.kt` |
| Port | `{Name}Port.kt` or `{Name}.kt` (interface name) | `UserPort.kt`, `TokenStore.kt` |
| Adapter | `{Name}Adapter.kt` or `{Protocol}{Name}.kt` | `UserPersistenceAdapter.kt`, `HttpSsoGateway.kt` |
| DTO | `{Name}Dtos.kt` (grouped) or `{Name}Response.kt` | `MfaDtos.kt`, `AuthResponse.kt` |
| Filter | `{Name}Filter.kt` | `LoginRateLimitFilter.kt`, `IdempotencyFilter.kt` |
| Config | `{Name}Config.kt` or `{Name}Properties.kt` | `SecurityConfig.kt`, `SecurityProperties.kt` |
| Exception | `{Module}Exceptions.kt` | `AuthExceptions.kt`, `AuthCoreExceptions.kt` |
| Event | `{Name}Event.kt` | `TokenIssuedEvent.kt`, `NewDeviceLoginEvent.kt` |

## 4. Flyway Migrations

| Version | File | Content |
|---------|------|---------|
| V1 | `V1__init_auth_rbac_pbac.sql` | Base auth/rbac/pbac tables |
| V2 | `V2__auth_core_features.sql` | Auth core (MFA, SSO, token management) |
| V3 | `V3__add_version_column.sql` | Optimistic locking |
| V4 | `V4__create_login_sessions.sql` | Login sessions |
| V5 | `V5__create_i18n_messages.sql` | i18n messages table |
| V6 | `V6__seed_i18n_messages.sql` | i18n seed data |
| V7 | `V7__create_cipher_key_session.sql` | E2EE key sessions |
| V8 | `V8__create_e2ee_audit_tables.sql` | E2EE audit |
| V9 | `V9__account_lifecycle.sql` | Account lifecycle |
| V10 | `V10__trusted_device_ttl.sql` | Trusted device TTL |
| V11 | `V11__event_sourcing_tables.sql` | Event store + outbox + processed events |
| V12 | `V12__seed_event_error_messages.sql` | Event error i18n |
| V13 | `V13__mfa_recovery_codes.sql` | MFA recovery codes |
| V14 | `V14__audit_logs.sql` | Audit logs table |
| V15 | `V15__domain_branding.sql` | Domain branding fields |
