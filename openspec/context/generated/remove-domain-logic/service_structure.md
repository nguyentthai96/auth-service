# Service Structure

_Generated: 2026-10-02_

## auth-service

### Detected Packages

- `auth.adapter.in.web` — Controllers (CqrsAuthController, TokenController, etc.)
- `auth.adapter.in.web.dto` — Request/Response DTOs
- `auth.adapter.in.web.filter` — Servlet filters (ContentLanguageFilter)
- `auth.adapter.in.kafka` — Kafka consumers (AccountStatusConsumer, PermissionChangedConsumer)
- `auth.adapter.out.persistence` — JPA adapters (DomainPersistenceAdapter)
- `auth.adapter.out.event` — Event publishers (KafkaEventPublisher, OutboxPoller)
- `auth.adapter.out.notification` — Notification gateway (KafkaNotificationGateway)
- `auth.adapter.out.cache` — Redis adapters (RedisLockoutAdapter)
- `auth.adapter.out.cipher` — Encryption adapters (RedisCipherKeySessionResolver)
- `auth.application` — Application services (PasswordPolicyService, DomainLookupService, MfaService, etc.)
- `auth.application.command` — CQRS command handlers (RegisterHandler, LoginHandler, TokenGenerator, SwitchDomainHandler)
- `auth.application.port.out` — Output port interfaces (DomainPort, UserPort, etc.)
- `auth.application.pipeline` — Authentication pipeline steps (PolicyEnforcementStep)
- `auth.domain.event` — Domain events (TokenIssuedEvent, UserRegisteredEvent, etc.)
- `auth.domain.model` — Domain models (AuthToken)
- `rbac.adapter.in.web` — RBAC controllers (RbacControllers, RolePermissionController)
- `rbac.adapter.out.persistence.entity` — JPA entities (RbacEntities, PermissionEntities, PasswordPolicyEntity)
- `rbac.adapter.out.persistence.repository` — JPA repositories (Repositories)
- `rbac.application` — RBAC engine (RbacEngine)
- `pbac.adapter.in.web` — Policy controllers (PolicyController)
- `shared.web` — Base controllers (BaseController, AdminController, AuthenticatedController)
- `shared.exception` — Exception hierarchy (AuthExceptions, CipherExceptions, GlobalExceptionHandler)
- `shared.config` — Configuration (SecurityConfig, RedisConfig, I18nConfig)
- `shared.audit` — Audit logging (AuditLogService)
- `shared.security` — Security rules (SecurityRuleController)

### Not Found

- `factory/` — NOT FOUND (uses Service pattern)
- `client/` — NOT FOUND (uses Gateway pattern)

### Naming Convention

- Controllers: `*Controller` (e.g., `CqrsAuthController`, `PolicyController`)
- Handlers: `*Handler` (e.g., `SwitchDomainHandler`, `RegisterHandler`)
- Services: `*Service` (e.g., `PasswordPolicyService`, `DomainLookupService`)
- Entities: `*Entity` (e.g., `DomainEntity`, `GroupEntity`)
- Repositories: `*Repository` (e.g., `DomainRepository`, `UserRepository`)
- DTOs: `*RequestDto` / `*Response` (e.g., `LoginRequestDto`, `AuthResponse`)
- Events: `*Event` (e.g., `UserRegisteredEvent`, `TokenIssuedEvent`)
- Ports: `*Port` (e.g., `DomainPort`, `UserPort`)
