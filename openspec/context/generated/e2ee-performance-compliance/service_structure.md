# Service Structure

_Generated: 2026-08-15_

## auth-service

### Detected Packages

- `auth/adapter/in/web/`: Controllers — REST API endpoints (auth, MFA, SSO, session, token, CAPTCHA, rate limit)
- `auth/adapter/in/web/dto/`: DTOs — Request/Response data transfer objects
- `auth/adapter/out/http/`: External HTTP clients — CaptchaClient, SsoProviderClient
- `auth/adapter/out/cache/`: Cache adapters — MultiTierPermissionCache (Caffeine + Redis)
- `auth/adapter/out/persistence/entity/`: JPA entities — UserEntity
- `auth/application/`: Application services — AuthService, MfaService, TotpService, OtpService, JwtService
- `auth/application/command/`: CQRS commands — LoginHandler
- `auth/application/query/`: CQRS queries — BuildAuthResponseQuery
- `auth/domain/`: Domain models
- `rbac/adapter/in/web/`: RBAC controllers — Domain, Role, Group, Resource, UserRole, RolePermission
- `rbac/adapter/out/persistence/entity/`: RBAC entities
- `rbac/application/`: RBAC services
- `pbac/adapter/in/web/`: PBAC controllers — PolicyController
- `shared/config/`: Configuration — SecurityConfig, SecurityProperties, RedisConfig, JacksonConfig, HttpClientConfig, I18nConfig, JpaAuditingConfig
- `shared/security/`: Security filters — JwtAuthFilter
- `shared/exception/`: Exception handling — AuthExceptions, AuthCoreExceptions, AuthErrorCode, GlobalExceptionHandler
- `shared/audit/`: Audit logging — AuditLogService
- `shared/i18n/`: Internationalization
- `shared/persistence/`: Shared persistence utilities

### Not Found

- `shared/encryption/` — NOT FOUND (proposed for E2EE feature)
- `shared/time/` — NOT FOUND (proposed for TimeSkewValidator)
- `handler/` — NOT FOUND (services used directly, no handler abstraction)
- `factory/` — NOT FOUND

### Naming Convention

- Controllers: `*Controller.kt` (e.g., `AuthController`, `MfaController`)
- Services: `*Service.kt` (e.g., `AuthService`, `TotpService`)
- Clients: `*Client.kt` (e.g., `CaptchaClient`, `SsoProviderClient`)
- Entities: `*Entity.kt` (e.g., `UserEntity`)
- DTOs: `*RequestDto.kt`, `*Response.kt`, `*Request.kt`
- Exceptions: `*Exception.kt` extending `AuthException`
- Error Codes: `AuthErrorCode` enum with format `AUTH_XXX`
- Config: `*Config.kt`, `*Properties.kt`
- Filters: `*Filter.kt`
- Cache: `*Cache.kt`

### Architecture Pattern

- **Clean Architecture**: `adapter/in/web/` (controllers) → `application/` (services) → `domain/` (models)
- **CQRS**: `application/command/` + `application/query/` in auth module
- **Hexagonal Ports**: `adapter/out/http/` (HTTP clients), `adapter/out/cache/` (cache), `adapter/out/persistence/` (JPA)
