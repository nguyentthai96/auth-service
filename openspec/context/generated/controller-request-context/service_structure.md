# Service Structure

_Generated: 2026-09-21_

## auth-service

### Detected Packages

- `auth/adapter/in/web/` — 16 controller classes (REST endpoints)
- `auth/adapter/in/web/dto/` — 7 DTO files (RequestDtos, AuthResponse, DeviceDtos, MfaDtos, SsoDtos, TokenDtos, AnonymousDtos)
- `auth/adapter/in/web/filter/` — 4 servlet filters (ClientMetadata, ServiceAuth, ContentLanguage, LoginRateLimit)
- `auth/adapter/in/kafka/` — 1 Kafka consumer (AccountStatusConsumer)
- `auth/adapter/out/` — outbound adapters (NotificationGatewayAdapter, repository implementations)
- `auth/application/` — 38 service classes
- `auth/application/command/` — command handlers (CQRS write side)
- `auth/application/query/` — query handlers (CQRS read side)
- `auth/application/event/` — domain event handlers
- `auth/application/port/` — port interfaces (hexagonal architecture)
- `auth/application/cipher/` — encryption/decryption services
- `auth/domain/` — domain entities and value objects
- `rbac/adapter/in/web/` — 5 controllers in 1 file (RbacControllers.kt)
- `rbac/application/` — RBAC service and handler classes
- `pbac/adapter/in/web/` — 1 controller (PolicyController)
- `shared/exception/` — exception hierarchy + GlobalExceptionHandler
- `shared/filter/` — shared filters (IdempotencyFilter)
- `shared/security/` — security config + JwtAuthFilter
- `shared/config/` — application configuration
- `shared/i18n/` — internationalization (I18nMessageRepository, I18nMessageEntity)
- `shared/cache/` — cache configuration
- `shared/audit/` — audit logging (AuditLogService — uses RequestContext)
- `shared/persistence/` — shared persistence utils

### Not Found

- `shared/web/` — Đây là package target cho BaseController, RequestContext (SẼ TẠO MỚI)

### Naming Convention

- Controllers: `<Feature>Controller` (e.g., `SessionController`, `MfaController`)
- Services: `<Feature>Service` (e.g., `LoginSessionService`, `MfaService`)
- Handlers: `<Action>Handler` (e.g., `LoginHandler`, `RegisterHandler`)
- DTOs: `<Feature>Dtos.kt` (grouped per feature) or `<Entity><Action>RequestDto`
- Filters: `<Feature>Filter` (e.g., `ClientMetadataFilter`)
- Exceptions: `<Feature>Exceptions.kt` (grouped) or `<Domain>Exception`
- Error codes: `AuthErrorCode` enum
