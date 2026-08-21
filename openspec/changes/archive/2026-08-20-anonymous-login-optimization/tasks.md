<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Non-Financial", factory: "N/A", feature_type: "MAINTENANCE", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: anonymous-login-optimization

> **Type**: MAINTENANCE | **Flow**: Non-Financial | **FRs**: 14 (12 active, 2 deferred)
> **Direction**: Approach D — Balanced Optimization v3 — 3-Lua-Script Architecture with 4-Phase Delivery

## Changes

[CHANGED] Entire scope changed from archive v1 (JTI fix, ThreadLocal, metrics, tests → completed) to v2 (Redis pipelining, Lua scripting, data integrity hardening).

**Phase 1** (Data Integrity — MUST, ~1-2 days): FR-004, FR-007, FR-008, FR-009
**Phase 2** (Performance, ~1-2 days): FR-001, FR-002, FR-003, FR-005
**Phase 3** (Reliability & Operations, ~1 day): FR-010, FR-011, FR-013, FR-014
**Phase 4** (Deferred): FR-006, FR-012

**Total**: 7 files modified, 4 files new

| Metric | Value |
|--------|-------|
| Modified files | 7 |
| New files | 4 |
| Active FRs | 12 |
| Deferred FRs | 2 |
| New dependencies | 0 |
| Database migrations | 0 |

---

## Phase 1: Data Integrity — Lua Infrastructure + Critical Fixes

- [x] **Task 1: Add config fields to AnonymousProperties** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` | Action: [MODIFY]
  - FR: FR-014 — Configuration enhancements
  - Details:
    - Add `val slidingWindowEnabled: Boolean = true` after `rateLimit` field in `AnonymousProperties` data class (L191-203)
    - Add `val scanCount: Int = 100` after `slidingWindowEnabled`
    - Both fields have sensible defaults — application starts without explicit configuration
  - Dependencies: None — this is a leaf change consumed by other tasks

- [x] **Task 2: Add deleteByExpiresAtBefore to TokenBlacklistRepository** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt` | Action: [MODIFY]
  - FR: FR-013 — TokenBlacklist cleanup
  - Details:
    - Add `fun deleteByExpiresAtBefore(cutoff: java.time.Instant): Int` to `TokenBlacklistRepository` interface (after L84 `existsByTokenJti`)
    - Import `java.time.Instant` if not already imported
    - Spring Data JPA auto-generates the query from method name — no `@Query` needed
    - Add `@Modifying` and `@Transactional` annotations for delete operations
  - Dependencies: `TokenBlacklistEntity` has `expiresAt: Instant` field (verified in `PermissionEntities.kt`)

- [x] **Task 3: Create Lua script files** `[NEW]`
  - File: `src/main/resources/redis/sliding_window_rate_limit.lua` | Action: [NEW]
  - File: `src/main/resources/redis/safe_lock_release.lua` | Action: [NEW]
  - File: `src/main/resources/redis/atomic_data_store.lua` | Action: [NEW]
  - FR: FR-009 — Lua script files
  - Details:
    - **`sliding_window_rate_limit.lua`** (~27 lines):
      - KEYS[1] = `anon:rate:{ip}:{currentWindow}`, KEYS[2] = `anon:rate:{ip}:{prevWindow}`
      - ARGV[1] = maxAttempts, ARGV[2] = windowSeconds, ARGV[3] = elapsedSeconds
      - Logic: `local current = redis.call('INCR', KEYS[1])` → if first → `EXPIRE 2×window` → `local prev = tonumber(redis.call('GET', KEYS[2]) or '0')` → `weight = math.max(0, (window - elapsed) / window)` → `count = prev * weight + current` → if `count > max` return `-1` else return `math.floor(count)`
      - Returns: Long (-1 = denied, ≥0 = weighted count)
    - **`safe_lock_release.lua`** (~8 lines):
      - KEYS[1] = lock key, ARGV[1] = ownerUUID
      - Logic: `if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]) return 1 else return 0 end`
      - Returns: Long (1 = released, 0 = not owner)
    - **`atomic_data_store.lua`** (~15 lines):
      - KEYS[1] = session hash key, KEYS[2] = data key to write
      - ARGV[1] = maxDataSizeBytes, ARGV[2] = value, ARGV[3] = ttlSeconds
      - Logic: `local currentSize = tonumber(redis.call('HGET', KEYS[1], 'dataSize') or '0')` → `local newSize = #ARGV[2]` → if `currentSize + newSize > tonumber(ARGV[1])` return `-1` → `redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[3]))` → `redis.call('HINCRBY', KEYS[1], 'dataSize', newSize)` → return `currentSize + newSize`
      - Returns: Long (-1 = limit exceeded, ≥0 = new total size)
  - Dependencies: None — pure Lua files

- [x] **Task 4: Create RedisLuaScriptConfig** `[NEW]`
  - File: `src/main/kotlin/com/ntt/authservice/shared/config/RedisLuaScriptConfig.kt` | Action: [NEW]
  - FR: FR-008 — RedisLuaScriptConfig configuration class
  - Base: Spring `@Configuration` class in `com.ntt.authservice.shared.config` package
  - Pattern: Follows existing `RedisConfig.kt` naming convention
  - Details:
    - `@Configuration` class with 3 `@Bean` methods
    - `@Bean fun slidingWindowRateLimitScript(): DefaultRedisScript<Long>` — loads `ClassPathResource("redis/sliding_window_rate_limit.lua")`, result type `Long::class.java`
    - `@Bean fun safeLockReleaseScript(): DefaultRedisScript<Long>` — loads `ClassPathResource("redis/safe_lock_release.lua")`, result type `Long::class.java`
    - `@Bean fun atomicDataStoreScript(): DefaultRedisScript<Long>` — loads `ClassPathResource("redis/atomic_data_store.lua")`, result type `Long::class.java`
    - SHA1 caching is automatic with `DefaultRedisScript` — Redis EVALSHA used after first execution
  - Dependencies: `org.springframework.data.redis.core.script.DefaultRedisScript`, `org.springframework.core.io.ClassPathResource`
  - Imports: `org.springframework.context.annotation.Bean`, `org.springframework.context.annotation.Configuration`

- [x] **Task 5: Implement safe distributed lock release in SessionPromotionService** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt` | Action: [MODIFY]
  - FR: FR-004 — Safe distributed lock release
  - Base: `SessionPromotionService` from `com.ntt.authservice.auth.application`
  - Details:
    - Add constructor dependency: `private val safeLockReleaseScript: DefaultRedisScript<Long>` (injected from RedisLuaScriptConfig)
    - Remove `private const val LOCK_VALUE = "locked"` from companion object (L35)
    - Change `acquireLock(sessionId): Boolean` to `acquireLock(sessionId): String?` — returns ownerUUID on success, null on failure:
      ```kotlin
      private fun acquireLock(sessionId: String): String? {
          val lockKey = "$LOCK_PREFIX$sessionId"
          val ownerUUID = java.util.UUID.randomUUID().toString()
          return try {
              val acquired = redisTemplate.opsForValue().setIfAbsent(
                  lockKey, ownerUUID, Duration.ofSeconds(LOCK_TTL_SECONDS)
              ) == true
              if (acquired) ownerUUID else null
          } catch (ex: Exception) {
              log.error("Failed to acquire promotion lock for session={}: {}", sessionId, ex.message)
              null
          }
      }
      ```
    - Change `releaseLock(sessionId)` to `releaseLock(sessionId, ownerUUID)`:
      ```kotlin
      private fun releaseLock(sessionId: String, ownerUUID: String) {
          val lockKey = "$LOCK_PREFIX$sessionId"
          try {
              val released = redisTemplate.execute(
                  safeLockReleaseScript, listOf(lockKey), ownerUUID
              )
              if (released == 0L) {
                  log.warn("Lock for session={} owned by different process — skipped release", sessionId)
              }
          } catch (ex: Exception) {
              log.warn("Failed to release lock for session={} via Lua — will auto-expire in {}s", sessionId, LOCK_TTL_SECONDS)
          }
      }
      ```
    - Update `promoteSession()`: `val ownerUUID = acquireLock(sessionId)` → null check instead of boolean → pass `ownerUUID` to `releaseLock` in finally block
  - Dependencies: Task 3 (Lua files), Task 4 (RedisLuaScriptConfig)

- [x] **Task 6: Implement atomic storeData via Lua in AnonymousSessionDataService** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` | Action: [MODIFY]
  - FR: FR-007 — TOCTOU fix, FR-005 — Running data size counter
  - Base: `AnonymousSessionDataService` from `com.ntt.authservice.auth.application`
  - Details:
    - Add constructor dependency: `private val atomicDataStoreScript: DefaultRedisScript<Long>` (injected from RedisLuaScriptConfig)
    - Rewrite `storeData()` (L37-60) to use Lua atomic check-and-set:
      ```kotlin
      fun storeData(sessionId: String, namespace: String, key: String, value: String) {
          if (!verifySessionExists(sessionId)) {
              throw AnonymousSessionExpiredException(sessionId)
          }
          val sessionKey = "$SESSION_PREFIX$sessionId"
          val dataKey = dataKey(sessionId, namespace, key)
          val maxSize = securityProperties.anonymous.maxDataSizeBytes
          val sessionTtl = redisTemplate.getExpire(sessionKey)
              ?: securityProperties.anonymous.sessionTtlSeconds

          val result = redisTemplate.execute(
              atomicDataStoreScript,
              listOf(sessionKey, dataKey),
              maxSize.toString(),
              value,
              sessionTtl.toString()
          )

          if (result == -1L) {
              meterRegistry.counter("auth.anonymous.data.size_exceeded").increment()
              val currentSize = redisTemplate.opsForHash<String, String>()
                  .get(sessionKey, "dataSize")?.toLongOrNull() ?: 0L
              throw AnonymousDataLimitExceededException(
                  currentSize + value.toByteArray().size.toLong(),
                  maxSize
              )
          }
          meterRegistry.counter("auth.anonymous.data.stored").increment()
      }
      ```
    - Update `deleteData()` (L78-83) to decrement running counter:
      ```kotlin
      fun deleteData(sessionId: String, namespace: String, key: String) {
          if (!verifySessionExists(sessionId)) {
              throw AnonymousSessionExpiredException(sessionId)
          }
          val dataKey = dataKey(sessionId, namespace, key)
          // Get size before deletion for counter decrement
          val deletedSize = redisTemplate.opsForValue().size(dataKey) ?: 0L
          redisTemplate.delete(dataKey)
          if (deletedSize > 0) {
              val sessionKey = "$SESSION_PREFIX$sessionId"
              redisTemplate.opsForHash<String, String>()
                  .increment(sessionKey, "dataSize", -deletedSize)
          }
      }
      ```
    - Replace `getSessionDataSize()` O(N) SCAN+STRLEN (L135-160) with O(1) HGET:
      ```kotlin
      fun getSessionDataSize(sessionId: String): Long {
          val sessionKey = "$SESSION_PREFIX$sessionId"
          return try {
              redisTemplate.opsForHash<String, String>()
                  .get(sessionKey, "dataSize")?.toLongOrNull() ?: 0L
          } catch (ex: Exception) {
              log.warn("Error reading dataSize for session={}", sessionId, ex)
              0L
          }
      }
      ```
    - Note: Keep old SCAN+STRLEN implementation as `private fun getSessionDataSizeScan(sessionId)` for potential reconciliation (FR-012 deferred)
  - Dependencies: Task 3 (Lua files), Task 4 (RedisLuaScriptConfig)

## Phase 2: Performance — Pipelining + Sliding Window

- [x] **Task 7: Pipeline HSET+EXPIRE in AnonymousSessionHandler** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` | Action: [MODIFY]
  - FR: FR-001 — Pipeline session creation, FR-005 — Initialize dataSize=0
  - Base: `AnonymousSessionHandler` from `com.ntt.authservice.auth.application.command`
  - Details:
    - Add `"dataSize" to "0"` to `sessionData` map (L57-62):
      ```kotlin
      val sessionData = mapOf(
          "deviceFingerprint" to (command.deviceFingerprint ?: ""),
          "ipAddress" to command.ipAddress,
          "createdAt" to Instant.now().toString(),
          "renewalCount" to "0",
          "dataSize" to "0"  // ← FR-005: running counter init
      )
      ```
    - Replace sequential `putAll` + `expire` (L63-64) with pipeline:
      ```kotlin
      try {
          redisTemplate.executePipelined { connection ->
              val rawKey = sessionKey.toByteArray()
              val rawData = sessionData.map { (k, v) -> k.toByteArray() to v.toByteArray() }.toMap()
              connection.hashCommands().hMSet(rawKey, rawData)
              connection.keyCommands().expire(rawKey, sessionTtl.seconds)
              null
          }
      } catch (ex: Exception) {
          log.warn("Pipeline failed for session creation, falling back to sequential: {}", ex.message)
          redisTemplate.opsForHash<String, String>().putAll(sessionKey, sessionData)
          redisTemplate.expire(sessionKey, sessionTtl)
      }
      ```
    - FR-010 fallback is built into the try/catch above
  - Dependencies: None — uses existing `StringRedisTemplate`

- [x] **Task 8: Implement sliding window rate limiting in AnonymousRateLimitService** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` | Action: [MODIFY]
  - FR: FR-002 — Sliding window rate limiting, FR-011 — Lua fallback
  - Base: `AnonymousRateLimitService` from `com.ntt.authservice.auth.application`
  - Details:
    - Add constructor dependencies:
      - `private val slidingWindowRateLimitScript: DefaultRedisScript<Long>` (injected from RedisLuaScriptConfig)
      - `private val securityProperties: SecurityProperties` (already present)
    - Rewrite `checkRateLimit()` (L36-70):
      ```kotlin
      fun checkRateLimit(ipAddress: String) {
          val config = securityProperties.anonymous.rateLimit
          try {
              if (securityProperties.anonymous.slidingWindowEnabled) {
                  checkSlidingWindow(ipAddress, config)
              } else {
                  checkFixedWindow(ipAddress, config)
              }
          } catch (ex: AnonymousRateLimitedException) {
              throw ex
          } catch (ex: RedisConnectionFailureException) {
              log.error("Redis unavailable for rate limit — fail-open, ip={}", ipAddress, ex)
          } catch (ex: Exception) {
              log.error("Unexpected error in rate limit — fail-open, ip={}", ipAddress, ex)
          }
      }
      ```
    - New `private fun checkSlidingWindow(ip, config)`:
      - Calculate `currentWindowId = System.currentTimeMillis() / (config.windowSeconds * 1000)`
      - Calculate `prevWindowId = currentWindowId - 1`
      - Calculate `elapsedSeconds = (System.currentTimeMillis() % (config.windowSeconds * 1000)) / 1000`
      - Keys: `"$RATE_PREFIX$ip:$currentWindowId"`, `"$RATE_PREFIX$ip:$prevWindowId"`
      - Execute: `redisTemplate.execute(slidingWindowRateLimitScript, keys, config.maxAttempts.toString(), config.windowSeconds.toString(), elapsedSeconds.toString())`
      - If result == -1L → increment metric, throw `AnonymousRateLimitedException`
      - Wrap in try/catch — on Lua failure → `log.warn("Lua failed, fallback")` → call `checkFixedWindow()` (FR-011)
    - Extract existing INCR+EXPIRE logic into `private fun checkFixedWindow(ip, config)` (existing code from L40-70)
  - Dependencies: Task 1 (slidingWindowEnabled config), Task 3 (Lua files), Task 4 (RedisLuaScriptConfig)

- [x] **Task 9: Pipeline batch data transfer in AnonymousSessionDataService** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` | Action: [MODIFY]
  - FR: FR-003 — Batch data transfer
  - Base: `AnonymousSessionDataService` from `com.ntt.authservice.auth.application`
  - Details:
    - Rewrite `transferData()` (L88-130) to use pipeline MGET + pipeline MSET:
      ```kotlin
      fun transferData(sessionId: String, userId: Long): DataTransferResult {
          val pattern = "$DATA_PREFIX$sessionId:*"
          val promotedTtl = Duration.ofSeconds(securityProperties.anonymous.promotedDataTtlSeconds)
          val scanCount = securityProperties.anonymous.scanCount
          val namespaces = mutableSetOf<String>()

          // Step 1: SCAN collect all keys
          val allKeys = mutableListOf<String>()
          try {
              redisTemplate.execute { connection ->
                  val cursor = connection.scan(
                      ScanOptions.scanOptions().match(pattern).count(scanCount.toLong()).build()
                  )
                  cursor.use {
                      while (cursor.hasNext()) {
                          allKeys.add(String(cursor.next()))
                      }
                  }
                  null
              }
          } catch (ex: Exception) {
              log.error("Error scanning keys for transfer session={}", sessionId, ex)
              return DataTransferResult(0, emptyList(), partial = true)
          }

          if (allKeys.isEmpty()) {
              return DataTransferResult(0, emptyList(), partial = false)
          }

          try {
              // Step 2: Pipeline GET all values
              val values = redisTemplate.executePipelined { connection ->
                  allKeys.forEach { key ->
                      connection.stringCommands().get(key.toByteArray())
                  }
                  null
              }

              // Step 3: Build user key mappings + Pipeline SET all
              val keyValuePairs = mutableListOf<Triple<String, String, String>>() // userKey, value, namespace
              allKeys.forEachIndexed { index, rawKey ->
                  val value = values.getOrNull(index) as? String ?: return@forEachIndexed
                  val parts = rawKey.removePrefix("$DATA_PREFIX$sessionId:").split(":", limit = 2)
                  if (parts.size == 2) {
                      val namespace = parts[0]
                      val dataKey = parts[1]
                      namespaces.add(namespace)
                      val userKey = "user:session_data:$userId:$namespace:$dataKey"
                      keyValuePairs.add(Triple(userKey, value, namespace))
                  }
              }

              redisTemplate.executePipelined { connection ->
                  keyValuePairs.forEach { (userKey, value, _) ->
                      connection.stringCommands().setEx(
                          userKey.toByteArray(),
                          promotedTtl.seconds,
                          value.toByteArray()
                      )
                  }
                  null
              }

              log.info("Transferred {} items from session {} to user {} (namespaces: {})",
                  keyValuePairs.size, sessionId, userId, namespaces)
              return DataTransferResult(keyValuePairs.size, namespaces.toList(), partial = false)
          } catch (ex: Exception) {
              log.error("Error in pipeline transfer session={}", sessionId, ex)
              return DataTransferResult(0, namespaces.toList(), partial = true)
          }
      }
      ```
    - Uses `securityProperties.anonymous.scanCount` from Task 1 for configurable SCAN batch size
  - Dependencies: Task 1 (scanCount config)

## Phase 3: Reliability & Operations

- [x] **Task 10: Add pipeline fallback to AnonymousSessionHandler** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt` | Action: [MODIFY]
  - FR: FR-010 — Pipeline fallback on failure
  - Details:
    - Already implemented as part of Task 7 (try/catch around `executePipelined` with sequential fallback)
    - This task is a verification that Task 7's fallback works correctly
    - Ensure log message: `"Pipeline failed for session creation, falling back to sequential: {ex.message}"`
    - Ensure session is created regardless of pipeline failure
  - Dependencies: Task 7

- [x] **Task 11: Add Lua script fallback to AnonymousRateLimitService** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt` | Action: [MODIFY]
  - FR: FR-011 — Lua script fallback on failure
  - Details:
    - Already implemented as part of Task 8 (try/catch around Lua EVAL with `checkFixedWindow()` fallback)
    - This task is a verification that Task 8's fallback works correctly
    - Ensure log message: `"Lua sliding window failed, falling back to fixed-window: {ex.message}"`
    - Ensure rate limiting works under Lua failure — no request blocked by script errors
  - Dependencies: Task 8

- [x] **Task 12: Extend SessionCleanupScheduler with blacklist cleanup** `[MODIFY]`
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/SessionCleanupScheduler.kt` | Action: [MODIFY]
  - FR: FR-013 — TokenBlacklist cleanup scheduler
  - Base: `SessionCleanupScheduler` from `com.ntt.authservice.auth.application`
  - Details:
    - Add constructor dependency: `private val tokenBlacklistRepository: TokenBlacklistRepository`
    - Add import: `com.ntt.authservice.rbac.adapter.out.persistence.repository.TokenBlacklistRepository`
    - Add new `@Scheduled` method:
      ```kotlin
      @Scheduled(cron = "\${app.security.anonymous.blacklist-cleanup-cron:0 0 */6 * * *}")
      @Transactional
      fun cleanupExpiredBlacklistEntries() {
          val cutoff = Instant.now()
          try {
              val deletedCount = tokenBlacklistRepository.deleteByExpiresAtBefore(cutoff)
              if (deletedCount > 0) {
                  log.info("TOKEN_BLACKLIST_CLEANUP Deleted {} expired entries (cutoff={})",
                      deletedCount, cutoff)
              } else {
                  log.debug("TOKEN_BLACKLIST_CLEANUP No expired entries found (cutoff={})", cutoff)
              }
          } catch (ex: Exception) {
              log.error("TOKEN_BLACKLIST_CLEANUP Failed to cleanup expired entries", ex)
          }
      }
      ```
    - Runs every 6 hours by default (configurable via `app.security.anonymous.blacklist-cleanup-cron`)
    - Deletes all `token_blacklist` entries where `expires_at < NOW()`
  - Dependencies: Task 2 (deleteByExpiresAtBefore in repository)

## Phase 4: Verification

- [x] **Task 13: FR traceability verification**
  - Verify all 14 FRs addressed:
    - FR-001: ✓ Task 7 (pipeline HSET+EXPIRE)
    - FR-002: ✓ Task 8 (sliding window Lua)
    - FR-003: ✓ Task 9 (pipeline MGET+MSET)
    - FR-004: ✓ Task 5 (UUID lock + Lua safe release)
    - FR-005: ✓ Task 6 (running counter via Lua), Task 7 (dataSize=0 init)
    - FR-006: ⏸ DEFERRED (existing metrics adequate)
    - FR-007: ✓ Task 6 (atomic check-and-set via Lua)
    - FR-008: ✓ Task 4 (RedisLuaScriptConfig)
    - FR-009: ✓ Task 3 (3 Lua script files)
    - FR-010: ✓ Task 7/10 (pipeline fallback)
    - FR-011: ✓ Task 8/11 (Lua script fallback)
    - FR-012: ⏸ DEFERRED (Lua atomicity eliminates primary drift risk)
    - FR-013: ✓ Task 12 (blacklist cleanup scheduler)
    - FR-014: ✓ Task 1 (config enhancements)
  - Coverage: **12/14 active** (2 deferred with documented rationale)

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 13 (12 implementation + 1 verification) |
| Modified files | 7 |
| New files | 4 |
| Active FRs covered | 12/12 |
| Deferred FRs | 2 (FR-006, FR-012) |
| New dependencies | 0 |
| Database migrations | 0 |
| Estimated effort | 3-5 developer-days |
| Phases | 4 (Integrity → Performance → Reliability → Verification) |
