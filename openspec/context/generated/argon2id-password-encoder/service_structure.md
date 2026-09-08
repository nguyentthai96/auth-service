# Service Structure

_Generated: 2026-09-08_

## auth-service

### Detected Packages (relevant to password encoding)

- `shared/config/`: Configuration beans — SecurityConfig, SecurityProperties, OutboxProperties
- `shared/exception/`: Exception hierarchy — AuthException base, 40+ domain exceptions
- `auth/application/`: Application services — TokenGenerator, PasswordPolicyService, JwtService
- `auth/application/command/`: CQRS command handlers — TokenGenerator (encode/matches)
- `auth/adapter/in/web/`: REST controllers — CqrsAuthController, AccountLifecycleController
- `rbac/adapter/out/persistence/repository/`: JPA repositories — UserRepository, PasswordHistoryRepository
- `rbac/adapter/out/persistence/entity/`: JPA entities — UserEntity, PasswordHistoryEntity

### Not Found

- `shared/config/PasswordEncoderAutoConfiguration.kt` (TO BE CREATED)
- `auth/application/PasswordUpgradeService.kt` (TO BE CREATED)

### Naming Convention

- Config: `*Config.kt`, `*Properties.kt` (data class)
- Service: `*Service.kt` (application layer)
- Command: `*Generator.kt`, `*Handler.kt` (CQRS commands)
- Controller: `*Controller.kt` (@RestController)
- Entity: `*Entity.kt` (JPA)
- Repository: `*Repository.kt` (Spring Data JPA)
- Exception: `*Exception.kt` (extends AuthException)
