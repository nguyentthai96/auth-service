# Comparison Analysis: E2EE Performance & Compliance

> Tổng hợp so sánh và gap analysis từ Phase 2 (Open Source) + Phase 3 (Web Research) + Phase 1 (Current System).

## 1. Overall Approach Comparison

### 1.1 Per-Issue Solution Options

| Issue | Option A (Recommended) | Option B | Option C | Decision |
|-------|----------------------|----------|----------|----------|
| **#8 gRPC Streaming** | Tink Streaming AEAD | Custom frame-based (BouncyCastle) | Rely on TLS only | **Option A** — built-in, misuse-resistant |
| **#8 File >10MB** | TLS tunnel + SHA-256 integrity hash | Tink Streaming AEAD (large segments) | Custom chunked AES-GCM | **Option A** — performance, simplicity |
| **#9 Partial Encrypt** | Annotation-based + AAD | JSONPath config + AAD | Full body encrypt only | **Option A** — type-safe, compile-time |
| **#10 Time Skew** | Server-time header + offset cache | NTP sync requirement | Fixed ±10 min tolerance | **Option A** — adaptive, client-friendly |
| **#11 HSM/KMS** | Cloud KMS envelope (via Tink) | HashiCorp Vault Transit | Self-managed HSM | **Option A** — managed, low ops burden |
| **#12 Audit Vault** | Isolated Decryption Service + JIT | Vault Transit + ACL policies | Encrypted S3 + break-glass | **Option A** — tightest access control |

---

## 2. Feature Comparison Matrix

### 2.1 Encryption Library Comparison

| Feature | Tink ⭐ | Bouncy Castle | AWS Encryption SDK | Custom Implementation |
|---------|:---:|:---:|:---:|:---:|
| AES-256-GCM AEAD | ✅ | ✅ | ✅ | ✅ |
| Streaming AEAD | ✅ native | ⚠️ manual | ✅ frame-based | ⚠️ complex |
| AAD context binding | ✅ native | ✅ native | ✅ encryption context | ✅ |
| KMS Envelope | ✅ native | ❌ | ✅ native (AWS only) | ⚠️ custom |
| Key rotation | ✅ keyset | ❌ | ✅ multi-CMK | ⚠️ custom |
| Nonce management | ✅ automatic | ⚠️ manual | ✅ automatic | ⚠️ error-prone |
| Multi-cloud | ✅ AWS/GCP/Azure | N/A | ❌ AWS only | ⚠️ per provider |
| Java/Kotlin native | ✅ | ✅ | ✅ | ✅ |
| Misuse resistance | ✅ high | ⚠️ low | ✅ medium | ❌ |
| Learning curve | Medium | High | Medium | High |

### 2.2 Key Management Comparison

| Feature | Cloud KMS (via Tink) ⭐ | HashiCorp Vault | Self-managed HSM | DB plaintext |
|---------|:---:|:---:|:---:|:---:|
| Master key protection | ✅ HSM-backed | ✅ Shamir + auto-unseal | ✅ physical HSM | ❌ |
| Envelope encryption | ✅ native | ✅ Transit engine | ✅ PKCS#11 | ❌ |
| Audit trail | ✅ CloudTrail/Audit Log | ✅ built-in audit | ⚠️ varies | ❌ |
| Multi-region | ✅ automatic | ⚠️ requires setup | ⚠️ expensive | ❌ |
| Key rotation | ✅ automatic | ✅ configurable | ✅ manual | ❌ |
| Operational cost | Low (managed) | Medium (self-managed) | High | None |
| Latency | 10-50ms | 5-30ms | <5ms | 0ms |
| Availability | 99.999% (managed) | Depends on infra | Depends on hardware | DB dependent |
| **Cần cho project?** | ⭐ Must | Nice to have | Overkill | ❌ Unacceptable |

### 2.3 Audit & Forensics Comparison

| Feature | Decryption Vault ⭐ | Vault + ACL | Encrypted Cloud Storage | Plain Log + Access Control |
|---------|:---:|:---:|:---:|:---:|
| Plaintext isolation | ✅ never in main service | ⚠️ Vault has decrypt capability | ⚠️ depends on access | ❌ |
| Dual approval | ✅ JIT + 2-person rule | ⚠️ requires custom workflow | ❌ | ❌ |
| Time-limited access | ✅ auto-revoke (15-30 min) | ✅ TTL tokens | ⚠️ manual | ❌ |
| Immutable log | ✅ append-only | ✅ audit device | ⚠️ depends on storage | ⚠️ |
| Forensic search without decrypt | ⚠️ metadata only | ❌ | ❌ | ✅ but insecure |

---

## 3. Gap Analysis — Current System vs Requirements

### 3.1 Critical Gaps

| # | Gap | Current State | Required State | Severity | Effort |
|---|-----|--------------|----------------|----------|--------|
| G1 | **gRPC Streaming** | Không hỗ trợ gRPC, REST only | Frame-based encryption cho streaming | 🔴 Critical | Large |
| G2 | **Key Storage** | Plaintext key trong env var (`TOTP_ENCRYPTION_KEY`) | Encrypted DEK via KMS Envelope | 🔴 Critical | Medium |
| G3 | **Partial Encryption** | Chỉ full encrypt (TOTP secret) | Selective field encryption + AAD | 🟡 High | Large |
| G4 | **Nonce Management** | Random IV per encrypt (TotpService) | Frame-level sequence tracking + nonce | 🟡 High | Medium |
| G5 | **Time Skew** | Không có timestamp-based replay protection | Server time header + offset + nonce dedup | 🟡 High | Medium |
| G6 | **Audit Vault** | Không có audit log system | Encrypted audit + break-glass Vault | 🟡 High | Large |
| G7 | **Cipher Versioning** | Hardcoded AES/GCM/NoPadding | Versioned payload + header negotiation | 🟢 Medium | Small |
| G8 | **Multi-tenancy** | Không có tenant isolation | TenantID in AAD + per-tenant DEK | 🟢 Medium | Medium |

### 3.2 Existing Assets (có thể tái sử dụng)

| Asset | File | Reuse Potential |
|-------|------|----------------|
| AES-256-GCM pattern | `TotpService.kt` | ✅ High — extract chung `EncryptionService` |
| Security config system | `SecurityProperties.kt` | ✅ High — extend cho E2EE properties |
| Filter chain | `JwtAuthFilter.kt` + `SecurityConfig.kt` | ✅ High — add E2EE filter |
| Redis integration | `RedisConfig.kt` | ✅ High — cache DEK, nonce dedup |
| Event Sourcing utils | `eventsourcing-utils` dependency | ✅ Medium — audit event pattern |
| BouncyCastle | Test dependency | ⚠️ Medium — promote to runtime |

---

## 4. Recommendation Summary

### 4.1 Architecture Decision

```
┌────────────────────────────────────────────────────────────┐
│                    RECOMMENDED STACK                        │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  Crypto Engine:     Google Tink (primary)                  │
│                     + BouncyCastle (advanced primitives)    │
│                                                            │
│  Key Management:    Cloud KMS (AWS/GCP/Azure)              │
│                     via Tink KMS Envelope AEAD             │
│                                                            │
│  Streaming:         Tink Streaming AEAD (< 10MB)           │
│                     TLS tunnel + integrity hash (> 10MB)   │
│                                                            │
│  Partial Encrypt:   Custom middleware                      │
│                     @Sensitive annotation + AAD binding     │
│                                                            │
│  Audit:             Custom Decryption Vault service        │
│                     Break-glass with JIT + dual approval   │
│                                                            │
│  Versioning:        X-Cipher-Version header                │
│                     Server-side multi-version support      │
│                                                            │
│  Time Sync:         X-Server-Time response header          │
│                     Client offset calculation              │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 4.2 Build vs Buy Decision

| Component | Decision | Reasoning |
|-----------|----------|-----------|
| Crypto primitives | **Buy** (Tink) | Misuse-resistant, audited, KMS-integrated |
| Streaming encryption | **Buy** (Tink Streaming AEAD) | Complex to build correctly |
| KMS integration | **Buy** (Cloud KMS) | Managed, compliant, auditable |
| Partial encryption middleware | **Build** | Domain-specific, annotation-based |
| Time skew handling | **Build** | Simple, project-specific |
| Audit Decryption Vault | **Build** | Security-sensitive, custom access control |
| Cipher version negotiation | **Build** | Simple header/config mapping |
| Multi-tenant key isolation | **Build** (on top of Tink) | Domain-specific AAD context |

### 4.3 Priority Matrix

| Priority | Item | Reasoning |
|----------|------|-----------|
| P0 (Critical) | KMS Envelope Encryption (G2) | Plaintext keys in env var = immediate security risk |
| P0 (Critical) | Nonce Management (G4) | Nonce reuse → catastrophic failure |
| P1 (High) | Partial Encryption + AAD (G3) | Prevents cut-and-paste attacks |
| P1 (High) | Time Skew + Replay Protection (G5) | Anti-replay mechanism |
| P1 (High) | Audit Vault (G6) | Compliance requirement |
| P2 (Medium) | gRPC Streaming (G1) | Only needed when gRPC is added |
| P2 (Medium) | Cipher Versioning (G7) | Needed before first algorithm change |
| P3 (Low) | Multi-tenancy (G8) | Only if SaaS model confirmed |

---

## 5. Checklist Decisions

### Decision Matrix

| # | Question | Decision | Reasoning |
|---|----------|----------|-----------|
| 1 | Tolerate hay Reject? | **Fail-fast (Reject)** | Không giải mã được = không phục vụ. Circuit breaker + DEK cache (5 min) giảm KMS dependency |
| 2 | Stateless hay Stateful? | **Stateless (JWT)** | E2EE metadata (key_id, cipher_version) embedded trong JWT claims. Không cần Redis cho E2EE session |
| 3 | Multi-tenancy? | **Có — TenantID trong AAD** | Per-tenant DEK + TenantID in encryption context. Cross-tenant decrypt sẽ FAIL ở KMS level |
| 4 | CI/CD Refresh? | **Versioned Payload** | `X-Cipher-Version: v1` header. Server hỗ trợ decode multi-version, chỉ encode newest. Sunset sau 3 tháng |

---

> **Next**: Phase 5 (Business Analysis)
