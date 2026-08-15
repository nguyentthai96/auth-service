# Web Research: E2EE Performance & Compliance

> Kết quả research internet cho 5 vấn đề hiệu năng/compliance + 4 câu hỏi checklist kiến trúc.

## 1. Tổng quan

| Mục | Nội dung |
|-----|----------|
| **Feature** | E2EE Performance & Compliance |
| **Ngày research** | 2026-08-15 |
| **Số search iterations** | 4 |
| **Số unique sources** | 15+ |
| **Topics covered** | 9 (5 issues + 4 checklist) |

---

## 2. Research Findings

### 2.1 Issue #8: gRPC Streaming & Frame-based Encryption

#### Key Findings

**Finding 1: Per-Message Nonce Generation là bắt buộc**
- **Source**: Web search — gRPC streaming encryption best practices
- **Relevance**: 10/10
- **Insight**: Mỗi message trong gRPC stream PHẢI có unique nonce. Reuse nonce với cùng key trong AES-GCM → **catastrophic failure** (leak plaintext XOR).
- **Pattern**: `Transmission_Format = Nonce (12 bytes) || Encrypted_Payload || Auth_Tag`

**Finding 2: Frame Structure cho Streaming AEAD**
- **Source**: Google Tink Streaming AEAD documentation
- **Relevance**: 9/10
- **Insight**: Tink Streaming AEAD chia data thành segments (frames), mỗi frame có:
  - Unique nonce = `prefix || segment_counter (4 bytes) || is_last_segment (1 bit)`
  - Fixed frame size (default 4096 bytes)
  - Sequence number tự tăng → chống reorder attack
- **Pros**: Automatic nonce management, streaming decryption, random access possible
- **Cons**: Tink-specific format, không tương thích standard gRPC framing

**Finding 3: Tunnel Encryption cho File Lớn (>10MB)**
- **Source**: Web search — large file encryption streaming vs application layer
- **Relevance**: 9/10
- **Insight**: Với file >10MB, application-layer encryption gây bottleneck do:
  - CPU overhead per chunk
  - Memory buffering requirements
  - Không tận dụng được hardware acceleration (AES-NI) hiệu quả
- **Recommendation**: Dựa vào TLS 1.3 cho transport, chỉ ký SHA-256 hash (checksum) của toàn bộ file để verify integrity.
- **Threshold**: 10MB là ngưỡng hợp lý — dưới 10MB: app-layer encrypt từng frame; trên 10MB: TLS tunnel + integrity hash

**Design Decision — Frame Structure:**
```
┌─────────────────────────────────────────────────┐
│ Encrypted Frame (per gRPC message)              │
├─────────────────────────────────────────────────┤
│ Version (1 byte)  │ Flags (1 byte)              │
│ Sequence Number (4 bytes, big-endian)           │
│ Nonce (12 bytes, random per frame)              │
│ Encrypted Payload (variable)                    │
│ Auth Tag (16 bytes, AES-GCM)                    │
│ Frame HMAC (32 bytes, optional for chaining)    │
└─────────────────────────────────────────────────┘

Flags:
  bit 0: is_last_frame
  bit 1: is_compressed
  bit 2-7: reserved
```

---

### 2.2 Issue #9: Partial Encryption Optimization

#### Key Findings

**Finding 1: AAD Context Binding Pattern**
- **Source**: Web search — AES-256-GCM partial field encryption AAD context binding
- **Relevance**: 10/10
- **Insight**: Mỗi field được mã hóa **độc lập** với AAD chứa context:
  ```
  AAD = "tenant:{tenantId}:user:{userId}:field:{fieldPath}"
  ```
  → Nếu copy encrypted field từ user A sang user B, decryption FAIL vì AAD mismatch.
- **Pattern**: "Encrypted Field Wrapper"
  ```json
  {
    "ssn": {
      "v": 1,
      "alg": "AES-256-GCM", 
      "keyId": "key-v1",
      "iv": "base64...",
      "tag": "base64...",
      "ct": "base64..."
    }
  }
  ```

**Finding 2: Performance Optimization cho JSON Path Matching**
- **Source**: Multiple articles on selective field encryption
- **Relevance**: 8/10
- **Insight**: 
  - **Pre-compiled path matching**: Cache compiled JSON path expressions → amortize parsing cost
  - **Struct Tags / Annotations**: Đánh dấu `@Sensitive` trên DTO fields → compile-time path resolution
  - **Streaming JSON parser** (Jackson `JsonParser`): Parse JSON token-by-token, chỉ encrypt khi gặp marked field → O(n) thay vì full parse + re-serialize
  - **Benchmark**: Partial encryption ~3-5× slower than full body (not 10×) nếu dùng streaming parser

**Finding 3: Masking JSON Path Convention**
- **Source**: JSON path masking patterns in enterprise security
- **Relevance**: 8/10
- **Insight**: Quy ước đánh dấu field nhạy cảm:
  - Proto: `option (sensitive) = true;` hoặc custom annotation
  - JSON/DTO: `@field:Sensitive(level = SensitivityLevel.PII)` Kotlin annotation
  - Config: `$.user.ssn`, `$.payment.card_number` — JSONPath expressions
  - Annotation-based preferred cho type safety + compile-time validation

---

### 2.3 Issue #10: Client Time Skew

#### Key Findings

**Finding 1: Kerberos Model — ±5 phút tolerance là industry standard**
- **Source**: Web search — client server time skew replay prevention
- **Relevance**: 10/10
- **Insight**: 
  - Kerberos (Windows domain) dùng ±5 phút tolerance
  - AWS API Gateway dùng ±5 phút cho request signing
  - ⚠️ Mobile devices có thể lệch >5 phút nếu user set manual time
  - **Recommendation**: ±5 phút là hợp lý, kết hợp server-time response cho offset correction

**Finding 2: Server Time Offset Mechanism**
- **Source**: NTP synchronization patterns, Google's TrueTime
- **Relevance**: 9/10
- **Insight**:
  ```
  Server Response Header:
    X-Server-Time: 1723708539000 (epoch ms)
  
  Client calculates:
    offset = server_time - client_time
  
  Next request:
    adjusted_timestamp = client_time + offset
  ```
  - Client cần cache offset, re-calculate mỗi response
  - Offset nên smooth (moving average) để tránh jitter
  - First request: server nên chấp nhận wider tolerance (±10 phút) cho initial sync

**Finding 3: Anti-Replay kết hợp Nonce + Timestamp**
- **Source**: Multiple security protocol designs
- **Relevance**: 9/10
- **Insight**: Timestamp alone không đủ — cần kết hợp:
  1. **Timestamp**: Chống replay sau window (±5 min)
  2. **Nonce (UUID)**: Chống replay TRONG window — server cache nonce 10 min, reject duplicate
  3. **Sequence number**: Chống reorder (streaming only)
  - Redis SET with TTL 10 min cho nonce dedup

---

### 2.4 Issue #11: Data Residency & HSM/KMS Integration

#### Key Findings

**Finding 1: Envelope Encryption Workflow**
- **Source**: Web search — HSM KMS envelope encryption Spring Boot
- **Relevance**: 10/10
- **Insight**: Standard envelope encryption workflow:
  ```
  1. Server → KMS: GenerateDataKey(CMK_ARN, context={tenantId: "T1"})
  2. KMS → Server: {plaintext_DEK, encrypted_DEK}
  3. Server: encrypt(data, plaintext_DEK) → ciphertext
  4. Server: WIPE plaintext_DEK from memory
  5. Server → DB: store(ciphertext, encrypted_DEK, key_version)
  
  Decrypt:
  1. Server → DB: read(ciphertext, encrypted_DEK)
  2. Server → KMS: Decrypt(encrypted_DEK, context={tenantId: "T1"})
  3. KMS → Server: plaintext_DEK
  4. Server: decrypt(ciphertext, plaintext_DEK) → data
  5. Server: WIPE plaintext_DEK
  ```

**Finding 2: Cloud KMS Options cho Spring Boot**
- **Source**: Official SDK docs (AWS, GCP, Azure)
- **Relevance**: 9/10
- **Insight**:
  | Cloud | Service | SDK | Latency | Cost |
  |-------|---------|-----|---------|------|
  | AWS | AWS KMS | `software.amazon.awssdk:kms` | ~10-50ms | $1/key/month + $0.03/10K API calls |
  | GCP | Cloud KMS | `com.google.cloud:google-cloud-kms` | ~10-30ms | $0.06/key/month + $0.03/10K calls |
  | Azure | Key Vault | `com.azure:azure-security-keyvault-keys` | ~20-60ms | $0.03/10K calls |
  - Tink có native integration cho cả 3: `TinkAwsKms`, `TinkGcpKms`, `TinkAzureKms`

**Finding 3: GDPR Data Residency — Region-specific Master Key**
- **Source**: GDPR compliance documentation
- **Relevance**: 10/10
- **Insight**:
  - EU data → EU master key (stored in eu-west-1 / europe-west1)
  - US data → US master key (stored in us-east-1 / us-central1)
  - Key PHẢI nằm cùng region với data
  - Cross-region key access → audit log + compliance alert
  - **Implementation**: `RegionResolver` service → map user.region → KMS endpoint

---

### 2.5 Issue #12: Audit Log & Decryption Vault

#### Key Findings

**Finding 1: Break-Glass Procedure Architecture**
- **Source**: Web search — audit decryption vault break-glass
- **Relevance**: 10/10
- **Insight**: 
  - Audit log format: `{encrypted_payload, key_id, trace_id, timestamp, user_id, action}`
  - **KHÔNG** log plaintext — middleware chính KHÔNG CÓ QUYỀN decrypt log
  - Break-glass = **JIT (Just-in-Time) access** + **dual approval**

**Finding 2: Decryption Vault Service Architecture**
- **Source**: PAM (Privileged Access Management) patterns
- **Relevance**: 9/10
- **Insight**:
  ```
  ┌─────────────┐     ┌──────────────┐     ┌─────────────┐
  │ Security     │────→│ Approval     │────→│ Decryption  │
  │ Admin A      │     │ Service      │     │ Vault       │
  │ (request)    │     │ (2-person)   │     │ (isolated)  │
  └─────────────┘     │              │     │             │
  ┌─────────────┐     │ ✅ Admin A    │     │ ↓ decrypt   │
  │ Security     │────→│ ✅ Admin B    │     │ ↓ return    │
  │ Admin B      │     │ → grant JIT  │     │ ↓ audit     │
  │ (approve)    │     └──────────────┘     └─────────────┘
  └─────────────┘
  ```
  - JIT access: 15-30 phút, auto-revoke
  - All access logged in separate immutable audit trail
  - Vault isolated network segment

**Finding 3: FA-SEAL — Forensic Analysis without Full Decryption**
- **Source**: NSF research paper on forensically analyzable encryption
- **Relevance**: 7/10
- **Insight**: Emerging technique: cho phép search/filter encrypted logs bằng metadata (timestamp, user_id, action type) mà KHÔNG cần decrypt payload. Giảm thiểu exposure khi điều tra.

---

### 2.6 Checklist: Fail-fast vs Fail-open

#### Key Findings

**Finding**: **Fail-fast là bắt buộc** cho encryption middleware
- **Source**: Security engineering best practices, OWASP
- **Relevance**: 10/10
- **Insight**: 
  - Fail-open trong encryption = **bypass encryption entirely** → unacceptable
  - Nếu KMS unreachable → reject ALL requests, return HTTP 503 Service Unavailable
  - Circuit breaker pattern: sau N failures → open circuit → fast-fail → periodic health check
  - Cache DEK trong memory (TTL 5 min) để giảm KMS dependency
  - ⚠️ Avalanche risk: nếu tất cả service instances mất KMS cùng lúc → total outage
  - **Mitigation**: DEK cache + fallback to local HSM + alarm + graceful degradation strategy

---

### 2.7 Checklist: Stateless vs Stateful

#### Key Findings

**Finding**: **Stateless (JWT self-contained)** là preferred
- **Source**: JWT architecture patterns, API security standards
- **Relevance**: 9/10
- **Insight**:
  - Stateless: E2EE key metadata (key_id, cipher_version) embedded trong JWT claims
  - Không cần Redis cho E2EE session state
  - Nhưng: key rotation phải sync qua JWT → short-lived tokens (15 min) giúp rotate nhanh
  - If Stateful needed (VD: long-lived WebSocket): Session DB riêng biệt với TTL

---

### 2.8 Checklist: Multi-tenancy Key Isolation

#### Key Findings

**Finding**: **TenantID PHẢI có trong AAD context**
- **Source**: Web search — multi-tenant encryption key isolation SaaS
- **Relevance**: 10/10
- **Insight**:
  - Mỗi tenant → unique DEK (hoặc unique KEK → shared DEK pattern)
  - AAD context: `{tenantId: "T1", userId: "U1", resourceType: "payment"}`
  - KMS encryption context bao gồm TenantID → cross-tenant decrypt FAIL
  - Per-tenant KMS key: tốt hơn nhưng expensive ($$$ per key/month)
  - **Recommended**: Shared KEK + per-tenant DEK + TenantID in AAD

---

### 2.9 Checklist: CI/CD Cipher Version Refresh

#### Key Findings

**Finding**: **Versioned Payload + Header-based negotiation**
- **Source**: Web search — E2EE cipher version negotiation backward compatibility
- **Relevance**: 10/10
- **Insight**:
  - Request header: `X-Cipher-Version: v1` hoặc `Cipher-Version: v1`
  - Server maintains mapping: `{v1: ChaCha20-Poly1305, v2: AES-256-GCM}`
  - **Decrypt**: Dùng version từ request header → correct algorithm
  - **Encrypt response**: LUÔN dùng version mới nhất (v2)
  - Client cũ (v1) → server decrypt bằng v1, encrypt response bằng v2 → client update dần
  - **Sunset policy**: 
    - v1 deprecated → response header `Sunset: 2027-01-01`
    - Sau 3 tháng → reject v1 requests (HTTP 426 Upgrade Required)
  - **Feature flag**: Rollout v2 cho 10% → 50% → 100% clients

---

## 3. Product/Tool Evaluation

| Sản phẩm/Công cụ | Cách giải quyết | Tính năng chính | Pros | Cons | URL |
|-------------------|----------------|----------------|------|------|-----|
| Google Tink | Crypto library with KMS integration | Streaming AEAD, Envelope AEAD, Keyset management | Misuse-resistant, automatic nonce, KMS native | Opinionated key format, learning curve | github.com/tink-crypto |
| AWS Encryption SDK | Envelope encryption framework | Multi-CMK, caching CMM, frame-based encryption | Production-proven, AWS native | AWS vendor lock-in, heavy SDK | docs.aws.amazon.com |
| HashiCorp Vault | Secret management + encryption as a service | Transit engine, auto-unseal, PKI, key rotation | Feature-rich, multi-cloud, audit logs | Operational complexity, self-managed | vaultproject.io |
| Azure Confidential Ledger | Immutable audit log | Tamper-proof, CCF-based | Regulatory compliance, immutable | Azure only, expensive | azure.microsoft.com |

---

## 4. Summary

### Top 3 Insights
1. **Tink Streaming AEAD** giải quyết gRPC streaming encryption (Issue #8) out-of-the-box — frame-based, automatic nonce, sequence tracking
2. **AAD Context Binding** pattern (`tenant:T1:user:U1:field:$.ssn`) giải quyết cả partial encryption (Issue #9) VÀ multi-tenancy isolation
3. **Envelope Encryption** (KMS GenerateDataKey) là the only acceptable approach cho key management (Issue #11) — tuyệt đối không plaintext key trong DB

### Critical Gaps (cần custom implementation)
1. JSON path selective encryption middleware (annotation-based)
2. Break-glass Decryption Vault service
3. Client time offset synchronization mechanism
4. Cipher version negotiation protocol

---

> **Next**: Phase 4 (Comparison Analysis)
