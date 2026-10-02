<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Implementation Tasks: cache-starter-migration-upgrade

## Phase 0: Cache Key Taxonomy & Lifecycle
- [x] **Task 1: Enum `CacheKeyCategory`**
  - File: `com/ntt/basecore/autoconfigure/cache/taxonomy/CacheKeyCategory.kt` | Action: [NEW]
  - FR: FR-023
- [x] **Task 2: Interface `CacheKeyResolver` & TTL Jitter Logic**
  - File: `com/ntt/basecore/autoconfigure/cache/taxonomy/CacheKeyResolver.kt` | Action: [NEW]
  - FR: FR-024, FR-025
- [x] **Task 3: Cập nhật `CacheProperties`**
  - File: `com/ntt/basecore/autoconfigure/cache/CacheProperties.kt` | Action: [MODIFY]
  - FR: FR-026, FR-027, FR-028

## Phase 1: Encryption Engine
- [x] **Task 4: Enums & Annotations**
  - File: `com/ntt/basecore/autoconfigure/cache/encryption/CacheEncryptionMode.kt`, `CacheEncrypt.kt` | Action: [NEW]
  - FR: FR-003, FR-004, FR-005, FR-006
- [x] **Task 5: Strategy Key Provider**
  - File: `com/ntt/basecore/autoconfigure/cache/encryption/CacheEncryptionKeyProvider.kt`, `InlineCacheEncryptionKeyProvider.kt`, `KeyStoreCacheEncryptionKeyProvider.kt` | Action: [NEW]
  - FR: FR-007, FR-008, FR-009
- [x] **Task 6: Implementation `AesGcmCacheEncryptor`**
  - File: `com/ntt/basecore/autoconfigure/cache/encryption/AesGcmCacheEncryptor.kt` | Action: [NEW]
  - FR: FR-018

## Phase 2: Compression Engine
- [x] **Task 7: Compression Mode & Interfaces**
  - File: `com/ntt/basecore/autoconfigure/cache/compression/CacheCompressionMode.kt`, `CacheCompressor.kt` | Action: [NEW]
  - FR: FR-010, FR-011, FR-012, FR-019

## Phase 3: Smart Serialization
- [x] **Task 8: `SmartCacheSerializer` Pipeline**
  - File: `com/ntt/basecore/autoconfigure/cache/serialization/SmartCacheSerializer.kt` | Action: [NEW]
  - FR: FR-013, FR-015, FR-016

## Phase 4: Auto-Configuration & Metrics
- [x] **Task 9: Auto-Configuration Beans**
  - File: `com/ntt/basecore/autoconfigure/cache/CacheAutoConfiguration.kt`, `CacheEncryptionAutoConfiguration.kt`, `CacheCompressionAutoConfiguration.kt` | Action: [NEW/MODIFY]
  - FR: FR-014, FR-022
- [x] **Task 10: Micrometer Metrics**
  - File: `com/ntt/basecore/autoconfigure/cache/CacheMetricsRegistrar.kt` | Action: [MODIFY]
  - FR: FR-020, FR-021

## Phase 5: Auth-Service Migration
- [x] **Task 11: Xóa mã nguồn cache cũ**
  - File: `AbstractTwoTierCache.kt`, `MultiTierPermissionCache.kt`, `CaffeinePermissionCache.kt`, `InMemoryPermissionCache.kt`, `PermissionCache.kt` | Action: [DELETE]
  - FR: FR-002
- [x] **Task 12: Cập nhật Injection tại các services tiêu thụ**
  - File: `TokenGenerator.kt`, `LoginHandler.kt` | Action: [MODIFY]
- [x] **Task 13: Cập nhật Kafka Consumer `PermissionChangedConsumer`**
  - File: `PermissionChangedConsumer.kt` | Action: [MODIFY]
  - FR: FR-017
- [x] **Task 14: Áp dụng `@Cacheable`**
  - File: `GetPermissionsHandler.kt`, `GetUserRolesHandler.kt` | Action: [MODIFY]
  - FR: FR-001
