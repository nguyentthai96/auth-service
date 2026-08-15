<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: e2ee-performance-compliance

> **Type**: EXTEND | **Flow**: Command | **FRs**: 35 (30 URD + 5 ENRICHED)
> **Reuse**: 17+ components from base-core (CipherFilter, CipherProperties, DecryptRequestAdvice, etc.) — zero modification

## Phase 1: Foundation — Dependencies & Config

- [x] **Task 1: Add Tink dependency + config**
  - File: `build.gradle.kts` | Action: [MODIFY]
  - FR: FR-005, FR-006 — AAD context binding, per-field nonce
  - Dependencies: `com.google.crypto.tink:tink:1.15.0`, `com.google.crypto.tink:tink-awskms:1.15.0` (optional)
  - Details:
    - Add `implementation("com.google.crypto.tink:tink:1.15.0")` to dependencies
    - Add `implementation("com.google.crypto.tink:tink-awskms:1.15.0")` for AWS KMS adapter
    - Tink handles automatic nonce generation for AES-GCM

- [x] **Task 2: Add E2EE cipher config to application.yml**
  - File: `src/main/resources/application.yml` | Action: [MODIFY]
  - FR: FR-035 — E2EE filter trong security chain
  - FR: FR-016 — DEK cache TTL 5 phút
  - FR: FR-009 — Default tolerance ±5 phút
  - Pattern: Uses `CipherProperties` (base-core) — all keys pre-defined
  - Details:
    - `app.cipher.enabled: true`
    - `app.cipher.http.enabled: true`, `order: 50`
    - `app.cipher.http.exclude-paths: [/actuator/**, /auth/key-exchange, /health, /v3/api-docs/**]`
    - `app.cipher.anti-replay.enabled: true`, `window-seconds: 300`, `nonce-ttl-seconds: 600`
    - `app.cipher.key-exchange.enabled: true`, `session-ttl: 24h`, `max-devices-per-user: 5`
    - `app.cipher.signing.enabled: true`
    - `app.cipher.cache.l1-max-size: 500`, `l1-expire-after-write: 5m`, `l2-ttl: 30m`
    - `app.cipher.timeout.key-lookup-ms: 500`, `policy-lookup-ms: 200`
    - `app.cipher.retry.key-session-store-max-attempts: 3`

- [x] **Task 3: Add E2EE error codes to AuthErrorCode**
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthErrorCode.kt` | Action: [MODIFY]
  - Pattern: Enum values follow `AUTH_XXX` format, implements `ErrorCodeBase`
  - Details:
    - AUTH_030: `E2EE_TIME_SKEW` (400), msgCode: `auth.e2ee_time_skew`
    - AUTH_031: `E2EE_VERSION_UNKNOWN` (400), msgCode: `auth.e2ee_version_unknown`
    - AUTH_032: `E2EE_CONTEXT_MISMATCH` (403), msgCode: `auth.e2ee_context_mismatch`
    - AUTH_033: `E2EE_REPLAY_DETECTED` (409), msgCode: `auth.e2ee_replay_detected`
    - AUTH_034: `E2EE_VERSION_SUNSET` (426), msgCode: `auth.e2ee_version_sunset`
    - AUTH_035: `E2EE_DECRYPT_FAILED` (500), msgCode: `auth.e2ee_decrypt_failed`
    - AUTH_036: `E2EE_KMS_UNAVAILABLE` (503), msgCode: `auth.e2ee_kms_unavailable`
    - AUTH_037: `E2EE_KEY_EXPIRED` (401), msgCode: `auth.e2ee_key_expired`
    - AUTH_038: `E2EE_DEVICE_UNREGISTERED` (403), msgCode: `auth.e2ee_device_unregistered`
    - AUTH_039: `E2EE_MAX_DEVICES` (429), msgCode: `auth.e2ee_max_devices`

- [x] **Task 4: Add E2EE exception classes**
  - File: `src/main/kotlin/com/ntt/authservice/shared/exception/CipherExceptions.kt` | Action: [NEW]
  - Base: `AuthException` from `com.ntt.authservice.shared.exception`
  - Pattern: Same as `AuthCoreExceptions.kt` — each exception maps to an `AuthErrorCode`
  - Details:
    - `CipherTimeSkewException` (AUTH_030)
    - `CipherVersionUnknownException` (AUTH_031)
    - `CipherContextMismatchException` (AUTH_032)
    - `CipherReplayDetectedException` (AUTH_033)
    - `CipherVersionSunsetException` (AUTH_034)
    - `CipherDecryptFailedException` (AUTH_035)
    - `CipherKmsUnavailableException` (AUTH_036)
    - `CipherKeyExpiredException` (AUTH_037)
    - `CipherDeviceUnregisteredException` (AUTH_038)
    - `CipherMaxDevicesException` (AUTH_039)

## Phase 2: Crypto Engine — Tink AEAD

- [x] **Task 5: Implement TinkCipherAlgorithmFactory**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/TinkCipherAlgorithmFactory.kt` | Action: [NEW]
  - Base: `CipherAlgorithmFactory` from `com.ntt.basecore.autoconfigure.security.cipher.core`
  - FR: FR-005 — AAD context binding (associatedData parameter)
  - FR: FR-006 — Per-field nonce (Tink automatic nonce)
  - Pattern: `@Service` — overrides `DefaultCipherAlgorithmFactory` via `@ConditionalOnMissingBean`
  - Dependencies: `com.google.crypto.tink:tink`, `CipherAlgorithm` enum (base-core)
  - Details:
    - `init { AeadConfig.register() }` — register Tink AEAD primitives
    - `encrypt(algorithm, keyBytes, plaintext, associatedData)` → Tink AEAD
    - `decrypt(algorithm, keyBytes, ciphertext, associatedData)` → Tink AEAD
    - Support `AES_GCM` and `CHACHA20_POLY1305` via `CipherAlgorithm` enum
    - Thread-safe: Tink Aead is thread-safe

## Phase 3: Key Exchange — X25519 ECDH

- [x] **Task 6: Create DB migration for cipher_key_session**
  - File: `src/main/resources/db/migration/V__create_cipher_key_session.sql` | Action: [NEW]
  - FR: FR-014 — Không lưu plaintext key
  - Details:
    - Table `cipher_key_session`: key_id (PK), user_id, device_id, platform, algorithm_id, client_to_server_key (BYTEA), server_to_client_key (BYTEA), key_version, app_version, created_at, expires_at, is_active
    - Index: `idx_key_session_user_device(user_id, device_id)`
    - Index: `idx_key_session_active(is_active) WHERE is_active = TRUE`

- [x] **Task 7: Create CipherKeySessionEntity + JPA Repository**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/CipherKeySessionEntity.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/CipherKeySessionJpaRepository.kt` | Action: [NEW]
  - Base: `CipherKeySessionRepository` from `com.ntt.basecore.autoconfigure.security.cipher.key`
  - FR: FR-014 — Key session persistence
  - Pattern: JPA entity + Spring Data repository, same as `UserEntity` pattern
  - Dependencies: `CipherKeySession` data class (base-core) — maps 1:1

- [x] **Task 8: Implement X25519KeyExchangeServiceImpl**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/cipher/X25519KeyExchangeServiceImpl.kt` | Action: [NEW]
  - Base: `KeyExchangeService` from `com.ntt.basecore.autoconfigure.security.cipher.key`
  - FR: FR-014~019 — KMS envelope encryption
  - FR: FR-031 — Idempotency (same clientPublicKey + deviceId → return existing)
  - Pattern: `@Service`, constructor injection
  - Dependencies: `CipherKeySessionRepository`, `StringRedisTemplate`, `CipherProperties`
  - Details:
    - `exchange(request: KeyExchangeRequest): KeyExchangeResponse`
    - X25519 via `java.security.KeyPairGenerator("X25519")` (JDK 11+)
    - ECDH via `javax.crypto.KeyAgreement("X25519")`
    - HKDF-SHA256 via Tink `Hkdf.computeHkdf()`
    - Labels: `"cipher-c2s"`, `"cipher-s2c"` (match base-core contract)
    - Persist: Redis (TTL from `cipherProperties.keyExchange.sessionTtl`) + JPA (backup)
    - Zero-fill: `Arrays.fill(privateKey, 0)` + `Arrays.fill(sharedSecret, 0)`

- [x] **Task 9: Implement RedisCipherKeySessionResolver**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisCipherKeySessionResolver.kt` | Action: [NEW]
  - Base: `CipherKeySessionResolver` from `com.ntt.basecore.autoconfigure.security.cipher.key`
  - FR: FR-016 — DEK cache resolution
  - Pattern: Redis L1 (fast) → JPA L2 (backup) → back-fill Redis on JPA hit
  - Dependencies: `StringRedisTemplate`, `CipherKeySessionJpaRepository`, `ObjectMapper`
  - Details:
    - `resolve(request: HttpServletRequest): CipherKeySession?`
    - Extract `X-Key-ID` from request header (`CipherHeaders.X_KEY_ID`)
    - Redis key: `cipher:session:{keyId}`
    - Check expiry: `CipherKeySession.isExpired`

- [x] **Task 10: Create KeyExchangeController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/KeyExchangeController.kt` | Action: [NEW]
  - FR: FR-014 — Key exchange endpoint
  - Pattern: `@RestController`, same as `AuthController`
  - Dependencies: `KeyExchangeService`
  - Details:
    - `@PostMapping("/auth/key-exchange")` — excluded from CipherFilter
    - Accept `KeyExchangeRequest`, return `KeyExchangeResponse` (both from base-core)
    - `@Valid` on request body

## Phase 4: Anti-Replay & Field Signing

- [x] **Task 11: Implement RedisAntiReplayValidator**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/RedisAntiReplayValidator.kt` | Action: [NEW]
  - Base: `AntiReplayValidator` from `com.ntt.basecore.autoconfigure.security.cipher.security`
  - FR: FR-009 — Timestamp tolerance ±5min
  - FR: FR-010 — Initial tolerance ±10min
  - FR: FR-013 — Nonce dedup Redis SETNX
  - Pattern: `@Service`, constructor injection
  - Dependencies: `StringRedisTemplate`, `CipherProperties`
  - Details:
    - `validate(timestamp: String, nonce: String, keyId: String): Boolean`
    - Timestamp check: `|Long.parseLong(timestamp) - System.currentTimeMillis()| ≤ windowSeconds * 1000`
    - Nonce dedup: `redisTemplate.opsForValue().setIfAbsent(prefix + nonce, "1", nonceTtlSeconds, SECONDS)`
    - Return false if SETNX returns false (duplicate nonce)

- [x] **Task 12: Implement HmacFieldSigningServiceImpl**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/cipher/HmacFieldSigningServiceImpl.kt` | Action: [NEW]
  - Base: `FieldSigningService` from `com.ntt.basecore.autoconfigure.security.cipher.security`
  - FR: FR-005 — AAD context binding field integrity
  - Pattern: `@Service`, per-call `Mac.getInstance()` (thread-safe)
  - Details:
    - `computeSignature(signingKey, fields, timestamp, nonce, keyId): ByteArray`
    - Signing input: sorted field keys → `field1|field2|...|timestamp|nonce|keyId`
    - `verifySignature()`: constant-time comparison via `MessageDigest.isEqual()`

## Phase 5: Encrypted Audit & Vault

- [x] **Task 13: Create DB migration for audit tables**
  - File: `src/main/resources/db/migration/V__create_e2ee_audit_tables.sql` | Action: [NEW]
  - FR: FR-020 — Encrypted audit logging
  - FR: FR-025 — Immutable vault audit trail
  - Details:
    - Table `audit_log_encrypted`: id (BIGSERIAL PK), trace_id, user_id, action, encrypted_payload_ref, key_id_used, created_at
    - Table `vault_access_log`: id (BIGSERIAL PK), requester_id, approver_id, request_type, status, target_audit_id, decrypt_count, max_decrypts, expires_at, created_at, approved_at
    - Indexes per design.md

- [x] **Task 14: Implement EncryptedAuditService**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/cipher/EncryptedAuditService.kt` | Action: [NEW]
  - FR: FR-020 — Middleware không log plaintext
  - FR: FR-032 — Full request audit logging
  - Pattern: `@Service`, uses `CipherAlgorithmFactory` to encrypt before persist
  - Dependencies: `CipherAlgorithmFactory`, JPA repository, MDC context
  - Details:
    - `logOperation(traceId, userId, action, sensitivePayload?)` — encrypt payload if present
    - Store encrypted reference in `audit_log_encrypted`
    - Main middleware has NO decrypt permission

- [x] **Task 15: Extend AuditLogService with encrypted audit method**
  - File: `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt` | Action: [MODIFY]
  - FR: FR-032 — Full request audit logging
  - Pattern: Additive method — backward compatible, existing `logAction()` unchanged
  - Dependencies: `EncryptedAuditService` (optional injection via `ObjectProvider`)
  - Details:
    - Add `fun logEncryptedAction(action, userId, payload?)` — delegates to `EncryptedAuditService`
    - Existing 5 callers (AuthService, MfaService, etc.) unaffected

- [x] **Task 16: Implement DecryptionVaultService**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/cipher/DecryptionVaultService.kt` | Action: [NEW]
  - FR: FR-021~025 — Break-glass procedure
  - Pattern: `@Service`, 4-eyes principle
  - Dependencies: `VaultAccessLogEntity` (JPA), `StringRedisTemplate` (JIT TTL)
  - Details:
    - `requestAccess(requesterId): Long` — create pending request
    - `approve(approverId, requestId): VaultAccessToken` — 4-eyes check, JIT 30min TTL
    - `decrypt(accessToken, auditLogId): String` — rate limit 10/session
    - `revokeAccess(requestId)` — manual revoke

- [x] **Task 17: Create VaultAccessLogEntity + Repository**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/entity/VaultAccessLogEntity.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/repository/VaultAccessLogJpaRepository.kt` | Action: [NEW]
  - FR: FR-025 — Immutable vault audit trail

## Phase 6: Cipher Version Negotiation

- [x] **Task 18: Implement CipherVersionNegotiator**
  - File: `src/main/kotlin/com/ntt/authservice/auth/application/cipher/CipherVersionNegotiator.kt` | Action: [NEW]
  - FR: FR-026 — Decode ALL active versions
  - FR: FR-027 — Encode response bằng latest
  - FR: FR-028 — Sunset timeline 3 tháng
  - FR: FR-029 — Feature flag rollout
  - FR: FR-030 — Hot-reloadable config
  - Pattern: `@Service`, `@RefreshScope` for hot-reload
  - Dependencies: `CipherProperties`
  - Details:
    - `resolveVersion(clientVersion: String): CipherVersion` — validate, check sunset
    - `getLatestVersion(): CipherVersion` — for response encoding
    - `getSunsetHeader(clientVersion: String): String?` — `Sunset: {date}` header

## Phase 7: Integration Testing

- [x] **Task 19: Integration test — Key Exchange flow**
  - File: `src/test/kotlin/com/ntt/authservice/auth/cipher/KeyExchangeIntegrationTest.kt` | Action: [NEW]
  - Pattern: `@SpringBootTest` + `@AutoConfigureMockMvc`
  - Details:
    - Test happy path: POST /auth/key-exchange → 200 + valid response
    - Test idempotency: same clientPublicKey + deviceId → same keyId
    - Test max devices: 6th device → AUTH_039

- [x] **Task 20: Integration test — Encrypted request flow**
  - File: `src/test/kotlin/com/ntt/authservice/auth/cipher/CipherFilterIntegrationTest.kt` | Action: [NEW]
  - Pattern: `@SpringBootTest`
  - Details:
    - Test ENCRYPT_FULL: encrypted request → decrypted → processed → encrypted response
    - Test anti-replay: duplicate nonce → 409
    - Test time skew: timestamp out of tolerance → 400
    - Test expired key → 401

- [x] **Task 21: Unit test — TinkCipherAlgorithmFactory**
  - File: `src/test/kotlin/com/ntt/authservice/auth/adapter/out/cipher/TinkCipherAlgorithmFactoryTest.kt` | Action: [NEW]
  - Details:
    - Test AES-GCM encrypt → decrypt roundtrip
    - Test ChaCha20 encrypt → decrypt roundtrip
    - Test AAD mismatch → decrypt fails
    - Test different keys → decrypt fails
