# Service Structure

_Generated: 2026-08-05_

## auth-service

### Detected Packages

- `com.ntt.authservice`: Root package (Application entry point)
- `com.ntt.authservice.auth`: Authentication bounded context
  - `auth.adapter.in.web`: REST controllers (AuthController, MfaController, SsoController, TokenController)
  - `auth.adapter.in.web.dto`: Web DTOs (MfaDtos, SsoDtos, TokenDtos)
  - `auth.application`: Application services (AuthService, JwtService, MfaService, OtpService, etc.)
- `com.ntt.authservice.rbac`: RBAC bounded context
  - `rbac.adapter.in.web`: REST controllers (RbacControllers, RolePermissionController)
  - `rbac.adapter.out.persistence.entity`: JPA entities (UserEntity, RbacEntities, PermissionEntities, etc.)
  - `rbac.adapter.out.persistence.repository`: Spring Data repositories (Repositories.kt)
  - `rbac.application`: Application services (RbacEngine)
- `com.ntt.authservice.pbac`: PBAC bounded context
  - `pbac.adapter.in.web`: REST controllers (PolicyController)
  - `pbac.adapter.out.persistence.entity`: JPA entities (PolicyEntities)
  - `pbac.adapter.out.persistence.repository`: Spring Data repositories (PolicyRepository)
  - `pbac.application`: Application services (PolicyEvaluator)
- `com.ntt.authservice.shared`: Cross-cutting concerns
  - `shared.audit`: Audit logging (AuditLogService)
  - `shared.config`: Spring configuration (SecurityConfig, SecurityProperties, JpaAuditingConfig)
  - `shared.exception`: Exception handling (GlobalExceptionHandler, AuthExceptions, AuthCoreExceptions)
  - `shared.security`: Security filters (JwtAuthFilter)

### Not Found

- `domain/`: NOT FOUND — No pure domain layer (CRITICAL gap)
- `application/port/`: NOT FOUND — No port interfaces (CRITICAL gap)
- `adapter/out/persistence/mapper/`: NOT FOUND — No MapStruct mappers
- `adapter/in/grpc/`: NOT FOUND — No gRPC adapters
- `adapter/out/cache/`: NOT FOUND — No dedicated cache adapter
- `adapter/out/http/`: NOT FOUND — No @HttpExchange clients

### Naming Convention

- Controllers: `<Feature>Controller.kt` (e.g., AuthController, PolicyController)
- Services: `<Feature>Service.kt` or `<Feature>Engine.kt` or `<Feature>Adapter.kt`
- Entities: `<Feature>Entity.kt` (e.g., UserEntity, PolicyEntity)
- DTOs: `<Feature>Dtos.kt` (e.g., MfaDtos, SsoDtos)
- Repositories: `Repositories.kt` (single file for all repos in a context) or `<Feature>Repository.kt`
- Exceptions: `<Feature>Exceptions.kt` (e.g., AuthExceptions, AuthCoreExceptions)
- Language: Kotlin
- Build: Gradle Kotlin DSL (`build.gradle.kts`)
