# Base Class Map

_Generated: 2025-07-15 | Services: auth-service_

## Controller

- `AnonymousAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt` — `@RestController`, `@RequestMapping("/api/v1/auth/anonymous")`, 5 endpoints (POST create, POST renew, PUT data, GET data, DELETE data)
- `CqrsAuthController` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` — `@RestController`, login/register endpoints (promotion integration point)

## Handler (CQRS CommandHandler)

- `AnonymousSessionHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` — implements `CommandHandler<CreateAnonymousSessionCommand, AnonymousSessionResult>` — **OPTIMIZATION TARGET**: pipeline HSET+EXPIRE (lines 64-65), add dataSize=0 to session map
- `RenewAnonymousTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt` — implements `CommandHandler<RenewAnonymousTokenCommand, AnonymousSessionResult>` — minor optimization: pipeline renewalCount+EXPIRE
- `LoginHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt` — implements `CommandHandler<LoginCommand, LoginResult>` — promotion caller at line 155
- `RegisterHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt` — implements `CommandHandler<RegisterCommand, RegisterResult>` — promotion caller at line 83
- `RefreshTokenHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt` — implements `CommandHandler<RefreshTokenCommand, AuthToken>`
- `RevokeSessionsHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt` — implements `CommandHandler<RevokeSessionsCommand, Int>`
- `SwitchDomainHandler` — `src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt` — implements `CommandHandler<SwitchDomainCommand, AuthToken>`

## Application Service

- `AnonymousSessionDataService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — **OPTIMIZATION TARGET** (3 changes): (1) batch pipeline in `transferData()` lines 90-115, (2) running counter replacing `getSessionDataSize()` SCAN+STRLEN lines 129-147, (3) TOCTOU fix in `storeData()` lines 37-57
- `AnonymousRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` — **OPTIMIZATION TARGET**: replace INCR+EXPIRE fixed window (lines 44-54) with Lua sliding window counter
- `SessionPromotionService` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` — **OPTIMIZATION TARGET** (2 changes): (1) UUID lock value replacing static "locked" (line 38), (2) Lua conditional DEL in `releaseLock()` (lines 140-147)
- `JwtService` — `src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt` — JWT generation/parsing, `generateAnonymousToken()` at line 157, `parseAnonymousToken()` at line 175. No optimization needed.
- `SessionCleanupScheduler` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt` — **OPTIMIZATION TARGET**: extend with token_blacklist cleanup for expired anonymous entries
- `LoginRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt` — Same INCR+EXPIRE pattern — sliding window can be applied here too (future)
- `MfaRateLimitService` — `src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt` — Same `StringRedisTemplate` usage pattern
- `SessionPolicyService` — `src/main/kotlin/com/ntt/authservice/auth/application/SessionPolicyService.kt` — Session policy enforcement

## Client / Gateway

- `CaptchaGateway` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/CaptchaGateway.kt` — CAPTCHA verification port (not optimization target)

## Security Filter

- `JwtAuthFilter` — `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` — extends `OncePerRequestFilter`, anonymous token detection at line 63 (`ROLE_ANONYMOUS`). No optimization needed.

## Configuration (NEW — to be created)

- `RedisLuaScriptConfig` — TO BE CREATED at `src/main/kotlin/com/ntt/authservice/shared/config/RedisLuaScriptConfig.kt` — `@Configuration` defining `DefaultRedisScript<Long>` beans for Lua scripts

## Factory

NOT DETECTED — No factory classes found. Token generation uses `TokenGenerator` component.

## NOT DETECTED

- Factory pattern (no `*Factory` classes)
- `executePipelined` usage (0 results in codebase — NEW pattern to introduce)
- `RedisScript` usage (0 results in codebase — NEW pattern to introduce)
