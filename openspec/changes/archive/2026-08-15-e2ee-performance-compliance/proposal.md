## Why

Hệ thống auth-service hiện chỉ có encryption cơ bản trong `TotpService.kt` (AES-256-GCM với plaintext key từ env var) — không đáp ứng enterprise E2EE requirements: không có key exchange protocol, không anti-replay protection, không field-level encryption, không KMS integration, không encrypted audit trail. Cần nâng cấp lên full E2EE middleware tận dụng infrastructure đã sẵn sàng trong `base-security-starter` (39 cipher files, 17+ components, 8 auto-configuration classes).

## What Changes

- **Tink AEAD integration**: Implement `CipherAlgorithmFactory` contract (base-core) bằng Google Tink — AES-256-GCM + ChaCha20-Poly1305, automatic nonce management
- **X25519 ECDH key exchange**: Implement `KeyExchangeService` contract (base-core) — client/server key pair, HKDF-SHA256 key derivation, session management
- **Anti-replay protection**: Implement `AntiReplayValidator` contract (base-core) — Redis SETNX nonce dedup + timestamp tolerance ±5min
- **Field signing**: Implement `FieldSigningService` contract (base-core) — HMAC-SHA256 field integrity cho critical endpoints
- **Key session persistence**: Implement `CipherKeySessionResolver` + `CipherKeySessionRepository` contracts (base-core) — Redis L1 + JPA L2
- **Encrypted audit logging**: Extend `AuditLogService` — encrypted audit entries, compliance trail
- **KMS envelope encryption**: DEK cache (TwoLevelCache from base-cache-starter) + Cloud KMS master key
- **Cipher version negotiation**: Runtime config, sunset timeline, feature flag rollout
- **Decryption Vault**: Break-glass procedure — 4-eyes principle, JIT access 30min, rate limit 10/session
- **Configuration**: `app.cipher.*` config block via `CipherProperties` (base-core, 192 LOC đã sẵn sàng)

## Capabilities

### New Capabilities
- `e2ee-key-exchange`: X25519 ECDH key exchange endpoint `/auth/key-exchange` — client sends public key, server derives shared symmetric keys, persists session
- `e2ee-anti-replay`: Redis-backed nonce dedup (SETNX + TTL 10min) + timestamp tolerance validation + HMAC binding
- `e2ee-field-signing`: HMAC-SHA256 field-level integrity for payment/transfer endpoints — AAD context binding (TenantID + UserID + FieldPath)
- `e2ee-encrypted-audit`: Encrypted audit log entries — main service has NO decrypt permission, immutable trail
- `e2ee-decryption-vault`: Isolated break-glass procedure — 2 admins approve, JIT 30min access, rate limit 10 decrypts/session
- `e2ee-version-negotiation`: Server decodes ALL active cipher versions, ALWAYS encodes response with latest version, 3-month sunset timeline
- `e2ee-kms-envelope`: Cloud KMS envelope encryption — master key in HSM, DEK cache L1 Caffeine 5min + L2 Redis 30min

### Modified Capabilities
- `cipher-middleware`: CipherFilter (base-core) activated via `app.cipher.enabled=true` — handles ENCRYPT_FULL + ENCRYPT_PARTIAL
- `partial-encryption`: DecryptRequestAdvice/EncryptResponseAdvice (base-core) activated via `@CipherFields` annotation on controller methods
- `audit-logging`: AuditLogService extended with encrypted audit method (backward compatible)

## Impact

### Backend (auth-service) — IMPLEMENT CONTRACTS

**NEW files** (13):
- `TinkCipherAlgorithmFactory.kt` — `CipherAlgorithmFactory` impl (Tink AEAD)
- `X25519KeyExchangeServiceImpl.kt` — `KeyExchangeService` impl (ECDH + HKDF)
- `RedisCipherKeySessionResolver.kt` — `CipherKeySessionResolver` impl (Redis + JPA)
- `CipherKeySessionEntity.kt` + `CipherKeySessionJpaRepository.kt` — key session persistence
- `RedisAntiReplayValidator.kt` — `AntiReplayValidator` impl (Redis nonce dedup)
- `HmacFieldSigningServiceImpl.kt` — `FieldSigningService` impl (HMAC-SHA256)
- `EncryptedAuditService.kt` — encrypted audit logging
- `DecryptionVaultService.kt` — break-glass procedure
- `KeyExchangeController.kt` — REST endpoint `/auth/key-exchange`
- `CipherVersionNegotiator.kt` — version management
- DB migration scripts (2 tables: `cipher_key_session`, `audit_log_encrypted`)

**MODIFY files** (3):
- `application.yml` — add `app.cipher.*` config block
- `build.gradle.kts` — add `com.google.crypto.tink:tink:1.15+` dependency
- `AuditLogService.kt` — extend with encrypted audit method (backward compatible)

### Backend (base-core) — NO MODIFICATION
- base-security-starter: 39 files reused as-is (CipherFilter, CipherProperties, auto-configurations)
- base-business: JpaCipherPolicyService reused as-is
- base-cache-starter: TwoLevelCache reused for DEK/policy cache

### Database
- **NEW**: `cipher_key_session` table (key_id, user_id, device_id, platform, algorithm_id, keys, version, timestamps)
- **NEW**: `cipher_endpoint_policy` table (if not existing — may already exist from base-business)
- **NEW**: `audit_log_encrypted` table (id, trace_id, user_id, action, encrypted_payload_ref, timestamp)
- **NEW**: `vault_access_log` table (id, requester_id, approver_id, access_type, expires_at, created_at)
