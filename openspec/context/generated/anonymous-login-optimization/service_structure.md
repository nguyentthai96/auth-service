# Service Structure

_Generated: 2025-07-15 (REUSE — updated scan)_

## auth-service

### Detected Packages

- `auth.adapter.in.web` — REST controllers (`AnonymousAuthController`, `CqrsAuthController`)
- `auth.adapter.in.web.dto` — Request/Response DTOs (`CreateAnonymousSessionRequest`, `AnonymousTokenResponse`, `StoreSessionDataRequest`, etc.)
- `auth.adapter.out.persistence` — JPA persistence adapters
- `auth.adapter.out.cipher` — Redis-based cipher adapters (`RedisAntiReplayValidator`, `RedisCipherKeySessionResolver`)
- `auth.application` — Application services (`AnonymousSessionDataService`, `AnonymousRateLimitService`, `SessionPromotionService`, `JwtService`, `SessionCleanupScheduler`, etc.)
- `auth.application.command` — CQRS command handlers (`AnonymousSessionHandler`, `RenewAnonymousTokenHandler`, `LoginHandler`, `RegisterHandler`, etc.)
- `auth.application.event` — Domain events
- `auth.application.query` — CQRS query handlers
- `auth.application.port` — Port interfaces (hexagonal architecture)
- `auth.application.cipher` — Cipher services (`DecryptionVaultService`)
- `auth.domain` — Domain entities
- `rbac.adapter.out.persistence.entity` — `TokenBlacklistEntity`
- `rbac.adapter.out.persistence.repository` — `TokenBlacklistRepository`
- `shared.config` — Configuration classes (`RedisLuaScriptConfig`, `SecurityProperties`, `RedisConfig`, `SecurityConfig`, `HttpClientConfig`, `KafkaConfig`, etc.)
- `shared.exception` — Exception classes (`AnonymousExceptions`, `AuthErrorCode`, `AuthExceptions`, `GlobalExceptionHandler`)
- `shared.filter` — Servlet filters (`IdempotencyFilter`)
- `shared.i18n` — Internationalization
- `shared.security` — Security configuration
- `shared.audit` — Audit infrastructure
- `shared.persistence` — Persistence configuration

### Naming Convention

- Controllers: `{Feature}Controller` (e.g., `AnonymousAuthController`)
- Handlers: `{Action}Handler` (e.g., `AnonymousSessionHandler`, `LoginHandler`)
- Services: `{Feature}Service` (e.g., `AnonymousSessionDataService`, `SessionPromotionService`)
- Commands: `{Action}Command` (e.g., `CreateAnonymousSessionCommand`)
- Results: `{Feature}Result` (e.g., `AnonymousSessionResult`, `PromotionResult`)
- Config: `{Feature}Config` / `{Feature}Properties` (e.g., `RedisLuaScriptConfig`, `SecurityProperties`)
- Exceptions: `{Feature}{Error}Exception` (e.g., `AnonymousRateLimitedException`)
- Lua scripts: `{description_snake_case}.lua` (e.g., `sliding_window_rate_limit.lua`)

### Key Patterns

- **CQRS**: CommandHandler<Command, Result> from `eventsourcing-utils` library
- **Redis**: `StringRedisTemplate` (not `RedisTemplate<String, Object>`)
- **Lua Scripts**: `DefaultRedisScript<Long>` beans managed by `RedisLuaScriptConfig`
- **Pipelining**: `redisTemplate.executePipelined { connection -> ... null }`
- **Metrics**: `MeterRegistry` injected, Counter/Timer usage throughout
- **Error Handling**: `AuthException` hierarchy → `AuthErrorCode` enum → `GlobalExceptionHandler`

### Not Found

- `factory/` package — NOT FOUND (CQRS handler pattern used instead)
- `cache/` package — exists at `shared/cache/` but empty
- `model/` package — NOT FOUND (domain entities in `auth.domain`)
- `entity/` package — at `rbac.adapter.out.persistence.entity/`, not at `auth/entity/`
