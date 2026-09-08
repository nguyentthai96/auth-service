# Service Structure

_Generated: 2026-09-08_

## auth-service

### Detected Packages (relevant to feature)

- `shared/config/`: Configuration classes — `PasswordEncoderAutoConfiguration`, `ConcurrencyLimitedPasswordEncoder`, `SecurityProperties`
- `auth/application/`: Application services — `PasswordUpgradeService`, `PasswordPolicyService`
- `auth/application/command/`: Command handlers — `LoginHandler`

### Package Architecture
- Clean Architecture (Hexagonal): `adapter/in/web` → `application` → `domain` → `adapter/out`
- Config lives in `shared/config/` (cross-cutting concerns)
- YAML config in `src/main/resources/application-security.yml`

### Naming Convention
- Configuration: `*AutoConfiguration.kt`, `*Properties.kt`
- Services: `*Service.kt`
- Handlers: `*Handler.kt`
- Config files: `application-*.yml` (profile-based)

### Not Found (not relevant to this feature)
- `controller/` — feature doesn't add/modify endpoints
- `repository/` — feature doesn't change data access
- `domain/entity/` — feature doesn't change domain model
