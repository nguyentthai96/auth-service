---
type: brainstorm_notes
change: cache-starter-migration-upgrade
date: 2026-08-25
selected_direction: "Decomposed Pipeline + Hybrid Enum/YAML Taxonomy"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Cache Starter Migration & Upgrade (Full Scope)

## Date
2026-08-25

## Context
Full-scope brainstorm for upgrading `base-cache-starter` with Encryption Engine (NONE/FULL/PARTIAL via AES-256-GCM), Compression Engine (Zstd/LZ4), Smart Serialization (type-aware pipeline), and Cache Key Taxonomy (11 categories). Additionally, complete `auth-service` migration: simplify SecurityConfig bean + improve PermissionChangedConsumer targeted eviction + add encryption config.

**Sources**:
- Research artifacts (2026-08-24): 7 artifacts validated PASS
- `pre_openspec.md`: 22 FRs (17 idea + 5 enriched), Quality 85/100
- Previous brainstorm (2026-08-21): Cache Key Taxonomy selected Hybrid Enum+YAML approach
- Codebase investigation: SecurityConfig.kt, PermissionChangedConsumer.kt, GetPermissionsHandler.kt, TokenBlacklistCacheService.kt, DatabaseMessageSource.kt

---

## Questions Asked & Answers

### Q1: Should SmartCacheSerializer be monolithic or decomposed pipeline?

The SmartCacheSerializer needs to handle 3 pipeline stages: Serialization, Compression, Encryption.

**Option A - Monolithic**: Single class (~140 LOC) handles all stages internally.
- Pros: Simple, fewer classes, less object creation
- Cons: Hard to test stages independently, SRP violation, grows with features

**Option B - Decomposed Pipeline (SELECTED)**: Separate CacheEncryptor, CacheCompressor, SmartCacheSerializer composed via constructor injection.
- Pros: Each stage testable independently, `@ConditionalOnClass` gating for compression libs, Clean Architecture, SRP, future extensibility (add new stages without touching existing code)
- Cons: More classes (~200 LOC total), slightly more complex object graph

The SmartCacheSerializer still implements `RedisSerializer<Object>` and serves as the Spring integration point. Internally it composes optional pipeline stages:

```
SmartCacheSerializer (implements RedisSerializer<Object>)
  |
  +-- serialize(value: Object): ByteArray
  |   1. typeDetect(value) -> choose serialization strategy
  |   2. CacheCompressor?.compress(bytes) -- optional
  |   3. CacheEncryptor?.encrypt(bytes) -- optional
  |   4. prepend envelope header
  |   5. return finalBytes
  |
  +-- deserialize(bytes: ByteArray): Object
      1. read envelope header -> detect format flags
      2. CacheEncryptor?.decrypt(bytes) -- if encrypted flag set
      3. CacheCompressor?.decompress(bytes) -- if compressed flag set
      4. typeDeserialize(bytes) -> Object
      5. return Object
```

Benefits of decomposition:
1. CacheCompressor gated by `@ConditionalOnClass(Zstd)` - app runs fine without zstd-jni
2. CacheEncryptor gated by `@ConditionalOnProperty(app.cache.encryption.enabled)`
3. Each stage has focused unit tests
4. Future extensibility: add new stages (e.g., schema versioning) without touching existing code
5. CacheEncryptor can be reused outside cache context (e.g., event encryption)

**A -> Decomposed Pipeline with coordinator. SmartCacheSerializer composes optional CacheCompressor + CacheEncryptor.**

---

### Q2: Should PermissionChangedConsumer use targeted eviction or keep clear()?

**Current code** (actual source, PermissionChangedConsumer.kt L30-33):
```kotlin
// For MVP, invalidate all caches for simplicity
// TODO: Parse JSON and invalidate specific user+domain
cacheManager.getCache("permissions")?.clear()
cacheManager.getCache("roles")?.clear()
```

**Option A - clear() (current)**:
- Pros: Simple, 2 lines
- Cons: Evicts ALL users' permissions (nuclear option). With 1000 active users, 1 admin change causes 1000 DB hits (stampede)

**Option B - evict(key) targeted (SELECTED)**:
- Pros: Only affected user's cache evicted, other users unaffected, no DB stampede
- Cons: Requires Kafka event to contain userId+domainId, requires key format knowledge, ~10-15 LOC more complex

**Option C - evict(key) + evict(wildcard)**:
- Pros: Handles "all permissions for domain X changed" events
- Cons: Redis KEYS/SCAN command for wildcard is expensive, not recommended for production

**A -> Option B (targeted eviction with clear() fallback)**. Parse Kafka event for userId/domainId, evict specific key. If parsing fails or scope=ALL, fallback to clear().

```kotlin
fun onPermissionChanged(message: String) {
    val event = parseEvent(message)
    if (event.userId != null && event.domainId != null) {
        val key = "${event.userId}:${event.domainId}"
        cacheManager.getCache("permissions")?.evict(key)
        cacheManager.getCache("roles")?.evict(key)
    } else {
        // scope=ALL or missing fields -> fallback to clear()
        cacheManager.getCache("permissions")?.clear()
        cacheManager.getCache("roles")?.clear()
    }
}
```

---

### Q3: Should SecurityConfig.twoLevelCacheManager() be simplified or removed?

**Current code** (actual source, SecurityConfig.kt L103-118):
```kotlin
@Bean @Primary @ConditionalOnMissingBean
fun twoLevelCacheManager(
    properties: CacheProperties,
    invalidationPublisher: ObjectProvider<CacheInvalidationPublisher>
): TwoLevelCacheManager {
    val inMemoryL1 = ConcurrentMapCacheManager()  // WRONG: should be Caffeine
    return TwoLevelCacheManager(
        l1CacheManager = inMemoryL1,
        l2CacheManager = null,        // WRONG: no L2
        invalidationPublisher = invalidationPublisher.ifAvailable,
        properties = properties
    )
}
```

**Problems identified**:
1. Uses `ConcurrentMapCacheManager` (no TTL, no maxSize) instead of CaffeineCacheManager
2. Passes `l2CacheManager = null` meaning no Redis L2 at all
3. `@ConditionalOnMissingBean` + `@Primary` blocks auto-config from base-cache-starter

**Options**:
- Option A: Fix the bean (use CaffeineCacheManager + RedisConnectionFactory)
- Option B: DELETE the bean entirely, rely on auto-configuration from base-cache-starter (SELECTED)

**A -> DELETE the bean entirely**. The `base-cache-starter`'s `CacheAutoConfiguration` is specifically designed to auto-configure `TwoLevelCacheManager` with proper Caffeine L1 + Redis L2 based on classpath + properties. The custom bean was a temporary workaround that became harmful.

---

### Q4: Should TokenBlacklistCacheService be migrated to @Cacheable?

**Current code analysis** (157 LOC):
- Custom Caffeine L1 + Redis L2 with programmatic API
- Circuit breaker for Redis failures (threshold + reset window)
- Write-through with 1 retry on failure
- Direct `StringRedisTemplate` usage (not Spring Cache)

**Feasibility assessment**:
- L1+L2 pattern: YES, TwoLevelCache supports this
- Cache key (JTI): YES, simple String key
- Circuit breaker: NO, NOT supported by Spring Cache
- Write-through with retry: NO, `@CachePut` doesn't retry
- Boolean return type: TRICKY, Spring Cache stores null differently
- Negative caching (JTI not found = valid token): TRICKY

The circuit breaker is a critical production safety feature that Spring Cache abstraction cannot replicate. This is a valid use case for programmatic cache, NOT annotation-driven cache.

**A -> KEEP TokenBlacklistCacheService as-is. Out of scope for this feature.**

---

### Q5: Should DatabaseMessageSource be migrated to @Cacheable?

**Current code analysis** (57 LOC):
- Caffeine-only cache (no L2) with 5-minute TTL, max 500 entries
- i18n messages are the same across all instances (from DB)
- No Redis L2 benefit: data rarely changes, small dataset

Caffeine-only is appropriate for i18n messages. Adding L2 Redis would add latency for no benefit. `@Cacheable` would change the method signature (need wrapper).

**A -> KEEP DatabaseMessageSource as-is. Out of scope.**

---

### Q6: How should PARTIAL mode's reflection scanning work?

**Design**:
1. Object -> Jackson serialize to `Map<String, Any?>`
2. Reflection: find `@CacheEncrypt` fields on original class
3. For each annotated String field: encrypt value -> `"ENC:" + Base64(IV + ciphertext + tag)`, replace value in Map
4. Serialize modified Map -> JSON bytes

**Key decisions**:
- **Reflection cache**: `ConcurrentHashMap<Class<*>, List<Field>>` cached after first scan. Thread-safe, lazy initialization.
- **Non-String annotated fields**: Log WARN, skip encryption. Only String fields support "ENC:" prefix encoding.
- **Null field values**: Skip (don't encrypt null).
- **Deserialization type info**: Need original class to find `@CacheEncrypt` annotations. Store class name via Jackson `@class` property or require consumer to specify return type.

**A -> ConcurrentHashMap reflection cache, skip non-String fields, skip nulls, store type info for deserialization.**

---

### Q7: Pipeline stage ordering - is serialize->compress->encrypt correct?

**Analysis**:
- Serialize FIRST: Object -> bytes (precondition for everything else)
- Compress SECOND: compression works on raw bytes. Encrypted data has high entropy -> compression ratio ~1.0x = useless
- Encrypt LAST: encrypted output goes to Redis (data at rest protection)

Reverse order (encrypt->compress) would be WRONG because encrypted data has high entropy and compression would be useless.

**Exception - PARTIAL mode**: serialize to Map -> encrypt individual fields -> re-serialize. Compression applied AFTER partial encryption (on final JSON bytes). This is OK because most of the JSON is still plaintext fields, so compression still has material to work with.

**A -> Confirmed: serialize -> compress -> encrypt for write; decrypt -> decompress -> deserialize for read.**

---

### Q8: Should envelope header be unified or per-algorithm magic bytes?

**Option A - Per-algorithm magic bytes** (from tech spec):
- Encrypted: `0xCA 0xCE 0x01` (3 bytes)
- Zstd: `0x28 0xB5 0x2F 0xFD` (4 bytes, standard)
- LZ4: `0x04 0x22 0x4D 0x18` (4 bytes, standard)
- Pros: Standard magic bytes, compatible with external tools
- Cons: Multiple detection branches on read, nested detection needed

**Option B - Unified 4-byte envelope header (SELECTED)**:
```
byte[0-1]: 0xCA 0xCE (cache magic - identifies our format)
byte[2]:   version (0x01)
byte[3]:   flags
  bit 0:   isEncrypted (0=no, 1=yes)
  bit 1:   isCompressed (0=no, 1=yes)
  bit 2-3: compressionAlgorithm (00=none, 01=zstd, 10=lz4)
  bit 4-5: encryptionMode (00=none, 01=full, 10=partial)
  bit 6-7: reserved
```
- Pros: Single detection point O(1), flags tell exactly what pipeline stages to run, version byte for future evolution, easy to add new stages
- Cons: 4 bytes overhead per entry (negligible), legacy plaintext entries need fallback detection

**Read pipeline**:
- If `bytes[0-1] == 0xCA 0xCE` -> parse header -> run pipeline stages per flags
- Else -> legacy plaintext -> direct JSON deserialize (backward compat)

**A -> Unified 4-byte envelope header with flags. O(1) detection, version support, extensible.**

---

### Q9: Should CacheKeyCategory be enum class or sealed class?

**enum class (SELECTED)**:
- Pros: Compiler-enforced exhaustive when(), all categories known at compile time, self-documenting
- Cons: Adding new category requires code change

**sealed class**:
- Pros: Consumer can extend with custom categories
- Cons: Compiler can't enforce exhaustive when(), more complex

**A -> enum class.** Platform team owns the library. Adding new category is rare (11 covers all known cases). YAML override provides enough flexibility per-cache. If consumer needs custom category, add to enum via PR.

---

### Q10: How to handle backward compatibility when rolling out encryption?

**Zero-downtime migration strategy**:
1. Deploy with `encryption.enabled=false` (default) -> no change
2. Deploy with `encryption.enabled=true`, `default-mode=NONE`, specific cache `encryption.mode=FULL`
3. Read pipeline auto-detects: magic header `0xCA 0xCE` = new format, else = legacy plaintext
4. Old entries (plaintext) read correctly via legacy path
5. New entries written in new format (with envelope header)
6. TTL naturally expires old entries -> after 1 TTL cycle, all entries use new format

**Key rotation**: Decrypt with wrong key -> `AEADBadTagException` -> graceful degradation (evict entry, reload from DB). After 1 TTL cycle, all entries use new key.

**A -> Magic byte auto-detection for backward compatibility. TTL-based natural migration. No migration script needed.**

---

## Approaches Considered

### Approach 1: Monolithic SmartCacheSerializer
- **Description**: Single class handles all pipeline stages (serialize, compress, encrypt)
- **Pros**: Simple, fewer classes, less indirection
- **Cons**: Hard to test stages independently, SRP violation, grows with features

### Approach 2: Decomposed Pipeline with Coordinator (SELECTED)
- **Description**: Separate CacheEncryptor, CacheCompressor, SmartCacheSerializer composed via constructor injection
- **Pros**: Each stage testable independently, `@ConditionalOnClass` gating, Clean Architecture, SRP, future extensibility
- **Cons**: More classes (~200 LOC total), slightly more complex object graph

### Approach 3: Spring Cache Interceptor Chain (AOP-based)
- **Description**: Use Spring AOP to intercept cache operations and apply encryption/compression
- **Pros**: Transparent to existing code
- **Cons**: AOP for serialization is unconventional, debugging difficulty, doesn't integrate well with `RedisSerializer<Object>` interface

---

## Selected Direction

**Approach 2: Decomposed Pipeline with Coordinator** combined with **Hybrid Enum+YAML Taxonomy** from previous brainstorm.

### Implementation Phasing

**Phase 0: Key Taxonomy (additive, new classes only)**
- `CacheKeyCategory` enum (11 types with defaults)
- `CacheKeyResolver` (key format + TTL jitter)
- `KeyTaxonomyProperties` in CacheProperties
- LOC: ~100. Risk: LOW.

**Phase 1: Encryption Engine (HIGH PRIORITY)**
- `CacheEncryptor` interface
- `AesGcmCacheEncryptor` (FULL + PARTIAL modes)
- `CacheEncryptionKeyProvider` (inline + keystore + custom)
- `@CacheEncrypt` annotation
- `EncryptionMode` enum
- `CacheEncryptionAutoConfiguration`
- LOC: ~200. Risk: MEDIUM (crypto code must be correct).

**Phase 2: Compression Engine**
- `CacheCompressor` (Zstd + LZ4)
- `CompressionAlgorithm` enum
- `@ConditionalOnClass` gating
- LOC: ~80. Risk: LOW (optional, graceful fallback).

**Phase 3: Smart Serialization + Pipeline**
- `SmartCacheSerializer` (implements `RedisSerializer<Object>`)
- `SerializationFormat` enum
- Wires compressor + encryptor as pipeline
- Unified 4-byte envelope header
- LOC: ~140. Risk: MEDIUM (integration point).

**Phase 4: CacheProperties + AutoConfiguration**
- Expand `CacheProperties` (encryption/compression/serialization/taxonomy sections)
- Update `CacheAutoConfiguration`
- Wire SmartCacheSerializer into TwoLevelCacheManager
- LOC: ~90. Risk: MEDIUM (changes existing classes).

**Phase 5: Auth-Service Migration**
- DELETE `SecurityConfig.twoLevelCacheManager()` bean
- MODIFY `PermissionChangedConsumer` (targeted eviction)
- ADD `app.cache` config in `application.yml`
- VERIFY `@Cacheable` on handlers works with new starter
- LOC: ~30. Risk: LOW (simplification + config).

**Phase 6: Tests + Metrics**
- 17+ test cases from tech spec
- Encryption + compression Micrometer metrics
- Integration tests with embedded Redis
- LOC: ~400. Risk: LOW.

### Data Pipeline (Write Path - Final Design)

```
Object Value
    |
    v
SmartCacheSerializer (implements RedisSerializer<Object>)
  1. Type detection: String->UTF-8, Number->ASCII, Boolean->1byte, Object->Jackson
    |
    v (raw bytes)
CacheCompressor? (optional, @ConditionalOnClass)
  if payload > 1KB: Zstd L3 or LZ4
  else: skip
    |
    v (compressed bytes or raw)
CacheEncryptor? (optional, @ConditionalOnProperty)
  NONE: pass-through
  FULL: AES-256-GCM entire blob
  PARTIAL: field-level (done earlier at serialization step)
    |
    v (encrypted bytes or raw)
Envelope Header (4 bytes)
  [0xCA][0xCE][version][flags]
    |
    v (header + payload)
Redis SET with TTL (+ jitter if applicable)
```

### Key Decisions Summary

| # | Decision | Reasoning |
|---|----------|-----------|
| 1 | Decomposed Pipeline | Each stage testable independently, conditionally loaded, SRP compliant |
| 2 | Unified 4-byte Envelope Header | O(1) format detection, version support, extensible via flag bits |
| 3 | Hybrid Enum+YAML Taxonomy | Compile-time safety from enum, runtime flexibility from YAML overrides |
| 4 | DELETE SecurityConfig bean | Custom bean is actively harmful (ConcurrentMap L1, no L2). Auto-config is correct. |
| 5 | Targeted Eviction + Fallback | Parse Kafka event for userId/domainId. If missing, fallback to clear(). |
| 6 | Keep TokenBlacklistCacheService | Circuit breaker pattern not supported by Spring Cache. Valid programmatic use case. |
| 7 | Keep DatabaseMessageSource | Caffeine-only is appropriate. No L2 benefit for i18n. |
| 8 | All Features Opt-In | Encryption/compression disabled by default. Zero behavior change for existing consumers. |
| 9 | TTL-based migration | No migration script. Magic bytes detect format. Old entries expire naturally. |

---

## Pre-classifications (preliminary)

- Feature type: **EXTEND** (upgrading existing base-cache-starter library)
- Flow type: **Command** (infrastructure change, no user-facing API changes)
- Affected modules:
  - `base-cache-starter` (PRIMARY): 12 new classes, 4 modified classes
  - `auth-service` (SECONDARY): 1 deleted bean, 1 modified consumer, config changes

## Codebase Investigation Findings

### Actual Code State (verified via grep + view_file)

| File | Current State | Action |
|------|--------------|--------|
| `SecurityConfig.kt` (L103-118) | Custom `twoLevelCacheManager()` with ConcurrentMapCacheManager L1, null L2 | **DELETE** bean entirely |
| `GetPermissionsHandler.kt` (L34) | `@Cacheable(cacheNames=["permissions"], keyGenerator="baseCacheKeyGenerator")` | **KEEP** - already correct |
| `PermissionChangedConsumer.kt` (L32-33) | `clear()` on permissions + roles caches, TODO for targeted evict | **MODIFY** - implement targeted eviction |
| `TokenBlacklistCacheService.kt` (157 LOC) | Custom Caffeine+Redis with circuit breaker | **KEEP** as-is (out of scope) |
| `DatabaseMessageSource.kt` (57 LOC) | Caffeine-only cache for i18n | **KEEP** as-is (out of scope) |
| `shared/cache/` directory | Empty (custom code already deleted) | **VERIFIED** - no action needed |
| `auth/adapter/out/cache/` directory | Empty (custom code already deleted) | **VERIFIED** - no action needed |

### Key Insights from Codebase

1. `baseCacheKeyGenerator` already available from base-cache-starter
2. Redis already configured: `spring-boot-starter-data-redis` in build.gradle.kts
3. Kafka already configured: `PermissionChangedConsumer` active with `@ConditionalOnProperty`
4. Micrometer already available: `spring-boot-starter-actuator` in dependencies

## Open Questions for Design Phase

- [OPEN] How should `SmartCacheSerializer` handle Object type lost during Redis roundtrip? Jackson needs type info. Options: store type header, use `@class` property, or require consumer to specify return type.
- [OPEN] Should envelope header logic be part of `SmartCacheSerializer` or a separate `CacheEnvelopeCodec` class?
- [OPEN] Per-cache `SmartCacheSerializer` instances: eager at startup or lazy on first use?
- [RESOLVED] TTL jitter: Apply at `TwoLevelCache` level during put() via `CacheKeyResolver.effectiveTtl()`.
- [RESOLVED] `CacheKeyCategory` as `enum` (not sealed class) - platform team owns library.
- [RESOLVED] SecurityConfig bean: DELETE entirely, rely on auto-config.
- [RESOLVED] TokenBlacklistCacheService: KEEP as-is (circuit breaker not in Spring Cache).
- [RESOLVED] DatabaseMessageSource: KEEP as-is (Caffeine-only is appropriate).
- [RESOLVED] Backward compatibility: magic byte detection, TTL-based natural migration.
- [RESOLVED] PermissionChangedConsumer: targeted evict(key) with clear() fallback.
- [RESOLVED] Pipeline ordering: serialize -> compress -> encrypt (confirmed).

## New FRs Identified (supplement to pre_openspec 22 FRs)

| FR | Description | Origin |
|----|-------------|--------|
| FR-023 | `CacheKeyCategory` enum with 11 categories + defaults | Brainstorm 2026-08-21 |
| FR-024 | `CacheKeyResolver` with key format + TTL jitter | Brainstorm 2026-08-21 |
| FR-025 | TTL Jitter anti-stampede (configurable per-category) | Brainstorm 2026-08-21 |
| FR-026 | `KeyTaxonomyProperties` in CacheProperties | Brainstorm 2026-08-21 |
| FR-027 | Per-cache `category` field linking to CacheKeyCategory | Brainstorm 2026-08-21 |
| FR-028 | Key naming standard: `{service}:{category}:{cache}::{key}` | Brainstorm 2026-08-21 |
| FR-029 | Unified 4-byte envelope header (magic + version + flags) for format detection | Brainstorm 2026-08-25 |
| FR-030 | Backward compatibility: auto-detect legacy plaintext entries | Brainstorm 2026-08-25 |
| FR-031 | Targeted eviction with clear() fallback in PermissionChangedConsumer | Brainstorm 2026-08-25 |

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| Encryption correctness (AES-GCM bug) | LOW | HIGH | Use JCA standard library. Comprehensive unit tests. |
| SecurityConfig deletion breaks startup | LOW | HIGH | base-cache-starter CacheAutoConfiguration provides @Primary TwoLevelCacheManager. Integration test. |
| Zstd JNI native lib not available | LOW | LOW | `@ConditionalOnClass` graceful fallback. |
| Key rotation breaks cached data | MEDIUM | MEDIUM | TTL-based expiry. Graceful degradation on decrypt failure. |
| SmartSerializer type detection edge cases | MEDIUM | LOW | Magic byte header protocol. Fallback to JSON. Test matrix. |
| Pipeline ordering error | LOW | MEDIUM | Hardcoded order: serialize->compress->encrypt / decrypt->decompress->deserialize. |
| Breaking existing base-cache-starter consumers | LOW | HIGH | All new features opt-in (disabled by default). No breaking changes. |
