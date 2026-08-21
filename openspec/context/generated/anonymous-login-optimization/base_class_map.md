# Base Class Map

_Generated: 2025-07-15 (REUSE — updated scan) | Services: auth-service_

## Controller

- `AnonymousAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt` — `@RestController`, `@RequestMapping("/api/v1/auth/anonymous")`, 5 endpoints (POST create, POST renew, PUT data, GET data, DELETE data)
- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` — `@RestController`, login/register endpoints (promotion integration point)

## Handler (CQRS CommandHandler)

- `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — implements `CommandHandler<CreateAnonymousSessionCommand, AnonymousSessionResult>` — pipeline HSET+EXPIRE (FR-001 ✅), dataSize=0 init (FR-005 ✅), fallback (FR-010 ✅)
- `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` — implements `CommandHandler<RenewAnonymousTokenCommand, AnonymousSessionResult>`
- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` — implements `CommandHandler<LoginCommand, LoginResult>` — promotion caller
- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` — implements `CommandHandler<RegisterCommand, RegisterResult>` — promotion caller
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt` — implements `CommandHandler<RefreshTokenCommand, AuthToken>`
- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt` — implements `CommandHandler<RevokeSessionsCommand, Int>`
- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt` — implements `CommandHandler<SwitchDomainCommand, AuthToken>`

## Application Service

- `AnonymousSessionDataService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — Redis CRUD for anonymous session data. Uses Lua `atomicDataStoreScript` (FR-007 ✅), pipeline MGET+MSET transfer (FR-003 ✅), running `dataSize` counter (FR-005 ✅).
- `AnonymousRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` — Dual-mode: sliding window Lua (default, FR-002 ✅) + fixed-window INCR+EXPIRE fallback (FR-011 ✅). Feature flag: `slidingWindowEnabled`.
- `SessionPromotionService` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` — UUID-based lock + Lua safe release (FR-004 ✅). Orchestrates promotion: lock → verify → transfer → blacklist → delete → release.
- `JwtService` — `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` — `generateAnonymousToken()`, `parseAnonymousToken()` methods.
- `SessionCleanupScheduler` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt` — Dual cleanup: inactive sessions + expired blacklist entries (FR-013 ✅).
- `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` — Fixed-window pattern. Same `StringRedisTemplate` usage.
- `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` — Same fail-open strategy pattern.
- `AuthService` — `src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt` — Main auth orchestration.

## Config

- `RedisLuaScriptConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisLuaScriptConfig.kt` — `@Configuration`, 3 `DefaultRedisScript<Long>` beans (FR-008 ✅): `slidingWindowRateLimitScript`, `safeLockReleaseScript`, `atomicDataStoreScript`
- `SecurityProperties` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` — `AnonymousProperties` data class with `slidingWindowEnabled`, `scanCount` (FR-014 ✅)
- `RedisConfig` — `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`

## Client / Gateway

NOT DETECTED — anonymous login feature operates entirely within auth-service (no external REST/gRPC calls).

## Factory

NOT DETECTED — CQRS handler pattern used instead of factory pattern.
