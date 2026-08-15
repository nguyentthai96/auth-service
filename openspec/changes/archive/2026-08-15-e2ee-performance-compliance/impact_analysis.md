# Impact Analysis: e2ee-performance-compliance

> **Type**: EXTEND | **Flow**: Command | **Date**: 2026-08-15

---

## 1. Core Files Affected

### 1.1 Reuse from base-core (NO MODIFICATION — consume only)

| File | Package | Role | Reuse Decision |
|------|---------|------|---------------|
| [`CipherFilter.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/http/CipherFilter.kt) | `basecore.security.cipher.http` | HTTP filter — ENCRYPT_FULL + ENCRYPT_PARTIAL | **REUSE** (272 LOC, fully functional) |
| [`CipherAlgorithmFactory.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/core/CipherAlgorithmFactory.kt) | `basecore.security.cipher.core` | Interface: encrypt/decrypt | **REUSE** (implement in auth-service) |
| [`DefaultCipherAlgorithmFactory`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/config/CipherCoreConfiguration.kt#L66-L93) | `basecore.security.cipher.config` | Default impl (TODO: Tink wiring) | **EXTEND** (add Tink classpath + config) |
| [`CipherProperties.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/CipherProperties.kt) | `basecore.security.cipher` | Full config tree (192 LOC) | **REUSE** (use via `application.yml`) |
| [`DecryptRequestAdvice.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/http/DecryptRequestAdvice.kt) | `basecore.security.cipher.http` | Field-level decrypt via `@CipherFields` | **REUSE** (auto-configured) |
| [`EncryptResponseAdvice.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/http/EncryptResponseAdvice.kt) | `basecore.security.cipher.http` | Field-level encrypt | **REUSE** (auto-configured) |
| [`CipherKeySession.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/key/CipherKeySession.kt) | `basecore.security.cipher.key` | Session data class + DB schema | **REUSE** (implement JPA entity in auth-service) |
| [`KeyExchangeService.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/key/KeyExchangeService.kt) | `basecore.security.cipher.key` | X25519 ECDH contract | **REUSE** (implement in auth-service) |
| [`AntiReplayValidator.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/security/AntiReplayValidator.kt) | `basecore.security.cipher.security` | Anti-replay interface | **REUSE** (implement in auth-service) |
| [`FieldSigningService.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/security/FieldSigningService.kt) | `basecore.security.cipher.security` | HMAC-SHA256 field signing | **REUSE** (implement in auth-service) |
| [`JpaCipherPolicyService.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-business/src/main/kotlin/com/ntt/basebusiness/cipher/service/JpaCipherPolicyService.kt) | `basebusiness.cipher.service` | Policy persistence (DB → ConcurrentHashMap) | **REUSE** (auto-configured via `@ConditionalOnMissingBean`) |
| [`TwoLevelCache.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-cache-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/cache/TwoLevelCache.kt) | `basecore.cache` | L1 Caffeine + L2 Redis | **REUSE** (for DEK cache, policy cache) |
| [`CipherCoreConfiguration.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/config/CipherCoreConfiguration.kt) | `basecore.security.cipher.config` | Auto-config with `@ConditionalOnMissingBean` | **REUSE** (all 8 config classes auto-wired) |

### 1.2 Existing auth-service Files (MODIFY)

| File | Package | Role | Impact |
|------|---------|------|--------|
| [`TotpService.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/TotpService.kt) | `auth.application` | Legacy AES-GCM encryption | 🟡 Medium — migrate `encryptSecret`/`decryptSecret` to use `CipherAlgorithmFactory` |
| [`AuditLogService.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt) | `shared.audit` | Audit logging | 🟡 Medium — extend for encrypted audit entries (5+ callers) |
| [`SecurityConfig.kt`](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | `shared.config` | Security filter chain | 🟢 Low — no modification needed if `CipherFilter` auto-configured |
| `application.yml` | `resources/` | Config | 🟢 Low — add `app.cipher.*` config block |
| `build.gradle.kts` | root | Dependencies | 🟢 Low — add Tink dependency |

### 1.3 New Files (auth-service) — Implementations

| File (proposed) | Package | Implements | FR Coverage |
|----------------|---------|-----------|-------------|
| `TinkCipherAlgorithmFactory.kt` | `auth.adapter.out.cipher` | `CipherAlgorithmFactory` (override default) | FR-005, FR-006 |
| `X25519KeyExchangeServiceImpl.kt` | `auth.application.cipher` | `KeyExchangeService` | FR-014~019 |
| `RedisCipherKeySessionResolver.kt` | `auth.adapter.out.cipher` | `CipherKeySessionResolver` | FR-016 |
| `CipherKeySessionEntity.kt` | `auth.adapter.out.persistence.entity` | JPA entity for `cipher_key_session` | FR-014 |
| `CipherKeySessionJpaRepository.kt` | `auth.adapter.out.persistence.repository` | `CipherKeySessionRepository` | FR-014 |
| `RedisAntiReplayValidator.kt` | `auth.adapter.out.cipher` | `AntiReplayValidator` | FR-009~013 |
| `HmacFieldSigningServiceImpl.kt` | `auth.adapter.out.cipher` | `FieldSigningService` | FR-005 |
| `DeviceBindingValidatorImpl.kt` | `auth.adapter.out.cipher` | `DeviceBindingValidator` | N/A (optional) |
| `DeviceRegistrationEntity.kt` | `auth.adapter.out.persistence.entity` | JPA entity | N/A (optional) |
| `EncryptedAuditService.kt` | `auth.application.cipher` | NEW — encrypted audit logging | FR-020, FR-032 |
| `DecryptionVaultService.kt` | `auth.application.cipher` | NEW — break-glass procedure | FR-021~025 |
| `KeyExchangeController.kt` | `auth.adapter.in.web` | NEW — REST endpoint | FR-014 |
| `CipherVersionNegotiator.kt` | `auth.application.cipher` | NEW — version management | FR-026~030 |
| DB migration V_cipher.sql | `resources/db/migration` | DDL scripts | FR-014, FR-020 |

---

## 2. Call Tree (Key Dependencies)

```
Client Request
  → CipherFilter (base-core, auto-configured)
    → CipherPolicyService.resolvePolicy() (base-business, auto-configured)
    → AntiReplayValidator.validate() (auth-service IMPLEMENT)
    → CipherKeySessionResolver.resolve() (auth-service IMPLEMENT)
      → Redis lookup (L1 cache) OR JPA repository (L2)
    → CipherAlgorithmFactory.decrypt() (auth-service IMPLEMENT — Tink AEAD)
    → [request forwarded to controller]
  → Controller → Service → Business Logic
  → CipherFilter
    → CipherAlgorithmFactory.encrypt() (response)
    → EncryptedAuditService.logOperation() (auth-service NEW)
  → Response to Client

Key Exchange Flow (NEW):
  → KeyExchangeController.exchange()
    → KeyExchangeService.exchange() (auth-service IMPLEMENT)
      → X25519 ECDH key agreement
      → HKDF-SHA256 key derivation
      → CipherKeySessionRepository.save() (auth-service IMPLEMENT)
      → Redis cache → JPA backup
```

---

## 3. Blast Radius

### Impact Level Summary

| Level | Count | Components |
|-------|-------|-----------|
| 🟢 Low (d=1, ≤2 refs) | 8 | New implementations (no existing callers) |
| 🟡 Medium (d=1, 3-5 refs) | 2 | `AuditLogService` (5 callers), `TotpService` (2 callers) |
| 🔴 High (d=1, >5 refs) | 0 | None |

### Detail

- **AuditLogService** — 🟡 Medium (5 callers: AuthService, MfaService, MfaRateLimitService, SsoAdapter, RevokeSessionsHandler, PasswordPolicyService)
  - Action: EXTEND (add encrypted audit method) — backward compatible, existing `logAction()` unchanged
  - Risk: None — additive change only
  
- **TotpService.encryptSecret/decryptSecret** — 🟡 Medium (internal methods, called by TotpService itself)
  - Action: MIGRATE to use `CipherAlgorithmFactory` (future iteration, not blocking)
  - Risk: Low — optional refactor, existing logic works independently

- **All new implementations** — 🟢 Low
  - No existing callers — Spring auto-wiring via `@ConditionalOnMissingBean` picks up new beans
  - CipherFilter in base-core automatically uses the implementations via DI

---

## 4. Reuse Map

| Logic Block | Existing Asset | Match % | Decision | Action |
|-------------|---------------|---------|----------|--------|
| HTTP encryption filter | `CipherFilter.kt` (base-core) | **100%** | **REUSE** | Auto-configured, zero code |
| Field-level decrypt | `DecryptRequestAdvice.kt` (base-core) | **100%** | **REUSE** | Auto-configured |
| Field-level encrypt | `EncryptResponseAdvice.kt` (base-core) | **100%** | **REUSE** | Auto-configured |
| Config system | `CipherProperties.kt` (base-core) | **100%** | **REUSE** | Configure via YAML |
| Policy persistence | `JpaCipherPolicyService.kt` (base-business) | **100%** | **REUSE** | Auto-configured |
| Cache (L1+L2) | `TwoLevelCache.kt` (base-cache-starter) | **100%** | **REUSE** | Auto-configured |
| Encrypt/decrypt engine | `CipherAlgorithmFactory` interface (base-core) | **80%** | **EXTEND** | Implement with Tink AEAD |
| Key exchange | `KeyExchangeService` interface (base-core) | **80%** | **EXTEND** | Implement X25519 ECDH |
| Anti-replay | `AntiReplayValidator` interface (base-core) | **80%** | **EXTEND** | Implement Redis nonce dedup |
| Field signing | `FieldSigningService` interface (base-core) | **80%** | **EXTEND** | Implement HMAC-SHA256 |
| Key session | `CipherKeySession` model + interfaces (base-core) | **80%** | **EXTEND** | Implement JPA entity + Redis resolver |
| AES-GCM encryption | `TotpService.encryptSecret()` (auth-service) | **50%** | **EXTRACT** (future) | Migrate to `CipherAlgorithmFactory` in later phase |
| L1+L2 cache pattern | `MultiTierPermissionCache.kt` (auth-service) | **60%** | **REFERENCE** | Pattern inspiration for DEK cache (use TwoLevelCache instead) |
| Audit logging | `AuditLogService.kt` (auth-service) | **70%** | **EXTEND** | Add encrypted audit method |
| Encrypted audit vault | N/A | **0%** | **NEW** | Build new service |
| gRPC cipher | `GrpcCipherInterceptor.kt` (base-core) | **60%** | **EXTEND** (P2) | Complete TODO when gRPC added |

---

## 5. Context Snapshot

### Architecture Decision

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Crypto engine | Google Tink AEAD | Automatic nonce, KMS integration, recommended by base-core `DefaultCipherAlgorithmFactory` |
| Key exchange | X25519 ECDH + HKDF-SHA256 | Defined in base-core `KeyExchangeService` contract |
| Middleware pattern | `CipherFilter` (base-core) | 272-line filter already handles ENCRYPT_FULL + ENCRYPT_PARTIAL |
| Cache strategy | `TwoLevelCache` (base-cache-starter) | L1 Caffeine + L2 Redis, already production-ready |
| Policy persistence | `JpaCipherPolicyService` (base-business) | DB-driven with `ConcurrentHashMap` in-memory cache |
| Config system | `CipherProperties` (base-core) | 192-line config with all E2EE properties pre-defined |

### Dependency Graph

```
auth-service
  ├── base-security-starter (cipher module — 39 files)
  │   ├── CipherFilter, DecryptRequestAdvice, EncryptResponseAdvice (HTTP)
  │   ├── CipherAlgorithmFactory, CipherPolicy, CipherType (core)
  │   ├── CipherKeySession, KeyExchangeService (key)
  │   ├── AntiReplayValidator, FieldSigningService (security)
  │   └── 8 auto-configuration classes
  ├── base-business (cipher JPA — 3 files)
  │   └── JpaCipherPolicyService, CipherEndpointPolicyEntity
  ├── base-cache-starter (TwoLevelCache)
  ├── base-resilience-starter (CircuitBreaker, Retry)
  ├── common-utils (ByteUtils, IdUtil, JsonExtension)
  └── Google Tink (NEW dependency — com.google.crypto.tink:tink:1.15+)
```

### Scoping Decisions

| Feature | Scope | Phase |
|---------|-------|-------|
| HTTP cipher (ENCRYPT_FULL + PARTIAL) | ✅ In scope | Phase 1 |
| Key exchange (X25519 ECDH) | ✅ In scope | Phase 1 |
| Anti-replay (Redis nonce dedup) | ✅ In scope | Phase 1 |
| Field signing (HMAC-SHA256) | ✅ In scope | Phase 1 |
| Encrypted audit logging | ✅ In scope | Phase 2 |
| KMS envelope encryption | ✅ In scope | Phase 2 |
| Cipher version negotiation | ✅ In scope | Phase 2 |
| Decryption Vault (break-glass) | ✅ In scope (design only) | Phase 3 |
| gRPC streaming encryption | ⏳ Deferred | P2 (when gRPC added) |
| TotpService migration | ⏳ Deferred | P2 (optional refactor) |
