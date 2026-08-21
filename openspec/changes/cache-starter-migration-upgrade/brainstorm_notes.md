---
type: brainstorm_notes
change: cache-starter-migration-upgrade
date: 2026-08-21
selected_direction: "Cache Key Taxonomy + Lifecycle Manager"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Cache Key Taxonomy & Lifecycle Management

## Date
2026-08-21

## Context
User yêu cầu thiết kế chuẩn phân loại key theo mục đích, lifecycle, và TTL policy.
Dựa trên inventory thực tế của auth-service — phát hiện 15+ Redis key prefixes thuộc 8+ loại mục đích khác nhau.

---

## Auth-Service Current Redis Key Inventory (THỰC TẾ)

Phân tích source code phát hiện tất cả Redis key prefix patterns:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AUTH-SERVICE REDIS KEY MAP (current)                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  CACHING (giảm DB hit):                                                     │
│    auth:perm:perms:{userId}:{domainId}  → List<String> permissions          │
│    auth:perm:roles:{userId}:{domainId}  → List<String> roles                │
│    (i18n — Caffeine only, chưa có L2)                                       │
│                                                                             │
│  SESSION-BOUND (hủy khi session end):                                       │
│    anon:session:{sessionId}              → Anonymous session data (Hash)     │
│    anon:data:{sessionId}:{ns}:{key}      → Anonymous session K/V data       │
│    cipher:session:{keyId}                → E2EE cipher key session           │
│                                                                             │
│  RATE-LIMITING (counter + window):                                          │
│    rate:login:attempts:{identity}         → Login attempt counter (INCR)     │
│    rate:login:lock:{identity}             → Login lockout flag               │
│    anon:rate:{fingerprint}:{action}       → Anonymous rate limit             │
│    otp:ratelimit:{userId}                 → OTP resend rate limit            │
│    mfa:verify:attempts:{userId}           → MFA attempt counter             │
│    mfa:verify:lock:{userId}               → MFA lockout flag                │
│                                                                             │
│  IDEMPOTENCY (anti-replay):                                                 │
│    idempotency:auth-service:{key}         → Cached API response             │
│    (anti-replay via RedisAntiReplayValidator — SETNX)                       │
│                                                                             │
│  TEMPORARY/PROCESS (TTL-bound steps):                                       │
│    otp:{userId}:{action}                  → OTP code (short TTL)            │
│    mfa:totp:setup:{userId}                → TOTP pending secret             │
│    vault:token:{accessToken}              → Decryption vault token          │
│                                                                             │
│  LOCK (distributed mutex):                                                  │
│    anon:lock:{sessionId}                  → Session promotion lock          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Deep Thinking: Cache Key Taxonomy Design

### Brainstormed Key Categories (8+3 = 11 loại)

#### Từ inventory thực tế (8 loại):

| # | Category | Purpose | TTL Strategy | Eviction | Data Type |
|---|----------|---------|:------------:|:--------:|:---------:|
| 1 | **DATA_CACHE** | Giảm DB load, read-heavy | Medium (15-30m) | Event-driven (Kafka) | Object/List |
| 2 | **SESSION_BOUND** | Dữ liệu gắn phiên user | Session TTL (30m-8h) | On logout/expire | Hash/Object |
| 3 | **RATE_COUNTER** | Đếm requests (rate limit) | Fixed window (1-15m) | Auto-expire | Number (INCR) |
| 4 | **RATE_LOCK** | Khóa tạm khi vượt limit | Lockout period (15-60m) | Auto-expire | Boolean (flag) |
| 5 | **IDEMPOTENCY** | Anti-duplicate request | Request window (5-30m) | Auto-expire | String (response) |
| 6 | **PROCESS_TOKEN** | Dữ liệu tạm cho multi-step | Step timeout (5-15m) | Auto-expire | Object |
| 7 | **DISTRIBUTED_LOCK** | Mutex cho concurrent ops | Lock timeout (10-30s) | Auto-release | String (owner ID) |
| 8 | **CRYPTO_SESSION** | Key material cho E2EE | Key rotation (1-24h) | On key rotate | Binary/String |

#### Brainstormed thêm (3 loại mới):

| # | Category | Purpose | TTL Strategy | Eviction | Data Type |
|---|----------|---------|:------------:|:--------:|:---------:|
| 9 | **STATIC_CONFIG** | Data tĩnh ít thay đổi (system config, feature flags) | Very long (24h+) hoặc permanent | Explicit evict on change | Object/Map |
| 10 | **COMPUTED_AGGREGATE** | Kết quả tính toán từ nhiều nguồn (dashboard stats, report cache) | Medium-Long (1-6h) | Event/schedule refresh | Object/Number |
| 11 | **WARMUP_PRELOAD** | Data preload khi app start (critical paths) | Long (1-24h) | Auto-refresh before expire | Object/List |

---

## Key Naming Convention Design

### Standard Format

```
{service}:{category}:{entity}:{qualifier}:{id}

Ví dụ:
  auth:cache:perm:perms:1001:5         → DATA_CACHE
  auth:session:anon:data:abc123:ns:k   → SESSION_BOUND
  auth:rate:login:attempts:user@mail   → RATE_COUNTER
  auth:rate:login:lock:user@mail       → RATE_LOCK
  auth:idempotent:api:req-uuid-123     → IDEMPOTENCY
  auth:process:otp:code:1001:login     → PROCESS_TOKEN
  auth:lock:session:promote:abc123     → DISTRIBUTED_LOCK
  auth:crypto:cipher:session:key-id    → CRYPTO_SESSION
  auth:config:feature:flag:dark-mode   → STATIC_CONFIG
  auth:computed:stats:login:daily      → COMPUTED_AGGREGATE
  auth:warmup:perm:tenant:5            → WARMUP_PRELOAD
```

### Quy tắc Naming

```
┌──────────────────────────────────────────────────────────────────┐
│  NAMING RULES                                                    │
├──────────────────────────────────────────────────────────────────┤
│  1. Tất cả lowercase, phân cách bằng ":"                         │
│  2. Level 1: {service} — namespace isolation (auth, account...)  │
│  3. Level 2: {category} — one of 11 categories                  │
│  4. Level 3+: {entity}:{qualifier}:{id}                          │
│  5. Không dùng dấu space, underscore trong key                   │
│  6. ID là phần cuối — cho phép prefix scan                       │
│  7. Cluster hash tag: {auth:cache}:perm:... nếu cần multi-key   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Approaches Considered

### Approach 1: Enum-Based Category (Code-First)

```kotlin
enum class CacheKeyCategory(
    val prefix: String,
    val defaultTtl: Duration,
    val ttlJitter: Boolean,       // Anti-stampede
    val evictionStrategy: EvictionStrategy,
    val encryptionDefault: EncryptionMode,
    val compressionDefault: Boolean,
    val metricsEnabled: Boolean
) {
    DATA_CACHE(
        prefix = "cache",
        defaultTtl = Duration.ofMinutes(30),
        ttlJitter = true,          // ✅ Anti-stampede cho cache
        evictionStrategy = EvictionStrategy.EVENT_DRIVEN,
        encryptionDefault = EncryptionMode.NONE,
        compressionDefault = false,
        metricsEnabled = true
    ),
    SESSION_BOUND(
        prefix = "session",
        defaultTtl = Duration.ofHours(1),
        ttlJitter = false,         // Session TTL phải chính xác
        evictionStrategy = EvictionStrategy.EXPIRE_WITH_SESSION,
        encryptionDefault = EncryptionMode.FULL,  // Session data = sensitive
        compressionDefault = false,
        metricsEnabled = true
    ),
    RATE_COUNTER(
        prefix = "rate",
        defaultTtl = Duration.ofMinutes(1),
        ttlJitter = false,         // Rate window phải chính xác
        evictionStrategy = EvictionStrategy.AUTO_EXPIRE,
        encryptionDefault = EncryptionMode.NONE,
        compressionDefault = false,
        metricsEnabled = true
    ),
    RATE_LOCK(
        prefix = "rate",
        defaultTtl = Duration.ofMinutes(15),
        ttlJitter = false,
        evictionStrategy = EvictionStrategy.AUTO_EXPIRE,
        encryptionDefault = EncryptionMode.NONE,
        compressionDefault = false,
        metricsEnabled = true
    ),
    IDEMPOTENCY(
        prefix = "idempotent",
        defaultTtl = Duration.ofMinutes(10),
        ttlJitter = false,
        evictionStrategy = EvictionStrategy.AUTO_EXPIRE,
        encryptionDefault = EncryptionMode.NONE,
        compressionDefault = false,
        metricsEnabled = false     // Too many unique keys
    ),
    PROCESS_TOKEN(
        prefix = "process",
        defaultTtl = Duration.ofMinutes(10),
        ttlJitter = false,
        evictionStrategy = EvictionStrategy.AUTO_EXPIRE,
        encryptionDefault = EncryptionMode.FULL,  // OTP = sensitive
        compressionDefault = false,
        metricsEnabled = false
    ),
    DISTRIBUTED_LOCK(
        prefix = "lock",
        defaultTtl = Duration.ofSeconds(30),
        ttlJitter = false,
        evictionStrategy = EvictionStrategy.AUTO_RELEASE,
        encryptionDefault = EncryptionMode.NONE,
        compressionDefault = false,
        metricsEnabled = true
    ),
    CRYPTO_SESSION(
        prefix = "crypto",
        defaultTtl = Duration.ofHours(4),
        ttlJitter = false,
        evictionStrategy = EvictionStrategy.ON_KEY_ROTATE,
        encryptionDefault = EncryptionMode.FULL,  // Key material!
        compressionDefault = false,
        metricsEnabled = true
    ),
    STATIC_CONFIG(
        prefix = "config",
        defaultTtl = Duration.ofHours(24),
        ttlJitter = true,
        evictionStrategy = EvictionStrategy.EXPLICIT_EVICT,
        encryptionDefault = EncryptionMode.NONE,
        compressionDefault = false,
        metricsEnabled = true
    ),
    COMPUTED_AGGREGATE(
        prefix = "computed",
        defaultTtl = Duration.ofHours(1),
        ttlJitter = true,
        evictionStrategy = EvictionStrategy.SCHEDULED_REFRESH,
        encryptionDefault = EncryptionMode.NONE,
        compressionDefault = true,   // Aggregates can be large
        metricsEnabled = true
    ),
    WARMUP_PRELOAD(
        prefix = "warmup",
        defaultTtl = Duration.ofHours(6),
        ttlJitter = true,
        evictionStrategy = EvictionStrategy.REFRESH_BEFORE_EXPIRE,
        encryptionDefault = EncryptionMode.NONE,
        compressionDefault = true,
        metricsEnabled = true
    );
}

enum class EvictionStrategy {
    AUTO_EXPIRE,              // Redis TTL handles eviction
    EVENT_DRIVEN,             // Kafka/Pub/Sub event → evict
    EXPLICIT_EVICT,           // Manual @CacheEvict
    EXPIRE_WITH_SESSION,      // Tied to session lifecycle
    AUTO_RELEASE,             // Lock auto-release on TTL
    ON_KEY_ROTATE,            // Evict when encryption key rotates
    SCHEDULED_REFRESH,        // Cron-refresh before expire
    REFRESH_BEFORE_EXPIRE     // Async refresh when TTL < threshold
}
```

**Pros**: Type-safe, self-documenting, compile-time validation, defaults baked in
**Cons**: Rigid — thêm category mới phải thay đổi code

---

### Approach 2: YAML-Driven Category (Config-First)

```yaml
app:
  cache:
    key-taxonomy:
      categories:
        data-cache:
          prefix: "cache"
          default-ttl: 30m
          ttl-jitter: true
          jitter-percent: 10
          eviction: event-driven
          encryption: NONE
          compression: false
          metrics: true
        
        session-bound:
          prefix: "session"
          default-ttl: 1h
          ttl-jitter: false
          eviction: expire-with-session
          encryption: FULL
          compression: false
          metrics: true
        
        rate-counter:
          prefix: "rate"
          default-ttl: 1m
          ttl-jitter: false
          eviction: auto-expire
          encryption: NONE
          compression: false
          metrics: true
        
        # ... more categories
      
      # Map cache names → categories
      cache-category-map:
        permissions: data-cache
        roles: data-cache
        userSessions: session-bound
        anonymousSession: session-bound
        loginRateLimit: rate-counter
        mfaRateLimit: rate-counter
        otpCode: process-token
        idempotencyKey: idempotency
```

**Pros**: Flexible, runtime reconfigurable, no code change for new categories
**Cons**: No compile-time safety, verbose, easy to misconfigure

---

### Approach 3: HYBRID — Enum + YAML Override (SELECTED ✅)

```kotlin
// Enum provides DEFAULTS + type-safety
enum class CacheKeyCategory(val prefix: String, val defaultTtl: Duration, ...)

// YAML can OVERRIDE any default per-cache
app:
  cache:
    caches:
      permissions:
        category: DATA_CACHE         # Links to enum defaults
        ttl: 15m                     # Override default 30m
        encryption:
          mode: PARTIAL              # Override default NONE
```

**Rationale**:
- Enum = **guardrails** (defaults, documentation, compile-time reference)
- YAML = **flexibility** (override per-environment, per-cache)
- Developer luôn biết categories nào tồn tại (via enum)
- Ops có thể tune TTL/encryption per-deployment (via YAML)

---

## Deep Dive: TTL Jitter Anti-Stampede

```
┌─────────────────────────────────────────────────────────────────┐
│  PROBLEM: Cache Stampede (Thundering Herd)                      │
│                                                                 │
│  Time ─────────────────────────────────────────►                │
│                                                                 │
│  Key A  ████████████████████████████████▓  ← expire             │
│  Key B  ████████████████████████████████▓  ← expire (same TTL) │
│  Key C  ████████████████████████████████▓  ← expire             │
│                                                                 │
│  → ALL expire simultaneously → massive DB hit spike!            │
│                                                                 │
│  SOLUTION: TTL Jitter (±10%)                                    │
│                                                                 │
│  Key A  ██████████████████████████████████▓                      │
│  Key B  ████████████████████████████▓                            │
│  Key C  ████████████████████████████████████▓                    │
│                                                                 │
│  → Spread expiration → smooth DB load                           │
│                                                                 │
│  Formula: effectiveTtl = baseTtl * (1.0 - jitter/2 + random*j) │
│  Default jitter = 10% → TTL 30m → actual 27m-33m               │
└─────────────────────────────────────────────────────────────────┘
```

### Nơi NÊN dùng jitter vs KHÔNG NÊN:

| Category | Jitter? | Lý do |
|----------|:-------:|-------|
| DATA_CACHE | ✅ Yes | Nhiều entries → cần spread eviction |
| SESSION_BOUND | ❌ No | Session TTL phải chính xác (security) |
| RATE_COUNTER | ❌ No | Rate window phải chính xác |
| RATE_LOCK | ❌ No | Lockout period = security boundary |
| IDEMPOTENCY | ❌ No | Request window phải deterministic |
| PROCESS_TOKEN | ❌ No | Step timeout phải chính xác |
| DISTRIBUTED_LOCK | ❌ No | Lock TTL = safety mechanism |
| CRYPTO_SESSION | ❌ No | Key rotation TTL phải chính xác |
| STATIC_CONFIG | ✅ Yes | Spread refresh giữa nhiều config keys |
| COMPUTED_AGGREGATE | ✅ Yes | Spread recomputation |
| WARMUP_PRELOAD | ✅ Yes | Spread preload refresh |

---

## Deep Dive: Encryption Mode per Category

```
┌──────────────────────────────────────────────────────────────────────┐
│  ENCRYPTION RECOMMENDATION BY CATEGORY                               │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  FULL ENCRYPTION (sensitive data at rest):                           │
│    SESSION_BOUND   — Session tokens, user state, PII                 │
│    PROCESS_TOKEN   — OTP codes, TOTP secrets, vault tokens           │
│    CRYPTO_SESSION  — E2EE key material (MUST encrypt!)               │
│                                                                      │
│  PARTIAL ENCRYPTION (mixed sensitivity):                             │
│    DATA_CACHE      — Permission lists = OK plaintext                 │
│                      But user profile cache → encrypt PII fields     │
│                                                                      │
│  NO ENCRYPTION (no sensitive data):                                  │
│    RATE_COUNTER    — Just a number (0, 1, 2, 3...)                   │
│    RATE_LOCK       — Just a flag ("1" or absent)                     │
│    IDEMPOTENCY     — API response (already secured by HTTPS)         │
│    DISTRIBUTED_LOCK— Lock owner ID (UUID, not sensitive)             │
│    STATIC_CONFIG   — Public system configuration                     │
│    COMPUTED_AGGREGATE — Aggregated stats (no PII)                    │
│    WARMUP_PRELOAD  — Denormalized read data (depends on content)     │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Deep Dive: Key Builder API Design

### Option A: Fluent Builder

```kotlin
val key = CacheKey.builder()
    .service("auth")
    .category(CacheKeyCategory.DATA_CACHE)
    .entity("permissions")
    .qualifier("perms")
    .id(userId, domainId)
    .build()
// → "auth:cache:permissions:perms:1001:5"
```

### Option B: Template-Based (SELECTED ✅)

```kotlin
// In CacheProperties, define key template per cache
@ConfigurationProperties("app.cache")
data class CacheProperties(
    // ...existing...
    var keyTaxonomy: KeyTaxonomyProperties = KeyTaxonomyProperties()
) {
    data class KeyTaxonomyProperties(
        var servicePrefix: String = "app",       // Level 1
        var defaultJitterPercent: Int = 10,       // 10% = ±5%
        var categories: Map<String, CategoryProperties> = emptyMap()
    )
    
    data class CategoryProperties(
        var prefix: String = "",
        var defaultTtl: Duration = Duration.ofMinutes(30),
        var ttlJitter: Boolean = true,
        var jitterPercent: Int = 10,
        var eviction: String = "auto-expire",
        var encryption: EncryptionMode = EncryptionMode.NONE,
        var compression: Boolean = false,
        var metrics: Boolean = true
    )
}
```

### Implementation: CacheKeyResolver

```kotlin
/**
 * Resolves cache key format with proper prefix and category.
 * Used internally by TwoLevelCacheManager.
 */
class CacheKeyResolver(
    private val servicePrefix: String,
    private val categoryMap: Map<String, CacheKeyCategory>
) {
    /**
     * Resolve full Redis key from cache name + Spring-generated key.
     * 
     * Input:  cacheName="permissions", springKey="1001:5"
     * Output: "auth:cache:permissions::1001:5"
     */
    fun resolve(cacheName: String, springKey: Any): String {
        val category = categoryMap[cacheName] ?: CacheKeyCategory.DATA_CACHE
        return "$servicePrefix:${category.prefix}:$cacheName::$springKey"
    }
    
    /**
     * Calculate effective TTL with jitter.
     */
    fun effectiveTtl(cacheName: String): Duration {
        val category = categoryMap[cacheName] ?: CacheKeyCategory.DATA_CACHE
        val baseTtl = category.defaultTtl
        
        return if (category.ttlJitter) {
            val jitterFactor = 1.0 - category.jitterPercent / 200.0 + 
                               ThreadLocalRandom.current().nextDouble() * 
                               category.jitterPercent / 100.0
            Duration.ofMillis((baseTtl.toMillis() * jitterFactor).toLong())
        } else {
            baseTtl
        }
    }
}
```

---

## Complete YAML Config Example

```yaml
app:
  cache:
    enabled: true
    
    key-taxonomy:
      service-prefix: "auth"                  # Level 1 namespace
      default-jitter-percent: 10              # ±5% jitter
      # Category defaults (override enum defaults if needed)
      categories:
        data-cache:
          prefix: "cache"
          default-ttl: 30m
          ttl-jitter: true
          encryption: NONE
          compression: false
        session-bound:
          prefix: "session"
          default-ttl: 1h
          ttl-jitter: false
          encryption: FULL
        rate-counter:
          prefix: "rate"
          default-ttl: 1m
          ttl-jitter: false
          metrics: true
        static-config:
          prefix: "config"
          default-ttl: 24h
          ttl-jitter: true
          
    # Per-cache overrides (link to category + specific settings)
    caches:
      permissions:
        category: DATA_CACHE
        l1:
          max-size: 10000
          expire-after-write: 30s
        l2:
          ttl: 30m
        encryption:
          mode: NONE
        compression:
          enabled: false
      
      roles:
        category: DATA_CACHE
        l1:
          max-size: 10000
          expire-after-write: 30s
        l2:
          ttl: 30m
      
      systemConfig:
        category: STATIC_CONFIG
        l1:
          max-size: 100
          expire-after-write: 5m
        l2:
          ttl: 24h
        compression:
          enabled: true
          algorithm: ZSTD
          level: 9                            # High ratio for stable data

    encryption:
      enabled: true
      algorithm: AES-256-GCM
      default-mode: NONE
      key-provider: inline
      secret-key: ${CACHE_ENCRYPTION_KEY:}
    
    compression:
      enabled: false
      algorithm: ZSTD
      level: 3
      threshold: 1024
```

---

## Selected Direction

**Approach 3: HYBRID (Enum + YAML Override)** được chọn vì:

1. **Type-safe defaults**: `CacheKeyCategory` enum → mọi developer biết categories nào tồn tại
2. **Runtime flexibility**: YAML override per-environment (dev = NONE, prod = FULL encryption)
3. **Self-documenting**: Enum javadoc → tài liệu sống
4. **Backward compatible**: Default category = DATA_CACHE → existing caches không bị ảnh hưởng
5. **TTL Jitter built-in**: Chỉ apply cho data-cache/static-config (categories phù hợp)
6. **Key naming standardized**: `{service}:{category}:{cacheName}::{key}` → dễ monitor, dễ scan

## New FRs to Add (from brainstorm)

| FR | Description | Tag |
|----|-------------|-----|
| FR-023 | `CacheKeyCategory` enum với 11 categories | [IDEA] |
| FR-024 | `CacheKeyResolver` resolve key format + jitter TTL | [IDEA] |
| FR-025 | TTL Jitter anti-stampede (configurable per-category) | [IDEA] |
| FR-026 | `KeyTaxonomyProperties` trong CacheProperties | [IDEA] |
| FR-027 | Per-cache `category` field linking to CacheKeyCategory | [IDEA] |
| FR-028 | Key naming standard: `{service}:{category}:{cache}::{key}` | [IDEA] |

## Pre-classifications (preliminary)

- Feature type: EXTEND (nâng cấp base-cache-starter)
- Flow type: Command (infrastructure change)
- Affected modules:
  - `base-cache-starter` — CacheProperties, TwoLevelCacheManager, TwoLevelCache
  - `auth-service` — application.yml, PermissionChangedConsumer

## Open Questions for Design Phase

- [OPEN] TTL jitter: nên làm ở level `TwoLevelCache` (apply cho mọi put) hay ở level `TwoLevelCacheManager` (set config khi tạo cache)?
- [OPEN] `CacheKeyCategory` nên là `enum` (trong base-cache-starter) hay `sealed class` (cho phép consumer extend)?
- [RESOLVED] Key format: `{service}:{category}:{cache}::{key}` — dùng `::` giữa cache name và key để phân biệt (giống Spring Cache convention)
- [RESOLVED] Jitter formula: `baseTtl * (1.0 - jitter/200 + random * jitter/100)` — ±5% cho 10% jitter
