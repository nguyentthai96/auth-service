# SRS: E2EE Performance & Compliance

## 1. Feature Overview

Enterprise E2EE middleware cho auth-service: mã hóa/giải mã request/response tự động qua `CipherFilter` (base-core), key exchange protocol X25519 ECDH, anti-replay protection Redis-backed, field-level signing HMAC-SHA256, KMS envelope encryption, encrypted audit trail với break-glass Decryption Vault. Tận dụng 80%+ infrastructure từ `base-security-starter` (39 cipher files), auth-service chỉ implement các interface contracts.

## 2. Functional Requirements

### UC-001: gRPC Streaming Encryption

#### FR-001: Mã hóa frame cho gRPC streaming [URD]
- Server phải mã hóa mỗi gRPC message/frame độc lập khi streaming data
- Mỗi frame có unique nonce = `prefix(8 bytes) || sequence_counter(4 bytes)`
- Sử dụng `CipherAlgorithmFactory.encrypt()` per frame
- **Error**: `CIPHER_STREAM_ENCRYPT_FAILED` — gRPC status `INTERNAL`
- **⚠️ DEFERRED**: P2 — implement khi gRPC dependency được thêm vào auth-service

#### FR-002: Sequence number monotonic [URD]
- Sequence number trong streaming PHẢI tăng monotonic, không skip
- Client reject nếu phát hiện gap (out-of-order detection)
- **Error**: `CIPHER_STREAM_SEQUENCE_GAP` — gRPC status `DATA_LOSS`
- **⚠️ DEFERRED**: P2

#### FR-003: Client verify thứ tự frame [URD]
- Client phải verify sequence order khi nhận streaming frames
- Frame n+1 chỉ được xử lý sau frame n
- **Scope**: Client-side requirement — server cung cấp sequence metadata
- **⚠️ DEFERRED**: P2

#### FR-004: TLS tunnel cho file lớn [URD]
- File > 10MB (configurable): chuyển sang TLS tunnel + SHA-256 integrity hash
- File ≤ 10MB: app-layer encryption bình thường
- Check via `Content-Length` header hoặc request body size
- **Error**: N/A — transparent fallback
- **⚠️ DEFERRED**: P2

### UC-002: Selective Field Encryption

#### FR-005: AAD context binding cho partial encrypt [URD]
- AAD = `"tenant:{tid}:user:{uid}:field:{path}"` cho mỗi encrypted field
- Sử dụng `CipherAlgorithmFactory.encrypt(algorithm, key, plaintext, aad)` — AAD parameter đã có trong interface
- Decrypt FAIL nếu AAD mismatch → chống cut-and-paste attack
- Supported bởi `DecryptRequestAdvice` + `EncryptResponseAdvice` (base-core)
- **Error**: `AUTH_032` (`E2EE_CONTEXT_MISMATCH`) — HTTP 403

#### FR-006: Nonce riêng cho mỗi encrypted field [URD]
- Generate random 12-byte nonce cho mỗi field khi partial encryption
- Google Tink AES-GCM handles nonce generation automatically
- Mỗi field có nonce khác nhau trong cùng request
- **Error**: N/A — Tink đảm bảo unique nonce

#### FR-007: Annotation đánh dấu field nhạy cảm [URD]
- Sử dụng `@CipherFields` annotation (base-core) trên controller method
- Annotation chỉ định field paths cần encrypt: `@CipherFields(encrypt = ["password", "secret", "card.number"])`
- `DecryptRequestAdvice` auto-decrypt annotated fields trước khi Jackson deserialize
- **Error**: N/A — transparent to business logic

#### FR-008: Partial encrypt chỉ cho JSON body [URD]
- `CipherType.ENCRYPT_PARTIAL` chỉ áp dụng cho JSON body (`application/json`)
- Headers và query params KHÔNG bị encrypt
- `CipherFilter` đã handle phân loại ENCRYPT_FULL vs ENCRYPT_PARTIAL
- **Error**: N/A

### UC-003: Time Skew & Anti-Replay

#### FR-009: Default tolerance ±5 phút [URD]
- Validate timestamp: `|X-Timestamp - server_time| ≤ 300s` (configurable via `app.cipher.anti-replay.window-seconds`)
- Sử dụng `AntiReplayValidator.validate()` interface (base-core)
- **Error**: `AUTH_030` (`E2EE_TIME_SKEW`) — HTTP 400

#### FR-010: Initial tolerance ±10 phút [URD]
- First request (no previous key session): tolerance 600s
- Subsequent requests: tolerance 300s (default)
- Detection: absence of `X-Key-ID` header = first request
- **Error**: `AUTH_030` (`E2EE_TIME_SKEW`) — HTTP 400

#### FR-011: X-Server-Time trong mọi response [URD]
- Header `X-Server-Time: {epoch_ms}` trong EVERY response
- `CipherFilter` đã inject via `CipherHeaders.X_SERVER_TIME` (base-core)
- Client dùng để tính time offset
- **Error**: N/A

#### FR-012: Moving average cho offset [URD]
- **Scope**: Client-side recommendation — server provides `X-Server-Time`
- Client nên dùng moving average (window = 5 responses) cho offset smoothing
- **Error**: N/A

#### FR-013: Nonce dedup qua Redis [URD]
- Redis `SETNX` + TTL 10 phút (configurable via `app.cipher.anti-replay.nonce-ttl-seconds`)
- Key format: `{prefix}{nonce_hash}` — prefix từ `app.cipher.anti-replay.nonce-cache-prefix`
- Duplicate nonce → reject
- **Error**: `AUTH_033` (`E2EE_REPLAY_DETECTED`) — HTTP 409

### UC-004: KMS Envelope Encryption

#### FR-014: Không lưu plaintext key [URD]
- DB chỉ lưu encrypted DEK (encrypted by master key)
- Plaintext key chỉ tồn tại in-memory (cache TTL)
- `CipherKeySessionEntity`: `clientToServerKey` + `serverToClientKey` encrypted at rest
- **Error**: N/A — design constraint

#### FR-015: Master Key trong HSM/KMS [URD]
- Master Key managed by Cloud KMS (AWS KMS / GCP Cloud KMS)
- Key material KHÔNG export được (HSM-backed)
- Provider chọn tại deployment time via config
- **Error**: N/A — infrastructure constraint

#### FR-016: DEK cache TTL 5 phút [URD]
- L1 Caffeine: `app.cipher.cache.l1-expire-after-write=5m` (đã có trong `CipherProperties`)
- L2 Redis: `app.cipher.cache.l2-ttl=30m` (đã có trong `CipherProperties`)
- Sử dụng `TwoLevelCache` (base-cache-starter)
- WIPE plaintext DEK sau khi hết TTL
- **Error**: Cache miss → call KMS → fallback to FR-018

#### FR-017: GDPR region-specific KMS [URD]
- Route key request tới KMS endpoint đúng region
- RegionResolver map `user.region` → KMS endpoint
- EU data → EU KMS, US data → US KMS
- **Error**: Unknown region → default KMS endpoint

#### FR-018: Fail-fast policy [URD]
- No cache + no KMS available = HTTP 503
- `CipherFilter` đã có error handling path
- Circuit breaker wrap KMS calls (base-resilience-starter)
- **Error**: `AUTH_036` (`E2EE_KMS_UNAVAILABLE`) — HTTP 503

#### FR-019: Key rotation zero-downtime [URD]
- Old version remains active until sunset (grace period: `app.cipher.key-exchange.rotation-grace-period`)
- New data encrypted with latest key; old data still decryptable with old key
- `CipherKeySession.keyVersion` tracks version per session
- **Error**: N/A

### UC-005: Encrypted Audit & Vault

#### FR-020: Middleware không log plaintext [URD]
- Main middleware KHÔNG log plaintext body
- Audit log chứa encrypted payload reference only
- `EncryptedAuditService` encrypt audit entry trước khi persist
- **Error**: N/A — design constraint

#### FR-021: Decryption Vault isolated [URD]
- Vault = isolated service/module, separate network segment
- **Scope**: Design specification — implementation as separate microservice (future)
- **Error**: N/A

#### FR-022: Break-glass 2 admins approve [URD]
- 4-eyes principle: requester ≠ approver
- `DecryptionVaultService.requestAccess(requesterId)` → pending
- `DecryptionVaultService.approve(approverId, requestId)` → granted
- **Error**: Self-approval → `VAULT_SELF_APPROVAL_DENIED` — HTTP 403

#### FR-023: JIT access TTL 30 phút [URD]
- Access auto-revoke after 30 minutes
- Redis TTL on access token
- **Error**: Expired access → `VAULT_ACCESS_EXPIRED` — HTTP 401

#### FR-024: Rate limit 10 decrypts/session [URD]
- Counter per vault session (Redis `INCR`)
- Max 10 decrypts per access session
- **Error**: `VAULT_RATE_EXCEEDED` — HTTP 429

#### FR-025: Immutable audit trail [URD]
- Vault access logged in `vault_access_log` table
- INSERT-only — no UPDATE/DELETE permissions on table
- **Error**: N/A

### UC-006: Cipher Version Negotiation

#### FR-026: Decode ALL active versions [URD]
- Server supports decoding all cipher versions with `is_active=true`
- `CipherVersionNegotiator` resolve version from `X-Cipher-Version` header
- **Error**: `AUTH_031` (`E2EE_VERSION_UNKNOWN`) — HTTP 400

#### FR-027: Encode response bằng latest [URD]
- Server ALWAYS encode response with latest active cipher version
- Response header `X-Cipher-Version: {latest_version}`
- **Error**: N/A

#### FR-028: Sunset timeline 3 tháng [URD]
- Deprecated version has sunset date (configurable)
- Response header `Sunset: {date}` when client uses old version
- After sunset → reject
- **Error**: `AUTH_034` (`E2EE_VERSION_SUNSET`) — HTTP 426

#### FR-029: Feature flag rollout [URD]
- Cipher version rollout: percentage-based (10% → 50% → 100%)
- Config via `application.yml` + `@RefreshScope`
- **Error**: N/A

#### FR-030: Versions config hot-reloadable [URD]
- `app.cipher.*` config via Spring Cloud Config / `@RefreshScope`
- No restart required for version changes
- **Error**: N/A

### Cross-cutting Requirements (Enriched)

#### FR-031: Idempotency cho key generation [ENRICHED]
- Same `clientPublicKey + deviceId` within session window → return existing session
- `KeyExchangeService.exchange()` checks existing session first
- **Error**: N/A — idempotent by design

#### FR-032: Full request audit logging [ENRICHED]
- Log mọi encrypt/decrypt operation: trace_id, user_id, action, timestamp
- Via `EncryptedAuditService.logOperation()`
- MDC context propagation (base-core structured logging)
- **Error**: Audit write failure → log warning, do NOT block request

#### FR-033: KMS timeout handling [ENRICHED]
- Circuit breaker: N failures → open → fast-fail → periodic health check
- Config: `app.cipher.timeout.key-lookup-ms=500` (đã có trong `CipherProperties`)
- base-resilience-starter CircuitBreaker
- **Error**: Open circuit → `AUTH_036` → HTTP 503

#### FR-034: KMS retry mechanism [ENRICHED]
- Retry max 3 lần, exponential backoff 100ms → 200ms → 400ms
- Config: `app.cipher.retry.key-session-store-max-attempts=3` (đã có trong `CipherProperties`)
- **Error**: All retries exhausted → `AUTH_036` → HTTP 503

#### FR-035: E2EE filter trong security chain [ENRICHED]
- `CipherFilter` order = 50 (configurable via `app.cipher.http.order`)
- After Spring Security (-100) → After JwtAuthFilter → CipherFilter
- Auto-configured by `CipherHttpConfiguration` (base-core)
- **Error**: N/A

## 3. Error Codes

| Code | Constant | msgCode | Description | HTTP |
|------|----------|---------|-------------|------|
| AUTH_030 | E2EE_TIME_SKEW | auth.e2ee_time_skew | Request timestamp out of tolerance window | 400 |
| AUTH_031 | E2EE_VERSION_UNKNOWN | auth.e2ee_version_unknown | Unknown cipher version | 400 |
| AUTH_032 | E2EE_CONTEXT_MISMATCH | auth.e2ee_context_mismatch | AAD context mismatch (cut-and-paste detected) | 403 |
| AUTH_033 | E2EE_REPLAY_DETECTED | auth.e2ee_replay_detected | Duplicate nonce — replay attack detected | 409 |
| AUTH_034 | E2EE_VERSION_SUNSET | auth.e2ee_version_sunset | Cipher version past sunset deadline | 426 |
| AUTH_035 | E2EE_DECRYPT_FAILED | auth.e2ee_decrypt_failed | Decryption failed (corrupted or tampered) | 500 |
| AUTH_036 | E2EE_KMS_UNAVAILABLE | auth.e2ee_kms_unavailable | KMS unavailable and no cached DEK | 503 |
| AUTH_037 | E2EE_KEY_EXPIRED | auth.e2ee_key_expired | Key session expired — re-exchange required | 401 |
| AUTH_038 | E2EE_DEVICE_UNREGISTERED | auth.e2ee_device_unregistered | Device not registered for E2EE | 403 |
| AUTH_039 | E2EE_MAX_DEVICES | auth.e2ee_max_devices | Maximum devices per user exceeded | 429 |

## 4. Non-functional Requirements

| NFR | Target | Measurement |
|-----|--------|-------------|
| Encryption latency (full body) | < 10ms | Micrometer timer `cipher.encrypt.duration` |
| Encryption latency (per field) | < 5ms | Micrometer timer `cipher.field.encrypt.duration` |
| KMS call latency (cached) | < 50ms | Micrometer timer `cipher.kms.lookup.duration` |
| KMS call latency (uncached) | < 100ms | Micrometer timer `cipher.kms.call.duration` |
| DEK cache hit ratio | > 95% | Caffeine stats via Micrometer |
| Nonce dedup latency | < 5ms | Redis SETNX latency |
| Service availability | 99.99% | DEK cache fallback + circuit breaker |
| Plaintext DEK in memory | < 5 min | Caffeine TTL `app.cipher.cache.l1-expire-after-write` |
| Key exchange latency | < 200ms | X25519 ECDH + HKDF + Redis persist |
| Anti-replay window | 5 min (configurable) | `app.cipher.anti-replay.window-seconds` |

## 5. API Endpoints

### Key Exchange

```
POST /auth/key-exchange
Content-Type: application/json

Request:
{
  "clientPublicKey": "Base64(X25519 public key, 32 bytes)",
  "deviceId": "device-uuid",
  "appVersion": "1.2.3",
  "platform": "iOS"
}

Response: 200 OK
{
  "serverPublicKey": "Base64(X25519 public key, 32 bytes)",
  "keyId": "uuid",
  "keyVersion": 1,
  "algorithm": "AES_GCM",
  "expiresAt": 1692345678000
}
```

### Cipher Headers (ALL requests after key exchange)

```
X-Key-ID: {keyId}
X-Cipher-Version: v2
X-Timestamp: {epoch_ms}
X-Nonce: {random_nonce_base64}
X-Field-Signature: {hmac_base64} (optional, for signed endpoints)
```

### Cipher Response Headers

```
X-Server-Time: {epoch_ms}
X-Cipher-Version: {latest_version}
Sunset: {date} (if client uses deprecated version)
Content-Language: {locale}
```
