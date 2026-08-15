---
type: brainstorm_notes
change: e2ee-performance-compliance
date: 2026-08-15
selected_direction: "Approach 1 — Implement Contracts from base-security-starter"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: E2EE Performance & Compliance

## Date
2026-08-15

## Context
Feature E2EE Performance & Compliance ban đầu được phân loại NEWBUILD tại `pre_openspec.md`.
Tuy nhiên, sau khi scan sâu vào base modules (`base-core`, `base-business`, `common-utils`, `eventsourcing-utils`),
phát hiện rằng **80%+ infrastructure đã tồn tại** trong `base-security-starter`. Điều này thay đổi hoàn toàn
hướng tiếp cận — từ NEWBUILD sang **EXTEND** (implement contracts đã có sẵn).

## 🔥 Phát Hiện Quan Trọng: Base Modules Analysis

### base-security-starter — CIPHER MODULE ĐÃ CÓ SẴN

```
base-core/starters/base-security-starter/
└── cipher/
    ├── CipherProperties.kt              ← Config đầy đủ (app.cipher.*)
    ├── config/
    │   ├── CipherCoreConfiguration.kt    ← Core bean wiring
    │   ├── CipherHttpConfiguration.kt    ← HTTP filter auto-config
    │   ├── CipherGrpcConfiguration.kt    ← gRPC interceptor auto-config
    │   ├── CipherCacheConfiguration.kt   ← TwoLevelCache for policy
    │   ├── CipherVersionConfiguration.kt ← Version check auto-config
    │   ├── CipherKeyExchangeConfiguration.kt ← Key exchange auto-config
    │   ├── CipherSecurityConfiguration.kt ← Security validators auto-config
    │   └── CipherMetricsConfiguration.kt ← Metrics auto-config
    ├── core/
    │   ├── CipherAlgorithm.kt            ← Enum: AES_GCM, CHACHA20_POLY1305
    │   ├── CipherAlgorithmFactory.kt     ← Interface: encrypt/decrypt (Tink-ready!)
    │   ├── CipherKeyResolver.kt          ← Interface: key resolution
    │   ├── CipherPolicy.kt              ← Data class: endpoint policy config
    │   ├── CipherPolicyService.kt        ← Interface: policy resolution
    │   └── CipherType.kt                ← Enum: NON_CIPHER, ENCRYPT_FULL, ENCRYPT_PARTIAL
    ├── http/
    │   ├── CipherFilter.kt              ← Full HTTP filter (272 lines!) — ENCRYPT_FULL + ENCRYPT_PARTIAL
    │   ├── CipherRequestWrapper.kt       ← Request wrapping for body decryption
    │   ├── CipherResponseWrapper.kt      ← Response wrapping for body encryption
    │   ├── DecryptRequestAdvice.kt       ← ENCRYPT_PARTIAL: field-level decrypt via @CipherFields
    │   └── EncryptResponseAdvice.kt      ← ENCRYPT_PARTIAL: field-level encrypt
    ├── grpc/
    │   └── GrpcCipherInterceptor.kt      ← gRPC interceptor (policy resolution done, processing TODO)
    ├── key/
    │   ├── CipherKeySession.kt           ← Session data class (X25519 ECDH derived keys)
    │   ├── CipherKeySessionResolver.kt   ← Interface: resolve session from request
    │   ├── CipherKeySessionRepository.kt ← Interface: CRUD for key sessions
    │   └── KeyExchangeService.kt         ← Interface: X25519 ECDH key exchange protocol
    ├── model/
    │   ├── CipherContext.kt              ← Request-scoped cipher metadata
    │   ├── CipherHeaders.kt             ← All HTTP header constants (X-Key-ID, X-Cipher-Version, etc.)
    │   ├── CipherErrorCode.kt           ← Error codes
    │   └── CipherException.kt           ← Exception hierarchy
    ├── policy/
    │   ├── CipherEndpointPolicy.kt       ← Per-endpoint policy model
    │   └── CipherPolicyCacheInvalidator.kt ← Cache invalidation
    ├── security/
    │   ├── AntiReplayValidator.kt        ← Interface: timestamp + nonce + HMAC anti-replay
    │   ├── DeviceBindingValidator.kt     ← Interface: device binding
    │   ├── DeviceRegistration.kt         ← Device registration model
    │   ├── DeviceRegistrationRepository.kt ← Interface: device CRUD
    │   └── FieldSigningService.kt        ← Interface: HMAC-SHA256 field signing
    └── version/
        ├── AppVersionChecker.kt          ← Interface: version check
        ├── AppVersionPolicy.kt           ← Version policy model
        ├── AppVersionRepository.kt       ← Interface: version CRUD
        └── UpdateStatus.kt              ← Update status enum
```

### base-business — CIPHER JPA PERSISTENCE ĐÃ CÓ

```
base-business/
└── cipher/
    ├── entity/CipherEndpointPolicyEntity.kt   ← JPA entity for cipher_endpoint_policy table
    ├── repository/CipherEndpointPolicyRepository.kt ← JPA repository
    └── service/JpaCipherPolicyService.kt      ← CipherPolicyService implementation (DB → ConcurrentHashMap cache)
```

### base-cache-starter — TwoLevelCache ĐÃ SẴN SÀNG

```
TwoLevelCache.kt (L1: Caffeine, L2: Redis) + CacheInvalidationPublisher (Pub/Sub)
→ Dùng trực tiếp cho DEK cache, policy cache, nonce dedup cache
```

### base-resilience-starter — CircuitBreaker + RateLimit ĐÃ SẴN SÀNG

```
ResilienceAutoConfiguration.kt + RateLimitFilter.kt + CircuitBreakerDefaults.kt
→ Wrap KMS calls với CircuitBreaker + Retry
```

### eventsourcing-utils — CQRS + Event Sourcing framework

```
Command/CommandHandler + Query/QueryHandler + AggregateRoot + BaseEvent
→ Dùng cho Audit Event tracking
```

### common-utils — ByteUtils, IdUtil, JsonExtension

```
ByteUtils.kt → byte manipulation
IdUtil.kt / SnowFlakeUtil.kt → unique ID generation
JsonExtension.kt → JSON utilities
StringEncryptExtensions.kt → string encryption helpers
```

## Approaches Considered

### Approach 1: Implement Contracts from base-security-starter ⭐ (SELECTED)

**Mô tả**: `base-security-starter` đã define toàn bộ interfaces và contracts. auth-service chỉ cần provide implementations cho các interfaces đó.

```
┌─────────────────────────────────────────────────────────────┐
│  base-core/base-security-starter (ĐÃ CÓ)                   │
│                                                             │
│  ┌──────────────────┐  ┌─────────────────────┐              │
│  │ CipherFilter     │  │ CipherAlgorithm     │              │
│  │ (HTTP filter)    │  │ Factory (interface)  │              │
│  │ ✅ DONE          │  │ ✅ Contract defined  │              │
│  └──────────────────┘  └─────────────────────┘              │
│  ┌──────────────────┐  ┌─────────────────────┐              │
│  │ AntiReplay       │  │ KeyExchangeService   │              │
│  │ Validator (iface)│  │ (interface)          │              │
│  │ ✅ Contract      │  │ ✅ Contract defined  │              │
│  └──────────────────┘  └─────────────────────┘              │
│  ┌──────────────────┐  ┌─────────────────────┐              │
│  │ CipherKeySession │  │ FieldSigningService  │              │
│  │ Resolver (iface) │  │ (interface)          │              │
│  │ ✅ Contract      │  │ ✅ Contract defined  │              │
│  └──────────────────┘  └─────────────────────┘              │
│  ┌──────────────────┐  ┌─────────────────────┐              │
│  │ DeviceBinding    │  │ CipherProperties     │              │
│  │ Validator (iface)│  │ (full config)        │              │
│  │ ✅ Contract      │  │ ✅ DONE              │              │
│  └──────────────────┘  └─────────────────────┘              │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  auth-service (CẦN LÀM)                                    │
│                                                             │
│  ┌──────────────────┐  ┌─────────────────────┐              │
│  │ TinkAlgorithm    │  │ X25519KeyExchange    │              │
│  │ Factory (impl)   │  │ ServiceImpl (impl)   │              │
│  │ 🔨 IMPLEMENT     │  │ 🔨 IMPLEMENT         │              │
│  └──────────────────┘  └─────────────────────┘              │
│  ┌──────────────────┐  ┌─────────────────────┐              │
│  │ RedisAntiReplay  │  │ RedisKeySession      │              │
│  │ ValidatorImpl    │  │ ResolverImpl         │              │
│  │ 🔨 IMPLEMENT     │  │ 🔨 IMPLEMENT         │              │
│  └──────────────────┘  └─────────────────────┘              │
│  ┌──────────────────┐  ┌─────────────────────┐              │
│  │ HmacFieldSigning │  │ KeySessionEntity     │              │
│  │ ServiceImpl      │  │ + JPA Repository     │              │
│  │ 🔨 IMPLEMENT     │  │ 🔨 IMPLEMENT         │              │
│  └──────────────────┘  └─────────────────────┘              │
│  ┌──────────────────┐  ┌─────────────────────┐              │
│  │ DeviceRegistra-  │  │ Audit Event (via ES) │              │
│  │ tionImpl         │  │ + DecryptionVault    │              │
│  │ 🔨 IMPLEMENT     │  │ 🔨 IMPLEMENT         │              │
│  └──────────────────┘  └─────────────────────┘              │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ application.yml → app.cipher.enabled=true            │   │
│  │                    app.cipher.key-exchange.enabled=true   │
│  │                    app.cipher.anti-replay.enabled=true    │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**Pros**:
- 80%+ infrastructure sẵn sàng (CipherFilter, DecryptRequestAdvice, EncryptResponseAdvice, CipherProperties)
- Chỉ cần implement interfaces — giảm ~70% effort so với NEWBUILD
- Consistent patterns across microservices (bất kỳ service nào cũng có thể dùng cipher)
- Auto-configuration via `@ConditionalOnMissingBean` → override linh hoạt
- TwoLevelCache sẵn sàng cho policy + key session cache
- CipherHeaders, CipherContext đã chuẩn hóa

**Cons**:
- Phải understand contracts trước khi implement
- GrpcCipherInterceptor chỉ có skeleton (TODO), cần implement message listener

### Approach 2: Build Everything from Scratch in auth-service (REJECTED)

**Mô tả**: Tự viết toàn bộ cipher middleware, filter, key exchange, etc. trong auth-service.

**Pros**:
- Full control
- Không phụ thuộc base-core updates

**Cons**:
- ❌ **Vi phạm reuse-first principle** — code đã có trong base-core
- ❌ Duplicate logic
- ❌ Không consistency giữa services
- ❌ 3-5x effort so với Approach 1
- ❌ Không tận dụng TwoLevelCache, CipherFilter, DecryptRequestAdvice

### Approach 3: Extend base-security-starter (add missing features to base-core)

**Mô tả**: Thêm Tink dependency, envelope encryption, streaming AEAD trực tiếp vào base-core.

**Pros**:
- Benefit cho tất cả downstream services

**Cons**:
- ⚠️ Scope creep — cần review + release cycle cho base-core
- ⚠️ Tink dependency ảnh hưởng tất cả services
- ⚠️ Audit Vault là domain-specific (auth-service)

## Selected Direction

**Approach 1 — Implement Contracts from base-security-starter**

### Lý do:
1. **Reuse-first**: base-core đã có **39 files** trong cipher package — tận dụng tối đa
2. **Interface-driven**: base-core define contracts (interfaces), auth-service provide implementations
3. **Configuration-driven**: `CipherProperties` đã support tất cả flags cần thiết
4. **Zero new filter code**: `CipherFilter` 272 dòng đã handle ENCRYPT_FULL + ENCRYPT_PARTIAL
5. **Zero new advice code**: `DecryptRequestAdvice` + `EncryptResponseAdvice` đã handle field-level encryption

### Classification Change: NEWBUILD → EXTEND

Lý do thay đổi:
- Pre-openspec scan chỉ scan `auth-service` → không thấy cipher code → classify NEWBUILD
- Brainstorm scan `base-core/starters/base-security-starter` → phát hiện 39 cipher files
- Đúng classification: **EXTEND** — implement contracts đã có sẵn trong base module

## Implementation Scope — What auth-service ACTUALLY Needs

### ✅ ĐÃ CÓ (base-core/base-business) — KHÔNG CẦN LÀM

| Component | File | Status |
|-----------|------|--------|
| CipherFilter (HTTP) | `CipherFilter.kt` | ✅ DONE — 272 lines, ENCRYPT_FULL + ENCRYPT_PARTIAL |
| DecryptRequestAdvice | `DecryptRequestAdvice.kt` | ✅ DONE — field-level decrypt via @CipherFields |
| EncryptResponseAdvice | `EncryptResponseAdvice.kt` | ✅ DONE — field-level encrypt |
| CipherRequestWrapper | `CipherRequestWrapper.kt` | ✅ DONE |
| CipherResponseWrapper | `CipherResponseWrapper.kt` | ✅ DONE |
| CipherPolicy model | `CipherPolicy.kt` | ✅ DONE — with signingRequired, antiReplayEnabled |
| CipherHeaders | `CipherHeaders.kt` | ✅ DONE — X-Key-ID, X-Cipher-Version, X-Timestamp, X-Nonce, etc. |
| CipherContext | `CipherContext.kt` | ✅ DONE — request-scoped metadata |
| CipherProperties | `CipherProperties.kt` | ✅ DONE — full config tree (192 lines!) |
| CipherErrorCode | `CipherErrorCode.kt` | ✅ DONE |
| CipherException | `CipherException.kt` | ✅ DONE |
| CipherType enum | `CipherType.kt` | ✅ DONE — NON_CIPHER, ENCRYPT_FULL, ENCRYPT_PARTIAL |
| CipherAlgorithm enum | `CipherAlgorithm.kt` | ✅ DONE — AES_GCM, CHACHA20_POLY1305 |
| CipherPolicyService JPA | `JpaCipherPolicyService.kt` | ✅ DONE (base-business) |
| CipherEndpointPolicyEntity | `CipherEndpointPolicyEntity.kt` | ✅ DONE (base-business) |
| TwoLevelCache | `TwoLevelCache.kt` | ✅ DONE (base-cache-starter) |
| GrpcCipherInterceptor skeleton | `GrpcCipherInterceptor.kt` | ✅ DONE (policy resolution) |
| Auto-configurations | 8 config classes | ✅ DONE |

### 🔨 CẦN IMPLEMENT (auth-service) — Interfaces cần provide implementation

| # | Interface (base-core) | Implementation cần tạo (auth-service) | Effort |
|---|----------------------|---------------------------------------|--------|
| 1 | `CipherAlgorithmFactory` | `TinkCipherAlgorithmFactory` — Google Tink AEAD | Medium |
| 2 | `KeyExchangeService` | `X25519KeyExchangeServiceImpl` — ECDH + HKDF | Large |
| 3 | `CipherKeySessionResolver` | `RedisCipherKeySessionResolver` — Redis + DB lookup | Medium |
| 4 | `CipherKeySessionRepository` | `JpaCipherKeySessionRepository` — JPA entity + repo | Small |
| 5 | `AntiReplayValidator` | `RedisAntiReplayValidator` — timestamp + nonce + HMAC | Medium |
| 6 | `FieldSigningService` | `HmacFieldSigningServiceImpl` — HMAC-SHA256 | Small |
| 7 | `DeviceBindingValidator` | `DeviceBindingValidatorImpl` — device trust | Small |
| 8 | `DeviceRegistrationRepository` | `JpaDeviceRegistrationRepository` — JPA entity + repo | Small |
| 9 | N/A (new) | `AuditEncryptionService` — encrypted audit log + Vault | Large |
| 10 | N/A (new) | `DecryptionVaultService` — break-glass procedure | Large |
| 11 | `AppVersionRepository` | `JpaAppVersionRepository` — version check CRUD | Small |
| 12 | N/A (new) | DB migration scripts (cipher_key_session, audit tables) | Medium |
| 13 | N/A (config) | `application.yml` cipher config block | Small |
| 14 | N/A (new) | `KeyExchangeController` — REST endpoint `/auth/key-exchange` | Small |
| 15 | N/A (new) | gRPC message listener (complete TODO in GrpcCipherInterceptor) | Medium |

### Effort Summary

| Category | Items | Effort |
|----------|-------|--------|
| ĐÃ CÓ (reuse) | 17+ components | **0** (free!) |
| Small implementations | 5 items | ~5 days |
| Medium implementations | 5 items | ~10 days |
| Large implementations | 3 items | ~9 days |
| **Total (auth-service only)** | **13 items** | **~24 days** |
| **Nếu NEWBUILD (build everything)** | **30+ items** | **~60-80 days** |
| **Savings from reuse** | | **~60-70%** |

## Pre-classifications (preliminary)

- Feature type: **EXTEND** (changed from NEWBUILD — base-core đã có 80%+ infrastructure)
- Flow type: **Command** (cross-cutting encryption middleware)
- Affected modules:
  - `base-security-starter` (reuse — NO modification needed)
  - `base-business` (reuse — cipher policy JPA already exists)
  - `base-cache-starter` (reuse — TwoLevelCache)
  - `base-resilience-starter` (reuse — wrap KMS calls)
  - `eventsourcing-utils` (reuse — audit events)
  - `common-utils` (reuse — ByteUtils, IdUtil)
  - `auth-service` (EXTEND — implement interfaces)

## Codebase Findings

### Key Architecture Insight: Interface-Driven Design

```
base-core defines:
  interfaces → CipherAlgorithmFactory, KeyExchangeService, AntiReplayValidator, etc.
  concrete → CipherFilter (HTTP filter), CipherProperties (config), CipherHeaders (constants)

base-business defines:
  concrete → JpaCipherPolicyService (default impl, @ConditionalOnMissingBean)

auth-service (consumer) provides:
  implementations → via @Service / @Component (Spring auto-wiring)
  overrides → via @ConditionalOnMissingBean pattern
```

### Key Discovery: CipherFilter Already Handles Everything

Từ `CipherFilter.kt` (272 lines):
- ✅ Policy resolution via `CipherPolicyService`
- ✅ ENCRYPT_FULL: full body decrypt → process → encrypt response
- ✅ ENCRYPT_PARTIAL: delegate to Advice layer via request attributes
- ✅ Anti-replay validation (optional, ObjectProvider pattern)
- ✅ Key session resolution (CipherKeySessionResolver — preferred) OR legacy CipherKeyResolver
- ✅ Device binding validation (optional)
- ✅ CipherContext propagation via request attributes
- ✅ MDC structured logging
- ✅ Magic byte idempotency detection
- ✅ Configurable exclude paths
- ✅ Error response handling

### Key Discovery: CipherProperties Already Covers ALL Config

Từ `CipherProperties.kt` (192 lines):
- `app.cipher.enabled` — master switch
- `app.cipher.http.*` — filter config (order, exclude paths)
- `app.cipher.grpc.*` — gRPC interceptor
- `app.cipher.anti-replay.*` — timestamp window, nonce TTL, Redis prefix
- `app.cipher.algorithm.*` — default algorithm
- `app.cipher.key-exchange.*` — X25519 config, session TTL, max devices
- `app.cipher.device-binding.*` — device binding
- `app.cipher.signing.*` — HMAC field signing
- `app.cipher.version-check.*` — app version check
- `app.cipher.cache.*` — L1/L2 cache config
- `app.cipher.timeout.*` — key lookup, policy lookup timeouts
- `app.cipher.retry.*` — retry config

### Missing in base-core (auth-service scope)

| Feature | Status | Where to Build |
|---------|--------|----------------|
| Tink AEAD implementation | ⚠️ Interface only | auth-service: `TinkCipherAlgorithmFactory` |
| X25519 ECDH implementation | ⚠️ Interface only | auth-service: `X25519KeyExchangeServiceImpl` |
| Redis nonce dedup | ⚠️ Interface only | auth-service: `RedisAntiReplayValidator` |
| Key session Redis+JPA | ⚠️ Interface only | auth-service: resolver + repository |
| Audit Vault | ❌ Not in base-core | auth-service: new service |
| Break-glass procedure | ❌ Not in base-core | auth-service: new service |
| gRPC message listener wrapping | ⚠️ TODO in code | auth-service or base-core |

## Open Questions for Design Phase

- [RESOLVED] Classification: NEWBUILD → EXTEND (base-core has 80%+ cipher infrastructure)
- [RESOLVED] Build vs Buy filter/middleware: REUSE (CipherFilter exists in base-core)
- [RESOLVED] Config system: REUSE (CipherProperties 192 lines in base-core)
- [RESOLVED] Cache system: REUSE (TwoLevelCache in base-cache-starter)
- [OPEN] KMS provider: AWS KMS vs GCP KMS — quyết định deploy target
- [OPEN] Tink dependency: Add to auth-service only vs add to base-core platform BOM?
- [OPEN] Audit Vault: Build trong auth-service vs tách thành standalone Vault service?
- [OPEN] gRPC streaming encryption: Implement trong auth-service vs extend GrpcCipherInterceptor trong base-core?

## Open Questions for URD Analysis

- N/A — URD analysis đã hoàn thành qua `/wf_feature_research` + `/wf_pre_openspec`
