# Impact Analysis: anonymous-login-optimization

_Generated: 2025-07-15_

> **Type**: MAINTENANCE — Redis optimization + data integrity hardening
> **Previous**: Archive v1 covered bug fixes, tests, observability (completed)
> **Direction**: Approach D — Balanced Optimization v3 — 3-Lua-Script Architecture with 4-Phase Delivery (brainstorm_notes.md)

[CHANGED] Scope fully changed from archive v1. Archive covered JTI fix, ThreadLocal removal, metrics, tests. This version covers Redis pipelining, Lua scripts, sliding window rate limiting, batch transfer, safe lock release, TOCTOU fix, running counter, cleanup scheduler, and config enhancements.

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [AnonymousSessionHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt) | L56-65 | [MODIFY] Pipeline HSET+EXPIRE (FR-001), add `dataSize=0` to session map (FR-005), pipeline fallback (FR-010) |
| 2 | [AnonymousRateLimitService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt) | L36-70 | [MODIFY] Replace INCR+EXPIRE with Lua sliding window (FR-002), Lua fallback (FR-011) |
| 3 | [AnonymousSessionDataService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) | L37-57, L88-130, L135-160 | [MODIFY] Atomic storeData via Lua (FR-007+FR-005), pipeline MGET+MSET in transferData (FR-003), replace getSessionDataSize SCAN+STRLEN with HGET (FR-005) |
| 4 | [SessionPromotionService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) | L35, L125-148 | [MODIFY] UUID lock value (FR-004), Lua safe lock release (FR-004) |
| 5 | [SessionCleanupScheduler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt) | L20-23, L50+ | [MODIFY] Add TokenBlacklistRepository dependency, add cleanupExpiredBlacklistEntries @Scheduled method (FR-013) |
| 6 | [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | L191-203 | [MODIFY] Add `slidingWindowEnabled: Boolean = true`, `scanCount: Int = 100` to AnonymousProperties (FR-014) |
| 7 | [Repositories.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt) | L83+ | [MODIFY] Add `deleteByExpiresAtBefore(cutoff: Instant): Int` to TokenBlacklistRepository (FR-013) |

### New Files

| # | File | Chức năng |
|---|------|-----------|
| 1 | `src/main/kotlin/com/ntt/authservice/shared/config/RedisLuaScriptConfig.kt` | [NEW] Spring @Configuration defining DefaultRedisScript<Long> beans for 3 Lua scripts (FR-008) |
| 2 | `src/main/resources/redis/sliding_window_rate_limit.lua` | [NEW] Sliding window rate limit Lua script — 2 KEYS, 3 ARGV (~27 lines) (FR-009) |
| 3 | `src/main/resources/redis/safe_lock_release.lua` | [NEW] Conditional DEL with UUID ownership — 1 KEY, 1 ARGV (~8 lines) (FR-009) |
| 4 | `src/main/resources/redis/atomic_data_store.lua` | [NEW] Atomic size check + data write + HINCRBY — 2 KEYS, 3 ARGV (~15 lines) (FR-007+FR-005) |

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. KHÔNG dùng Mermaid.

#### FR-001+FR-005+FR-010: `AnonymousSessionHandler.handle()` (L40-77)

```
⟶ handle(command: CreateAnonymousSessionCommand)
├── rateLimitService.checkRateLimit(command.ipAddress)          // ← now uses Lua (FR-002)
├── sessionId = UUID.randomUUID()
├── token = jwtService.generateAnonymousToken(sessionId, ...)
├── sessionData = mapOf(
│       "deviceFingerprint" → ..., "ipAddress" → ...,
│       "createdAt" → ..., "renewalCount" → "0",
│       "dataSize" → "0"                                       // ← NEW field (FR-005)
│   )
├── try {
│       redisTemplate.executePipelined(RedisCallback {          // ← NEW pipeline (FR-001)
│           connection.hashCommands().hMSet(sessionKey, sessionData)
│           connection.keyCommands().expire(sessionKey, sessionTtl)
│       })
│   } catch (ex: Exception) {                                  // ← FR-010 fallback
│       log.warn("Pipeline failed, falling back to sequential")
│       redisTemplate.opsForHash().putAll(sessionKey, sessionData)
│       redisTemplate.expire(sessionKey, sessionTtl)
│   }
└── return AnonymousSessionResult(token, sessionId, expiresIn)
```

#### FR-002+FR-011: `AnonymousRateLimitService.checkRateLimit()` (L36-70)

```
⟶ checkRateLimit(ipAddress: String)
├── config = securityProperties.anonymous.rateLimit
├── slidingWindowEnabled = securityProperties.anonymous.slidingWindowEnabled
├── slidingWindowEnabled?
│   ├── YES → try {
│   │       currentWindowKey = "anon:rate:{ip}:{currentWindowId}"
│   │       prevWindowKey = "anon:rate:{ip}:{prevWindowId}"
│   │       result = redisTemplate.execute(                     // ← Lua EVAL (FR-002)
│   │           slidingWindowScript,
│   │           listOf(currentWindowKey, prevWindowKey),
│   │           maxAttempts, windowSeconds, elapsedSeconds
│   │       )
│   │       if (result == -1L) → throw AnonymousRateLimitedException
│   │   } catch (scriptEx: Exception) {                        // ← FR-011 fallback
│   │       log.warn("Lua script failed, falling back to fixed-window")
│   │       → FIXED-WINDOW logic (original INCR+EXPIRE)
│   │   }
│   └── NO → FIXED-WINDOW logic (original INCR+EXPIRE)
└── catch (RedisConnectionFailureException) → fail-open
```

#### FR-007+FR-005: `AnonymousSessionDataService.storeData()` (L37-60)

```
⟶ storeData(sessionId, namespace, key, value)
├── !verifySessionExists(sessionId)? → throw AnonymousSessionExpiredException
├── dataKey = "anon:data:{sessionId}:{namespace}:{key}"
├── sessionKey = "anon:session:{sessionId}"
├── sessionTtl = redisTemplate.getExpire(sessionKey)
├── result = redisTemplate.execute(                             // ← Lua EVAL (FR-007)
│       atomicDataStoreScript,
│       listOf(sessionKey, dataKey),
│       maxDataSizeBytes.toString(),
│       value,
│       sessionTtl.toString()
│   )
├── result == -1L? → throw AnonymousDataLimitExceededException  // ← atomic check
└── meterRegistry.counter("auth.anonymous.data.stored").increment()
```

#### FR-003: `AnonymousSessionDataService.transferData()` (L88-130)

```
⟶ transferData(sessionId, userId): DataTransferResult
├── SCAN collect all keys matching "anon:data:{sessionId}:*"
├── keys = allCollectedKeys
├── if (keys.isEmpty()) → return DataTransferResult(0, emptyList(), false)
├── // Pipeline GET all values
│   values = redisTemplate.executePipelined(RedisCallback {     // ← NEW pipeline (FR-003)
│       keys.forEach { connection.stringCommands().get(it) }
│   })
├── // Pipeline SET all to user namespace
│   redisTemplate.executePipelined(RedisCallback {              // ← NEW pipeline (FR-003)
│       keys.zip(values).forEach { (key, value) →
│           userKey = "user:session_data:{userId}:{namespace}:{dataKey}"
│           connection.stringCommands().set(userKey, value, promotedTtl)
│       }
│   })
└── return DataTransferResult(itemCount, namespaces, partial=false)
```

#### FR-004: `SessionPromotionService` — Lock acquire + release

```
⟶ acquireLock(sessionId): Boolean
├── lockKey = "anon:lock:{sessionId}"
├── ownerUUID = UUID.randomUUID().toString()                    // ← NEW (FR-004)
├── redisTemplate.opsForValue().setIfAbsent(
│       lockKey, ownerUUID, Duration.ofSeconds(LOCK_TTL_SECONDS)  // ← UUID instead of "locked"
│   )
└── store ownerUUID for release

⟶ releaseLock(sessionId, ownerUUID)
├── lockKey = "anon:lock:{sessionId}"
├── result = redisTemplate.execute(                             // ← Lua EVAL (FR-004)
│       safeLockReleaseScript,
│       listOf(lockKey),
│       ownerUUID
│   )
└── result == 0L? → log.warn("Lock owned by different process")
```

#### FR-013: `SessionCleanupScheduler.cleanupExpiredBlacklistEntries()`

```
⟶ cleanupExpiredBlacklistEntries()                              // ← NEW @Scheduled method
├── cutoff = Instant.now()
├── count = tokenBlacklistRepository.deleteByExpiresAtBefore(cutoff)
└── log.info("Cleaned up {} expired token blacklist entries", count)
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (7 files MODIFY + 4 files NEW)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `AnonymousSessionHandler.kt` | [AnonymousSessionHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt) | Pipeline HSET+EXPIRE, add dataSize=0, fallback |
| 2 | `AnonymousRateLimitService.kt` | [AnonymousRateLimitService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt) | Lua sliding window, fallback to fixed-window |
| 3 | `AnonymousSessionDataService.kt` | [AnonymousSessionDataService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) | Lua atomic store, pipeline transfer, HGET counter |
| 4 | `SessionPromotionService.kt` | [SessionPromotionService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) | UUID lock + Lua safe release |
| 5 | `SessionCleanupScheduler.kt` | [SessionCleanupScheduler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt) | Add blacklist cleanup method |
| 6 | `SecurityProperties.kt` | [SecurityProperties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | Add config fields to AnonymousProperties |
| 7 | `Repositories.kt` | [Repositories](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt) | Add deleteByExpiresAtBefore to TokenBlacklistRepository |

### 🟡 Indirect Impact — auth-service (4 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `LoginHandler.kt` | [LoginHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | Calls SessionPromotionService.promoteSession() — promotion now uses UUID lock + Lua release internally. No API change. |
| 2 | `RegisterHandler.kt` | [RegisterHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt) | Same — calls SessionPromotionService.promoteSession(). No API change. |
| 3 | `RenewAnonymousTokenHandler.kt` | [RenewAnonymousTokenHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt) | Uses AnonymousRateLimitService — now Lua-based internally. No API change. |
| 4 | `AnonymousAuthController.kt` | [AnonymousAuthController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt) | Calls handlers — all optimizations are internal. No API contract change. |

### 🟠 Cross-service Impact (0 files)

No cross-service impact. All optimizations are internal to auth-service Redis operations. API contracts unchanged.

### 🟢 Shared Utilities (2 files — new only)

| # | File | Link | Methods dùng |
|---|------|------|-------------|
| 1 | `RedisLuaScriptConfig.kt` | TO BE CREATED at `shared/config/` | `slidingWindowRateLimitScript()`, `safeLockReleaseScript()`, `atomicDataStoreScript()` beans |
| 2 | `RedisConfig.kt` | [RedisConfig](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt) | Existing — no modification. StringRedisTemplate bean used by all services. |

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| Fixed-window INCR+EXPIRE | [AnonymousRateLimitService:L44-54](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt#L44) | 100% | REUSE as fallback | 🟢 (1 caller) | Keep as fallback when Lua fails (FR-011) |
| Simple DELETE lock release | [SessionPromotionService:L140-147](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt#L140) | 100% | REPLACE | 🟢 (1 caller — internal) | Replace with Lua conditional DEL |
| SCAN+STRLEN size calc | [AnonymousSessionDataService:L135-160](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt#L135) | 100% | REPLACE | 🟢 (1 caller — storeData) | Replace with HGET dataSize O(1) |
| Per-key GET+SET transfer | [AnonymousSessionDataService:L88-130](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt#L88) | 100% | REPLACE | 🟢 (1 caller — promoteSession) | Replace with pipeline MGET+MSET |
| Sequential HSET+EXPIRE | [AnonymousSessionHandler:L63-64](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt#L63) | 100% | REPLACE | 🟢 (1 caller — handle) | Replace with executePipelined |
| Session cleanup scheduler | [SessionCleanupScheduler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt) | 80% pattern | EXTEND | 🟢 (0 new callers) | Add second @Scheduled method for blacklist cleanup |
| Rate limit pattern (LoginRateLimitService) | [LoginRateLimitService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt) | 90% | REUSE pattern (future) | 🟢 (0 callers affected) | Same INCR+EXPIRE pattern — sliding window applicable later |

**Summary:** No EXTRACT needed. All changes are in-place REPLACE/EXTEND within existing classes. New patterns (executePipelined, RedisScript, Lua) are introduced but don't require extracting shared logic.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `StringRedisTemplate` | Auto-configured bean | `executePipelined(RedisCallback)`, `execute(RedisScript<T>, keys, args)`, `opsForHash()`, `opsForValue()`, `expire()`, `getExpire()` | Primary Redis client — all anonymous services use this |
| `DefaultRedisScript<Long>` | Spring Data Redis | Constructor: `(Resource, Long::class.java)` | Bean defined in RedisLuaScriptConfig — SHA1 caching automatic |
| `MeterRegistry` | Auto-configured (Actuator) | `counter()`, `Timer.start()` | Already injected in all anonymous services (from archive v1) |
| `TokenBlacklistRepository` | JpaRepository | `save()`, `existsByTokenJti()`, `deleteByExpiresAtBefore(Instant)` ← NEW | Used by SessionPromotionService + SessionCleanupScheduler |
| `SecurityProperties.AnonymousProperties` | Config data class | `.slidingWindowEnabled`, `.scanCount`, `.maxDataSizeBytes`, `.rateLimit` | Extended with 2 new fields |

### Config Keys

| Key | Source | Example value | Nơi dùng |
|-----|--------|--------------|---------|
| `app.security.anonymous.slidingWindowEnabled` | SecurityProperties | `true` | `AnonymousRateLimitService.checkRateLimit()` — feature flag |
| `app.security.anonymous.scanCount` | SecurityProperties | `100` | `AnonymousSessionDataService.transferData()` — SCAN batch size |
| `app.security.anonymous.maxDataSizeBytes` | SecurityProperties | `65536` | `atomic_data_store.lua` ARGV[1] — size limit |
| `app.security.anonymous.sessionTtlSeconds` | SecurityProperties | `86400` | Session EXPIRE TTL |
| `app.security.anonymous.rateLimit.maxAttempts` | SecurityProperties | `5` | Lua sliding window ARGV[1] |
| `app.security.anonymous.rateLimit.windowSeconds` | SecurityProperties | `3600` | Lua sliding window ARGV[2] |

### Error Codes Thrown

| Error Code | Condition | Nơi throw |
|-----------|-----------|-----------|
| `AUTH_041` (`AnonymousDataLimitExceededException`) | Lua atomic_data_store returns -1 (size exceeded) | [storeData():L49](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt#L49) |
| `AUTH_043` (`AnonymousRateLimitedException`) | Lua sliding_window returns -1 (rate exceeded) | [checkRateLimit():L57](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt#L57) |
| `AUTH_040` (`AnonymousSessionExpiredException`) | Session not found in Redis | [storeData():L41](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt#L41) |

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| N/A | N/A | N/A | No new DTOs — all changes are internal Redis operations |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `redisTemplate.executePipelined(RedisCallback)` | Spring Data Redis `RedisTemplate.executePipelined()` | Spring Data Redis docs | ✅ Available (Lettuce driver) |
| `redisTemplate.execute(RedisScript, keys, args)` | Spring Data Redis `RedisTemplate.execute(RedisScript<T>)` | Spring Data Redis docs | ✅ Available |
| `DefaultRedisScript<Long>(Resource, Long::class.java)` | Spring Data Redis `DefaultRedisScript` | Spring Data Redis docs | ✅ Available |
| `tokenBlacklistRepository.deleteByExpiresAtBefore(Instant)` | Spring Data JPA derived query | JPA Query Method naming | ✅ Auto-generated by Spring Data |
| `LOCK_VALUE = "locked"` → UUID | `SessionPromotionService.kt` L35 | grep_search | ✅ Confirmed — exists, to be replaced |
| `getSessionDataSize()` O(N) SCAN+STRLEN | `AnonymousSessionDataService.kt` L135-160 | view_file | ✅ Confirmed — exists, to be replaced |
