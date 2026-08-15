# Business Analysis: E2EE Performance & Compliance

> Phân tích nghiệp vụ chi tiết — use case decomposition cho nhóm tính năng E2EE bảo mật nâng cao.

## 1. Tổng quan nghiệp vụ

### 1.1 Business Context

Hệ thống auth-service cần nâng cấp bảo mật E2EE middleware để đáp ứng:
- **Hiệu năng**: Xử lý streaming data, file lớn, partial encryption mà không bottleneck
- **An toàn**: Chống nonce reuse, replay attack, cut-and-paste attack, key leakage
- **Compliance**: GDPR data residency, audit forensics, break-glass procedure
- **Vận hành**: CI/CD cipher migration, multi-tenancy isolation, failure handling

### 1.2 Stakeholders

| Stakeholder | Role | Concern |
|-------------|------|---------|
| Dev Team | Implementer | Complexity, testability, backward compatibility |
| Security Team | Auditor | Key management, audit trail, incident investigation |
| Ops/SRE | Operator | Availability, KMS dependency, monitoring |
| Product Owner | Decision maker | Timeline, cost, compliance deadlines |
| Client Team (Mobile/Web) | Consumer | SDK integration, time skew handling, version migration |

---

## 2. Use Cases

### UC-001: Frame-based Encryption cho gRPC Streaming

**Tại sao cần**: Khi server streaming data (VD: export report, realtime events), mỗi gRPC message phải được mã hóa độc lập với nonce riêng để tránh nonce reuse — lỗi phá hủy toàn bộ AES-GCM security.

#### Basic Flow
1. Client mở gRPC streaming connection (mTLS)
2. Server xác thực JWT, lấy key_id, cipher_version từ context
3. Server khởi tạo streaming encryption session:
   - Generate session nonce prefix (8 bytes random)
   - Initialize sequence counter = 0
4. Với mỗi message/frame:
   a. Tăng sequence counter
   b. Generate frame nonce = prefix || counter || is_last
   c. Encrypt payload với AES-256-GCM + nonce + AAD (contains sequence)
   d. Gửi: `{version, flags, seq_no, nonce, encrypted_payload, auth_tag}`
5. Frame cuối: set `is_last_frame = true` trong flags
6. Client decrypt từng frame, verify sequence order

#### Exception Flows
- **E1**: Sequence number gap detected → client reject + close stream
- **E2**: Auth tag verification failed → client reject frame + close stream
- **E3**: Frame size > max_frame_size (1MB) → server split hoặc reject

#### Business Rules
- BR-001: Mỗi frame PHẢI có unique nonce (prefix + sequence counter)
- BR-002: Sequence number PHẢI tăng monotonic, không skip
- BR-003: Client PHẢI verify sequence order, reject out-of-order frames
- BR-004: File > 10MB → switch sang TLS tunnel + SHA-256 integrity hash

---

### UC-002: Selective Field Encryption với AAD Context Binding

**Tại sao cần**: Mã hóa toàn bộ body tốn tài nguyên không cần thiết. Chỉ cần mã hóa field nhạy cảm (SSN, card number, PII). AAD binding ngăn chặn copy encrypted field giữa users.

#### Basic Flow
1. Request đến middleware, header chỉ định `Encryption-Mode: PARTIAL`
2. Middleware lấy list `@Sensitive` fields từ DTO metadata (cached)
3. Với mỗi sensitive field:
   a. Build AAD = `"tenant:{tid}:user:{uid}:field:{path}:ts:{timestamp}"`
   b. Generate random 12-byte nonce
   c. Encrypt field value với AES-256-GCM + nonce + AAD
   d. Replace field value bằng encrypted wrapper:
      ```json
      {"v":1, "alg":"A256GCM", "kid":"key-v1", "iv":"...", "tag":"...", "ct":"..."}
      ```
4. Non-sensitive fields giữ nguyên plaintext
5. Forward request tới handler

#### Exception Flows
- **E1**: Field path invalid → skip encryption, log warning
- **E2**: AAD mismatch khi decrypt (wrong user/tenant) → throw `EncryptionContextMismatchException`
- **E3**: Key not found by kid → throw `KeyNotFoundException`, fail-fast

#### Business Rules
- BR-005: AAD PHẢI chứa TenantID + UserID + FieldPath → chống cut-and-paste
- BR-006: Mỗi encrypted field có nonce riêng
- BR-007: `@Sensitive` annotation quyết định field nào cần encrypt
- BR-008: Partial encryption chỉ áp dụng cho JSON body, không áp dụng cho query params/headers

---

### UC-003: Client Time Skew Correction

**Tại sao cần**: Đồng hồ điện thoại user có thể lệch server vài phút. Nếu reject request có timestamp quá khác → UX tệ. Nếu accept quá rộng → replay window lớn.

#### Basic Flow
1. Server xử lý request, trả response
2. Response header chứa: `X-Server-Time: 1723708539000` (epoch ms)
3. Client nhận response, tính:
   ```
   offset = server_time - client_time
   ```
4. Client cache offset (moving average của 5 responses gần nhất)
5. Request tiếp theo:
   ```
   adjusted_timestamp = client_time + cached_offset
   ```
6. Server validate: `|request_timestamp - server_time| ≤ 300s` (±5 phút)

#### Exception Flows
- **E1**: First request — tolerance rộng hơn (±10 phút) cho initial sync
- **E2**: Offset thay đổi đột ngột (> 2 phút) → client re-sync, log anomaly
- **E3**: Request ngoài tolerance → reject HTTP 400 + error body chứa `server_time` để client sync lại

#### Business Rules
- BR-009: Default tolerance = ±5 phút (configurable)
- BR-010: Initial request tolerance = ±10 phút
- BR-011: Server PHẢI trả `X-Server-Time` trong EVERY response
- BR-012: Client nên dùng moving average (window = 5) cho offset smoothing
- BR-013: Nonce dedup: server cache nonce 10 phút (Redis SET + TTL)

---

### UC-004: KMS Envelope Encryption cho Key Management

**Tại sao cần**: Key plaintext trong DB/env var → SQL injection = lộ tất cả key. KMS envelope encryption đảm bảo master key KHÔNG BAO GIỜ rời HSM.

#### Basic Flow
1. **Key Generation** (one-time per tenant/purpose):
   a. Service → Cloud KMS: `GenerateDataKey(masterKeyARN, context={tenantId, purpose})`
   b. KMS → Service: `{plaintext_DEK, encrypted_DEK}`
   c. Service → DB: Store `encrypted_DEK` + `key_version` + `created_at`
   d. Service: **WIPE plaintext_DEK** from memory
   
2. **Encryption** (per request):
   a. Service → DB: Load `encrypted_DEK`
   b. Service → KMS: `Decrypt(encrypted_DEK, context={tenantId, purpose})`
   c. KMS → Service: `plaintext_DEK`
   d. Service: Encrypt data với `plaintext_DEK`
   e. Service: Cache `plaintext_DEK` in memory (TTL 5 min)
   f. Service: **WIPE after TTL**

3. **Key Rotation**:
   a. Generate new DEK (version N+1)
   b. Old data stays encrypted with old DEK (version N)
   c. New data encrypted with new DEK (version N+1)
   d. Background job: re-encrypt old data with new DEK (optional)

#### Exception Flows
- **E1**: KMS unreachable → check DEK cache → if cached, continue → else FAIL-FAST (503)
- **E2**: KMS decrypt fails (wrong context) → reject request (403 Forbidden)
- **E3**: DB read fails for encrypted_DEK → FAIL-FAST (503)

#### Business Rules
- BR-014: **TUYỆT ĐỐI** không lưu plaintext key trong DB, env var (trừ local dev)
- BR-015: Master Key nằm trong HSM, managed by Cloud KMS
- BR-016: DEK cache TTL = 5 phút, configurable
- BR-017: GDPR region: EU data → EU KMS endpoint, US data → US KMS endpoint
- BR-018: Fail-fast policy: KMS unavailable + no cache = reject all requests
- BR-019: Key rotation: zero-downtime, old version active until sunset

---

### UC-005: Audit Log Decryption Vault

**Tại sao cần**: Audit log chứa encrypted payload. Khi cần điều tra sự cố bảo mật, Security Team cần decrypt log. Nhưng cho phép decrypt tùy ý → nguy hiểm. Cần "Decryption Vault" với break-glass procedure.

#### Basic Flow
1. **Logging** (automatic, mỗi request):
   a. Middleware log: `{trace_id, encrypted_payload_hash, key_id, user_id, tenant_id, action, timestamp, ip, user_agent}`
   b. **KHÔNG LOG plaintext** — middleware KHÔNG CÓ decrypt quyền cho log
   c. Encrypted payload reference: `{payload_store_id}` → separate encrypted storage

2. **Investigation** (on-demand, break-glass):
   a. Security Admin A → Approval Service: Request decrypt cho `trace_id=xxx`, lý do: `"incident INV-2026-001"`
   b. Approval Service → Security Admin B: Notification cần approve
   c. Security Admin B → Approval Service: Approve
   d. Approval Service → Decryption Vault: Grant JIT access (TTL 30 min)
   e. Admin A → Decryption Vault: Request decrypt `trace_id=xxx`
   f. Vault → KMS: Decrypt DEK
   g. Vault → Admin A: Return decrypted payload
   h. Vault: Log access in immutable audit trail
   i. After TTL: Auto-revoke access

#### Exception Flows
- **E1**: Only 1 admin approves (not 2) → reject, log attempt
- **E2**: JIT access expired → reject, require new approval
- **E3**: Vault audit log storage full → alert + failover to backup storage
- **E4**: Admin tries to export bulk data → rate limit (max 10 decrypts per session)

#### Business Rules
- BR-020: Main middleware **KHÔNG ĐƯỢC** log plaintext, **KHÔNG ĐƯỢC** có decrypt quyền cho audit data
- BR-021: Decryption Vault = isolated service, separate network segment
- BR-022: Break-glass requires **2 Security Admins** approve (4-eyes principle)
- BR-023: JIT access TTL = 30 phút, auto-revoke
- BR-024: Rate limit: max 10 decrypts per session
- BR-025: ALL vault access logged in immutable, append-only audit trail

---

### UC-006: Cipher Version Negotiation

**Tại sao cần**: Khi chuyển thuật toán (VD: ChaCha20 → AES-GCM), client cũ chưa update app vẫn phải giao tiếp được. Server cần hỗ trợ multi-version.

#### Basic Flow
1. Client gửi request với header: `X-Cipher-Version: v1`
2. Server middleware kiểm tra version → load correct cipher suite
3. Server decrypt request body bằng cipher v1
4. Server process request
5. Server encrypt response body bằng **latest version** (v2)
6. Response header: `X-Cipher-Version: v2`
7. Nếu v1 sắp deprecated: Response header thêm `Sunset: 2027-01-01`

#### Exception Flows
- **E1**: Unknown version → reject HTTP 400 + supported versions list
- **E2**: Sunset version expired → reject HTTP 426 Upgrade Required
- **E3**: No version header → assume latest version (backward compatible default)

#### Business Rules
- BR-026: Server PHẢI hỗ trợ decode ALL active versions
- BR-027: Server LUÔN encode response bằng latest version
- BR-028: Sunset timeline = 3 tháng after deprecation announcement
- BR-029: Feature flag rollout: 10% → 50% → 100% clients
- BR-030: Supported versions configured in application.yml, hot-reloadable

---

## 3. Traceability Matrix

| Use Case | Business Rules | Gaps Addressed | Priority |
|----------|---------------|----------------|----------|
| UC-001 (gRPC Streaming) | BR-001→004 | G1 (gRPC), G4 (Nonce) | P2 |
| UC-002 (Partial Encrypt) | BR-005→008 | G3 (Partial) | P1 |
| UC-003 (Time Skew) | BR-009→013 | G5 (Time Skew) | P1 |
| UC-004 (KMS Envelope) | BR-014→019 | G2 (Key Storage) | **P0** |
| UC-005 (Audit Vault) | BR-020→025 | G6 (Audit) | P1 |
| UC-006 (Cipher Version) | BR-026→030 | G7 (Versioning) | P2 |

---

## 4. Business Rules Summary

### 4.1 Bảng tổng hợp 30 Business Rules

| ID | Category | Rule | Severity |
|----|----------|------|----------|
| BR-001 | Streaming | Mỗi frame PHẢI có unique nonce | 🔴 Critical |
| BR-002 | Streaming | Sequence number tăng monotonic | 🔴 Critical |
| BR-003 | Streaming | Client verify sequence order | 🟡 High |
| BR-004 | Streaming | File >10MB → TLS tunnel + hash | 🟡 High |
| BR-005 | Partial | AAD chứa TenantID + UserID + FieldPath | 🔴 Critical |
| BR-006 | Partial | Mỗi field có nonce riêng | 🔴 Critical |
| BR-007 | Partial | @Sensitive annotation quyết định | 🟡 High |
| BR-008 | Partial | Chỉ áp dụng cho JSON body | 🟢 Medium |
| BR-009 | Time Skew | Default tolerance ±5 phút | 🟡 High |
| BR-010 | Time Skew | Initial tolerance ±10 phút | 🟡 High |
| BR-011 | Time Skew | Server trả X-Server-Time mỗi response | 🟡 High |
| BR-012 | Time Skew | Client dùng moving average | 🟢 Medium |
| BR-013 | Time Skew | Nonce dedup cache 10 phút (Redis) | 🟡 High |
| BR-014 | KMS | KHÔNG lưu plaintext key trong DB | 🔴 Critical |
| BR-015 | KMS | Master Key trong HSM/KMS | 🔴 Critical |
| BR-016 | KMS | DEK cache TTL 5 phút | 🟡 High |
| BR-017 | KMS | GDPR: region-specific KMS endpoint | 🟡 High |
| BR-018 | KMS | Fail-fast: no cache + no KMS = reject | 🔴 Critical |
| BR-019 | KMS | Key rotation zero-downtime | 🟡 High |
| BR-020 | Audit | Middleware KHÔNG log plaintext | 🔴 Critical |
| BR-021 | Audit | Vault = isolated service | 🔴 Critical |
| BR-022 | Audit | Break-glass: 2 admins approve | 🔴 Critical |
| BR-023 | Audit | JIT access TTL 30 phút | 🟡 High |
| BR-024 | Audit | Rate limit: 10 decrypts/session | 🟡 High |
| BR-025 | Audit | Immutable audit trail | 🔴 Critical |
| BR-026 | Versioning | Decode ALL active versions | 🟡 High |
| BR-027 | Versioning | Encode response bằng latest | 🟡 High |
| BR-028 | Versioning | Sunset timeline 3 tháng | 🟢 Medium |
| BR-029 | Versioning | Feature flag rollout | 🟢 Medium |
| BR-030 | Versioning | Versions config hot-reloadable | 🟢 Medium |

---

> **Next**: Phase 6 (Technical Specification)
