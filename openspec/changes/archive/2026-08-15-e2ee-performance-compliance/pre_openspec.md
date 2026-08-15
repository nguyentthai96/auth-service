# Pre-OpenSpec: e2ee-performance-compliance

> **Type**: NEWBUILD
> **Flow**: Command
> **Source**: URD (File — business_analysis.md from research output)
> **Classification Evidence**: `encrypt` keyword → `TotpService.kt` (AES-GCM) → nhưng KHÔNG có E2EE middleware, filter, KMS integration → NEWBUILD
> **Archive**: N/A
> **Quality Score**: 78/100

## 📋 Feature Summary

Xây dựng middleware E2EE (End-to-End Encryption) nâng cao cho auth-service, bao gồm: frame-based encryption cho gRPC streaming, selective field encryption với AAD context binding, client time skew correction, KMS envelope encryption cho key management, audit log decryption vault, và cipher version negotiation. Tính năng này nâng cấp bảo mật từ basic AES-GCM (TotpService) lên enterprise-grade E2EE middleware.

| Metric | Giá trị |
|--------|---------|
| Số FR | 36 (URD: 30, Enriched: 6 → capped tại 5) |
| Issues | 6 (🔴: 2, 🟡: 4) |
| Open Questions | 3 |
| **Quality Score** | **78/100** |

---

## 1. Actors

- **Client (Mobile/Web)**: Gửi request encrypted, nhận response encrypted, đồng bộ time offset
- **Auth Service (Middleware)**: Encrypt/decrypt request/response, quản lý key, validate timestamp, audit logging
- **Cloud KMS (External)**: Quản lý master key, generate/decrypt DEK
- **Security Admin**: Yêu cầu break-glass decrypt audit log
- **Ops/SRE**: Monitor KMS availability, cipher version rollout
- **Redis**: Cache DEK, nonce dedup, rate limit

## 2. Functional Requirements

### FR-001: Mã hóa frame cho gRPC streaming [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải mã hóa mỗi gRPC message/frame độc lập với nonce riêng khi server streaming data
- **Validation**: Mỗi frame có unique nonce = prefix(8 bytes) || sequence_counter(4 bytes)

### FR-002: Sequence number tăng monotonic [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải đảm bảo sequence number trong streaming tăng monotonic, không skip
- **Validation**: Sequence number liên tục, client reject nếu phát hiện gap

### FR-003: Client verify thứ tự frame [URD]
- **Actor**: Client
- **Action**: Client phải verify sequence order khi nhận streaming frames, reject out-of-order frames
- **Validation**: Frame n+1 chỉ được xử lý sau frame n

### FR-004: Chuyển TLS tunnel cho file lớn [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải chuyển sang TLS tunnel + SHA-256 integrity hash khi file > 10MB thay vì app-layer encryption
- **Validation**: File ≤ 10MB: app-layer encrypt; File > 10MB: TLS + hash

### FR-005: AAD context binding cho partial encrypt [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải xây dựng AAD chứa TenantID + UserID + FieldPath cho mỗi encrypted field để chống cut-and-paste
- **Validation**: AAD = `"tenant:{tid}:user:{uid}:field:{path}"`, decrypt FAIL nếu AAD mismatch

### FR-006: Nonce riêng cho mỗi encrypted field [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải generate random 12-byte nonce riêng cho mỗi field khi partial encryption
- **Validation**: Mỗi field có nonce khác nhau trong cùng request

### FR-007: Annotation đánh dấu field nhạy cảm [URD]
- **Actor**: Developer
- **Action**: Hệ thống phải sử dụng `@Sensitive` annotation trên DTO field để xác định field cần encrypt
- **Validation**: Chỉ field có annotation mới được encrypt

### FR-008: Partial encrypt chỉ cho JSON body [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống chỉ áp dụng partial encryption cho JSON body, không áp dụng cho query params/headers
- **Validation**: Headers và query params không bị encrypt

### FR-009: Default tolerance ±5 phút [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải validate timestamp với tolerance ±5 phút (configurable)
- **Validation**: |request_timestamp - server_time| ≤ 300s

### FR-010: Initial tolerance ±10 phút [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải chấp nhận tolerance rộng hơn (±10 phút) cho first request để cho phép initial sync
- **Validation**: First request: tolerance 600s; subsequent: 300s

### FR-011: X-Server-Time trong mọi response [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải trả header `X-Server-Time` (epoch ms) trong EVERY response để client tính offset
- **Validation**: Header present trong tất cả response

### FR-012: Moving average cho offset [URD]
- **Actor**: Client
- **Action**: Client nên dùng moving average (window = 5 responses) cho offset smoothing
- **Validation**: Offset = trung bình 5 offset gần nhất

### FR-013: Nonce dedup qua Redis [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải cache nonce 10 phút (Redis SETNX + TTL) để chống replay attack
- **Validation**: Duplicate nonce → reject 409 Conflict

### FR-014: Không lưu plaintext key [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống TUYỆT ĐỐI không lưu plaintext key trong DB hoặc env var (trừ local dev)
- **Validation**: Chỉ encrypted_DEK được lưu trong DB

### FR-015: Master Key trong HSM/KMS [URD]
- **Actor**: Cloud KMS
- **Action**: Master Key phải nằm trong HSM, managed by Cloud KMS, KHÔNG BAO GIỜ rời HSM
- **Validation**: Key material không export được

### FR-016: DEK cache TTL 5 phút [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải cache plaintext DEK trong memory với TTL 5 phút, WIPE sau khi hết TTL
- **Validation**: Cache entry tự xóa sau 300s

### FR-017: GDPR region-specific KMS [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải route key request tới KMS endpoint đúng region (EU data → EU KMS, US data → US KMS)
- **Validation**: RegionResolver map user.region → KMS endpoint

### FR-018: Fail-fast policy [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải reject tất cả requests (503) khi KMS unavailable VÀ không có cached DEK
- **Validation**: No cache + no KMS = HTTP 503

### FR-019: Key rotation zero-downtime [URD]
- **Actor**: Auth Service
- **Action**: Hệ thống phải hỗ trợ key rotation zero-downtime — old version active until sunset
- **Validation**: Old data vẫn decrypt được bằng old DEK; new data dùng new DEK

### FR-020: Middleware không log plaintext [URD]
- **Actor**: Auth Service
- **Action**: Main middleware KHÔNG ĐƯỢC log plaintext, KHÔNG ĐƯỢC có decrypt quyền cho audit data
- **Validation**: Audit log chỉ chứa encrypted payload reference

### FR-021: Decryption Vault isolated [URD]
- **Actor**: Decryption Vault
- **Action**: Decryption Vault phải là isolated service, separate network segment
- **Validation**: Vault không cùng VPC/subnet với main service

### FR-022: Break-glass 2 admins approve [URD]
- **Actor**: Security Admin
- **Action**: Break-glass procedure yêu cầu 2 Security Admins approve (4-eyes principle) mới được decrypt audit log
- **Validation**: Request bởi Admin A, approve bởi Admin B

### FR-023: JIT access TTL 30 phút [URD]
- **Actor**: Decryption Vault
- **Action**: JIT access tự động revoke sau 30 phút
- **Validation**: Access expired → reject, require new approval

### FR-024: Rate limit 10 decrypts/session [URD]
- **Actor**: Decryption Vault
- **Action**: Hệ thống phải rate limit max 10 decrypts per session để ngăn bulk export
- **Validation**: Decrypt thứ 11 → reject

### FR-025: Immutable audit trail [URD]
- **Actor**: Decryption Vault
- **Action**: TẤT CẢ vault access phải được logged trong immutable, append-only audit trail
- **Validation**: Log entries không thể modify/delete

### FR-026: Decode ALL active versions [URD]
- **Actor**: Auth Service
- **Action**: Server phải hỗ trợ decode tất cả cipher versions đang active
- **Validation**: Request với v1 hoặc v2 đều decrypt thành công

### FR-027: Encode response bằng latest [URD]
- **Actor**: Auth Service
- **Action**: Server LUÔN encode response bằng cipher version mới nhất
- **Validation**: Response header `X-Cipher-Version: v2` (latest)

### FR-028: Sunset timeline 3 tháng [URD]
- **Actor**: Auth Service
- **Action**: Deprecated version có sunset timeline 3 tháng, response header `Sunset: date`
- **Validation**: Sau 3 tháng → reject requests với old version (426 Upgrade Required)

### FR-029: Feature flag rollout [URD]
- **Actor**: Ops/SRE
- **Action**: Cipher version rollout qua feature flag: 10% → 50% → 100% clients
- **Validation**: Phần trăm client nhận v2 tăng dần

### FR-030: Versions config hot-reloadable [URD]
- **Actor**: Auth Service
- **Action**: Supported versions phải configured trong application.yml, hot-reloadable không cần restart
- **Validation**: Thay đổi config → service tự pick up

### FR-031: Idempotency cho key generation [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải đảm bảo idempotency khi generate/rotate key — duplicate request không tạo duplicate key
- **Validation**: Same tenantId + purpose + version → return existing key

### FR-032: Full request audit logging [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải log mọi encryption/decryption operation với trace_id, user_id, action, timestamp
- **Validation**: Mọi request có audit entry

### FR-033: KMS timeout handling [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải handle KMS timeout (>100ms) bằng circuit breaker pattern
- **Validation**: N failures → open circuit → fast-fail → periodic health check

### FR-034: KMS retry mechanism [ENRICHED]
- **Actor**: Auth Service
- **Action**: Hệ thống phải retry KMS call tối đa 3 lần với exponential backoff khi gặp transient error
- **Validation**: Retry 3 lần, backoff 100ms → 200ms → 400ms

### FR-035: E2EE filter trong security chain [ENRICHED]
- **Actor**: Auth Service
- **Action**: E2EE middleware phải được inject như OncePerRequestFilter trong Spring Security filter chain, sau JwtAuthFilter
- **Validation**: E2eeEncryptionFilter chạy sau authentication

## 3. Non-functional Requirements

- **Latency**: KMS call overhead < 50ms (cached), < 100ms (uncached)
- **Throughput**: Encryption overhead < 5ms per field (partial), < 10ms full body
- **Availability**: 99.99% với DEK cache fallback
- **Cache hit ratio**: DEK cache > 95% steady state
- **Nonce dedup**: Redis SETNX < 5ms
- **Streaming**: < 1ms per frame encryption
- **Security**: Plaintext DEK in memory < 5 phút
- **Compliance**: GDPR data residency, immutable audit log

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp — 30 URD FRs đã unique (mỗi FR từ business rule riêng biệt).

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-031** [ENRICHED]: Idempotency cho key generation — tránh duplicate key khi retry
- **FR-032** [ENRICHED]: Full request audit logging — compliance yêu cầu trace mọi operation
- **FR-033** [ENRICHED]: KMS timeout handling — circuit breaker cho external dependency
- **FR-034** [ENRICHED]: KMS retry mechanism — resilience cho transient failures
- **FR-035** [ENRICHED]: E2EE filter injection — cần vị trí đúng trong Spring Security chain

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Cloud KMS (AWS/GCP/Azure) | Master Key management, envelope encryption | Via Google Tink KMS adapters |
| Redis | DEK cache L2, nonce dedup, rate limiting | Đã tích hợp sẵn |
| PostgreSQL | Encrypted DEK storage, audit logs | Đã tích hợp sẵn |

## 6. Assumptions

- Cloud KMS provider sẽ được chọn tại deployment time (AWS KMS, GCP KMS, hoặc Azure Key Vault)
- gRPC chưa có trong current dependencies — FR-001~003 là P2 priority, implement khi gRPC được thêm
- Decryption Vault (FR-021~025) là separate microservice — specification cover architecture, implementation là project riêng
- Google Tink được chọn là primary crypto library (đã đánh giá 5 alternatives trong research)
- Multi-tenancy (TenantID isolation) áp dụng khi system chuyển sang SaaS model

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 22/25 | FR-004: ngưỡng "10MB" cần benchmark thực tế; FR-029: "feature flag" chưa rõ mechanism |
| Đầy đủ (Completeness) | 18/25 | FR-012: client-side requirement — server không control được; FR-021: Vault architecture chưa detail đủ cho implementation |
| Nhất quán (Consistency) | 23/25 | FR-009 vs FR-010: hai tolerance khác nhau nhưng consistent logic (initial vs default) |
| Kiểm thử được (Testability) | 15/25 | FR-015: "KHÔNG BAO GIỜ rời HSM" — khó test; FR-021: "separate network segment" — infra test; FR-025: "immutable" — cần append-only DB |
| **Tổng** | **78/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -1 | FR-004 | "File > 10MB" — ngưỡng cần benchmark | Thêm configurable threshold |
| 2 | Clarity | -2 | FR-029 | "Feature flag rollout: 10% → 50%" — không rõ mechanism cụ thể | Specify feature flag system (LaunchDarkly/custom) |
| 3 | Completeness | -4 | FR-012 | "Client nên dùng moving average" — server không enforce được | Chuyển thành recommendation cho client SDK |
| 4 | Completeness | -3 | FR-021 | "Isolated service, separate network segment" — thiếu detail | Bổ sung network topology spec |
| 5 | Consistency | -2 | FR-009/010 | Hai tolerance values — cần rõ ràng hơn cách detect "first request" | Sử dụng absence of offset header = first request |
| 6 | Testability | -3 | FR-015 | "KHÔNG BAO GIỜ rời HSM" — khó test integration | Test với LocalStack KMS mock |
| 7 | Testability | -4 | FR-021 | "Separate network segment" — infra-level test | Test logical isolation, defer network to infra |
| 8 | Testability | -3 | FR-025 | "Immutable" — cần append-only storage | Test INSERT-only, no UPDATE/DELETE permissions |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🔴 | KMS dependency = SPOF. Nếu KMS outage + DEK cache expired → toàn bộ service down | FR-018 | Circuit breaker + DEK cache + local fallback HSM |
| 2 | Risk | 🔴 | Nonce reuse trong AES-GCM → catastrophic failure (leak plaintext XOR). Phải guarantee unique nonce | FR-001, FR-006 | Dùng Tink automatic nonce management |
| 3 | Incomplete | 🟡 | gRPC chưa có trong dependency — FR-001~003 phụ thuộc vào khi nào thêm gRPC support | FR-001~003 | Implement streaming module khi gRPC added, design trước |
| 4 | Incomplete | 🟡 | Decryption Vault là separate service — cần spec riêng cho microservice đó | FR-021~025 | Tạo separate openspec cho Vault service |
| 5 | Ambiguity | 🟡 | "Feature flag rollout" — chưa rõ dùng tool nào (LaunchDarkly, Unleash, custom?) | FR-029 | Dùng application.yml + @RefreshScope hoặc Spring Cloud Config |
| 6 | Missing | 🟡 | Không có FR cho metrics/monitoring (Prometheus counters cho encrypt ops, cache hit ratio, KMS latency) | N/A | Thêm observability FRs |

> Nếu không có issues → ghi "Không phát hiện vấn đề."

## 9. Open Questions

1. **Cloud KMS provider**: Dùng AWS KMS, GCP Cloud KMS, hay Azure Key Vault? Quyết định ảnh hưởng tới Tink adapter và region mapping.
2. **Multi-tenancy timeline**: Khi nào chuyển SaaS? Per-tenant DEK chỉ cần nếu multi-tenant enabled.
3. **Decryption Vault**: Build in-house hay dùng HashiCorp Vault Transit engine? Ảnh hưởng tới FR-021~025.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
- Security / Encryption / Middleware

### 10.2 Flow Type
- Command (cross-cutting encryption middleware, không có OTP/financial flow)

### 10.3 Candidate Services
- **auth-service**: Chứa existing encryption logic (TotpService.kt), security config (SecurityProperties.kt), security filter chain (JwtAuthFilter.kt), Redis integration, exception handling hierarchy
- **Evidence**: `encrypt` keyword → `TotpService.kt` (L37: AES/GCM/NoPadding) → `SecurityProperties.kt` (config hub) → `JwtAuthFilter.kt` (filter chain) → `SecurityConfig.kt` (filter registration)

### Detection Evidence
- Keyword: `AES/GCM` → Module: `auth.application.TotpService` → File: `src/main/kotlin/com/ntt/authservice/auth/application/TotpService.kt`
- Keyword: `SecurityProperties` → Module: `shared.config.SecurityProperties` → File: `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt`
- Keyword: `JwtAuthFilter` → Module: `shared.security.JwtAuthFilter` → File: `src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt`
- Keyword: `RedisConfig` → Module: `shared.config.RedisConfig` → File: `src/main/kotlin/com/ntt/authservice/shared/config/RedisConfig.kt`
- Keyword: `AuthException` → Module: `shared.exception.AuthExceptions` → File: `src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`

### 10.4 External Integrations
- Cloud KMS (AWS/GCP/Azure) — NEW integration, chưa có code
- Redis — đã tích hợp (OTP, rate limit, permission cache)
- PostgreSQL — đã tích hợp (entity storage)

### 10.5 Required Modules
- `shared/encryption/` — NEW package cho E2EE components
- `shared/audit/` — EXISTING (AuditLogService.kt), cần extend cho encrypted audit
- `shared/security/` — EXISTING (JwtAuthFilter.kt), cần add E2eeEncryptionFilter
- `shared/config/` — EXISTING (SecurityProperties.kt), cần extend cho E2eeProperties

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Client | Gửi encrypted request + cipher version header + timestamp + nonce | Auth Service |
| 2 | Auth Service | Validate cipher version, timestamp tolerance, nonce uniqueness | CipherRegistry, TimeSkewValidator, NonceTracker (Redis) |
| 3 | Auth Service | Resolve DEK từ cache hoặc KMS | KmsKeyService → Caffeine L1 → Redis L2 → Cloud KMS |
| 4 | Auth Service | Decrypt request body (full hoặc partial) | FieldEncryptor |
| 5 | Auth Service | Forward tới business handler | Handler |
| 6 | Handler | Process business logic, return response | Auth Service |
| 7 | Auth Service | Encrypt response body (latest cipher version) | FieldEncryptor |
| 8 | Auth Service | Add X-Server-Time, X-Cipher-Version headers | Middleware |
| 9 | Auth Service | Log encrypted audit entry | EncryptedAuditLogger → DB |
| 10 | Auth Service | Return encrypted response to client | Client |


## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-001 Basic Flow | StreamEncryptor | StreamEncryptor.kt [ADD] | Pending |
| FR-002 | UC-001 BR-002 | StreamEncryptor | StreamEncryptor.kt [ADD] | Pending |
| FR-003 | UC-001 BR-003 | Client SDK | N/A (client-side) | Pending |
| FR-004 | UC-001 BR-004 | StreamEncryptor | StreamEncryptor.kt [ADD] | Pending |
| FR-005 | UC-002 BR-005 | FieldEncryptor | FieldEncryptor.kt [ADD] | Pending |
| FR-006 | UC-002 BR-006 | FieldEncryptor | FieldEncryptor.kt [ADD] | Pending |
| FR-007 | UC-002 BR-007 | Sensitive | Sensitive.kt [ADD] | Pending |
| FR-008 | UC-002 BR-008 | E2eeFilter | E2eeEncryptionFilter.kt [ADD] | Pending |
| FR-009 | UC-003 BR-009 | TimeSkewValidator | TimeSkewValidator.kt [ADD] | Pending |
| FR-010 | UC-003 BR-010 | TimeSkewValidator | TimeSkewValidator.kt [ADD] | Pending |
| FR-011 | UC-003 BR-011 | E2eeFilter | E2eeEncryptionFilter.kt [ADD] | Pending |
| FR-012 | UC-003 BR-012 | Client SDK | N/A (client-side) | Pending |
| FR-013 | UC-003 BR-013 | NonceTracker | NonceTracker.kt [ADD] | Pending |
| FR-014 | UC-004 BR-014 | KmsKeyService | KmsKeyService.kt [ADD] | Pending |
| FR-015 | UC-004 BR-015 | KmsKeyService | Cloud KMS (external) | Pending |
| FR-016 | UC-004 BR-016 | KmsKeyService | KmsKeyService.kt [ADD] | Pending |
| FR-017 | UC-004 BR-017 | KmsKeyService | KmsKeyService.kt [ADD] | Pending |
| FR-018 | UC-004 BR-018 | E2eeFilter | E2eeEncryptionFilter.kt [ADD] | Pending |
| FR-019 | UC-004 BR-019 | KmsKeyService | KmsKeyService.kt [ADD] | Pending |
| FR-020 | UC-005 BR-020 | AuditLogger | EncryptedAuditLogger.kt [ADD] | Pending |
| FR-021 | UC-005 BR-021 | Vault Service | Separate microservice | Pending |
| FR-022 | UC-005 BR-022 | Vault Service | Separate microservice | Pending |
| FR-023 | UC-005 BR-023 | Vault Service | Separate microservice | Pending |
| FR-024 | UC-005 BR-024 | Vault Service | Separate microservice | Pending |
| FR-025 | UC-005 BR-025 | Vault Service | Separate microservice | Pending |
| FR-026 | UC-006 BR-026 | CipherRegistry | CipherRegistry.kt [ADD] | Pending |
| FR-027 | UC-006 BR-027 | CipherRegistry | CipherRegistry.kt [ADD] | Pending |
| FR-028 | UC-006 BR-028 | E2eeProperties | E2eeProperties.kt [ADD] | Pending |
| FR-029 | UC-006 BR-029 | E2eeProperties | Application config | Pending |
| FR-030 | UC-006 BR-030 | E2eeProperties | @RefreshScope config | Pending |
| FR-031 | Enriched | KmsKeyService | KmsKeyService.kt [ADD] | Pending |
| FR-032 | Enriched | AuditLogger | EncryptedAuditLogger.kt [ADD] | Pending |
| FR-033 | Enriched | KmsKeyService | KmsKeyService.kt [ADD] | Pending |
| FR-034 | Enriched | KmsKeyService | KmsKeyService.kt [ADD] | Pending |
| FR-035 | Enriched | SecurityConfig | SecurityConfig.kt [MODIFY] | Pending |

## 13. Agent Notes (Tổng hợp bổ sung)

> Phần này agent TỰ DO bổ sung thông tin phân tích ngoài template.

### Observations

- **Độ phức tạp**: HIGH — tính năng cross-cutting, ảnh hưởng toàn bộ request pipeline
- **Rủi ro lớn nhất**: Nonce reuse (AES-GCM catastrophic failure) và KMS SPOF
- **Điểm mạnh**: Project đã có AES-256-GCM pattern trong TotpService → có thể extract và generalize
- **Điểm yếu**: Current encryption key management = plaintext env var → immediate security debt

### Related Features / Precedents
- TotpService.kt — existing AES-256-GCM encrypt/decrypt pattern, reuse logic cốt lõi
- SecurityProperties.kt — config hub, extend thêm E2eeProperties
- JwtAuthFilter.kt — filter pattern, clone pattern cho E2eeEncryptionFilter
- MultiTierPermissionCache.kt — L1/L2 cache pattern (Caffeine + Redis), reuse cho DEK cache

### Integration Notes
- **Google Tink**: Crypto engine chính — Streaming AEAD, KMS Envelope AEAD, automatic nonce. Cần thêm `tink:1.15+` và `tink-awskms:1.15+`
- **Redis**: Đã tích hợp sẵn — reuse cho nonce dedup (SETNX + TTL 10 min) và DEK cache L2
- **PostgreSQL**: Cần migration thêm 4 tables: `encryption_key`, `audit_log_encrypted`, `vault_access_log`, `cipher_version`
- **BouncyCastle**: Đang là `testRuntimeOnly` — promote tới `implementation` nếu cần advanced primitives

### Suggested Approach
1. **Phase 1**: E2eeProperties + CipherRegistry — config foundation
2. **Phase 2**: KmsKeyService + DB migration — P0 (replace plaintext key ASAP)
3. **Phase 3**: @Sensitive + FieldEncryptor — partial encryption
4. **Phase 4**: TimeSkewValidator + NonceTracker — anti-replay
5. **Phase 5**: E2eeEncryptionFilter — integrate all into filter chain
6. **Phase 6**: EncryptedAuditLogger — audit system
7. **Phase 7**: StreamEncryptor — gRPC (when ready)
8. **Phase 8**: Vault Service — separate project

### Context from Confluence Images
N/A — source là local file, không có hình ảnh Confluence.
