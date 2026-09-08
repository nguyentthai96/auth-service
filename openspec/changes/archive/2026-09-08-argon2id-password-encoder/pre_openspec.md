# Pre-OpenSpec: argon2id-password-encoder

> **Type**: EXTEND
> **Flow**: Command
> **Source**: URD (file — research brief + business analysis)
> **Classification Evidence**: `BCryptPasswordEncoder` → module `shared/config/SecurityConfig.kt` → file `SecurityConfig.kt:112`, `SecurityProperties.kt:79`
> **Archive**: N/A
> **Quality Score**: 90/100

## 📋 Feature Summary

Chuyển đổi password hashing algorithm từ BCrypt (strength=12) sang Argon2id với kiến trúc Bean auto-configuration. BCrypt hiện chiếm 95.2% login latency (~252ms) và chỉ cung cấp CPU-hard protection. Argon2id là OWASP 2024 recommended algorithm — memory-hard, GPU-resistant. Hệ thống mới sử dụng DelegatingPasswordEncoder để backward compatible với BCrypt hashes hiện tại, kết hợp rehash-on-login để migrate transparent.

| Metric | Giá trị |
|--------|---------|
| Số FR | 12 (URD: 9, Enriched: 3) |
| Issues | 2 (🔴: 0, 🟡: 2) |
| Open Questions | 1 |
| **Quality Score** | **90/100** |

---

## 1. Actors

- **User**: Người dùng hệ thống — đăng ký, đăng nhập, đổi mật khẩu
- **System (auth-service)**: Hệ thống xác thực — encode/verify password, rehash-on-login
- **DevOps/SRE**: Quản trị viên hạ tầng — cấu hình algorithm, tune parameters via YAML

## 2. Functional Requirements

### FR-001: Mã hóa mật khẩu mới bằng Argon2id [URD]
- **Actor**: System
- **Action**: Hệ thống phải encode tất cả mật khẩu mới bằng Argon2id algorithm khi user đăng ký hoặc đổi mật khẩu
- **Validation**: Hash output phải có prefix `{argon2id}` và tuân theo format `$argon2id$v=19$m=...,t=...,p=...$salt$hash`

### FR-002: Backward compatible với BCrypt hashes [URD]
- **Actor**: System
- **Action**: Hệ thống phải verify được mật khẩu đã encode bằng BCrypt mà không cần migration đồng loạt
- **Validation**: DelegatingPasswordEncoder detect prefix `{bcrypt}` → route tới BCryptPasswordEncoder

### FR-003: Rehash-on-login transparent [URD]
- **Actor**: System
- **Action**: Hệ thống phải tự động re-encode mật khẩu từ BCrypt sang Argon2id khi user đăng nhập thành công với BCrypt hash cũ
- **Validation**: Sau login thành công, `password_hash` trong DB phải được update sang `{argon2id}...`

### FR-004: Cấu hình algorithm qua YAML [URD]
- **Actor**: DevOps
- **Action**: Hệ thống phải cho phép switch algorithm giữa `argon2id` và `bcrypt` qua `application.yml` mà không cần thay đổi code
- **Validation**: Property `app.security.password.algorithm` chấp nhận giá trị `argon2id` hoặc `bcrypt`

### FR-005: Tune Argon2id parameters qua YAML [URD]
- **Actor**: DevOps
- **Action**: Hệ thống phải cho phép cấu hình `memory-cost`, `iterations`, `parallelism`, `salt-length`, `hash-length` qua YAML
- **Validation**: Thay đổi `app.security.password.argon2.*` → restart service → encoder sử dụng giá trị mới

### FR-006: Password history cross-algorithm [URD]
- **Actor**: System
- **Action**: Hệ thống phải check password history chính xác khi user đổi mật khẩu, bất kể old hashes dùng BCrypt hay Argon2id
- **Validation**: DelegatingPasswordEncoder.matches() verify cả `{bcrypt}` và `{argon2id}` prefixed hashes

### FR-007: Database migration thêm prefix [URD]
- **Actor**: System (Flyway)
- **Action**: Hệ thống phải thêm prefix `{bcrypt}` cho tất cả password hashes hiện tại trong bảng `users` và `password_history`
- **Validation**: Sau migration, không còn hash nào thiếu prefix (WHERE password_hash NOT LIKE '{%}%')

### FR-008: BouncyCastle dependency promotion [URD]
- **Actor**: System (Build)
- **Action**: Hệ thống phải promote BouncyCastle từ `testImplementation` sang `implementation` scope
- **Validation**: `org.bouncycastle:bcprov-jdk18on:1.80` available at runtime

### FR-009: Default fallback cho hash không có prefix [URD]
- **Actor**: System
- **Action**: DelegatingPasswordEncoder phải fallback sang BCryptPasswordEncoder khi gặp hash không có prefix (trường hợp migration chưa chạy)
- **Validation**: `setDefaultPasswordEncoderForMatches(BCryptPasswordEncoder)` được cấu hình

### FR-010: Giới hạn concurrent hashing [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải giới hạn số lượng concurrent password hashing operations để tránh OOM từ Argon2id memory-hard
- **Validation**: Semaphore hoặc mechanism tương đương, max concurrent = configurable (default 20)

### FR-011: Logging khi rehash thành công [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải ghi audit log khi password được rehash từ BCrypt sang Argon2id
- **Validation**: Log entry chứa userId, old algorithm, new algorithm, timestamp

### FR-012: Monitoring migration progress [ENRICHED]
- **Actor**: DevOps
- **Action**: Hệ thống phải cung cấp metric đếm số password đã migrate sang Argon2id vs còn BCrypt
- **Validation**: Micrometer counter hoặc actuator endpoint hiển thị migration progress

## 3. Non-functional Requirements

- **NFR-001**: Argon2id hash latency phải ≤ 500ms với OWASP recommended params (m=65536, t=3, p=1)
- **NFR-002**: Memory usage per concurrent hash ≤ 64MB (configurable via `memory-cost`)
- **NFR-003**: Zero downtime migration — không cần force password reset
- **NFR-004**: Backward compatibility — 100% existing BCrypt hashes verifiable

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. Tất cả 12 FRs đều có scope riêng biệt.

## 5. Enriched Domain Requirements

### Enriched FRs

- **FR-010** (Concurrent hashing limit): Argon2id dùng 64MB per hash → 20 concurrent = 1.28GB. Cần semaphore để prevent OOM với Virtual Threads unbounded platform.
- **FR-011** (Rehash audit logging): Security best practice — audit trail cho password algorithm changes. Reuse `AuditLogService` đã có.
- **FR-012** (Migration monitoring): DevOps cần visibility vào migration progress. Counter metric qua Micrometer.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| BouncyCastle | Argon2id implementation | Pure Java, đã có trong project (test scope) |
| PostgreSQL | Password hash storage | Column `password_hash VARCHAR` — đủ size cho Argon2id format |
| Flyway | DB migration | Thêm `{bcrypt}` prefix cho existing hashes |

## 6. Assumptions

- **ASM-001**: Tất cả existing password hashes đều là BCrypt format ($2a$12$...) — không có plain text hoặc algorithm khác
- **ASM-002**: BouncyCastle 1.80 hỗ trợ đầy đủ Argon2id (verified — có)
- **ASM-003**: PostgreSQL VARCHAR column đủ dài cho Argon2id hash (~128 chars + prefix)
- **ASM-004**: Virtual Threads không giới hạn số thread — cần explicit concurrency control cho memory-heavy operations

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 24/25 | FR-010: "mechanism tương đương" — cần specify rõ Semaphore |
| Đầy đủ (Completeness) | 23/25 | FR-012: Chưa specify format metric (counter name, labels) |
| Nhất quán (Consistency) | 25/25 | Không có conflict |
| Kiểm thử được (Testability) | 18/25 | FR-003: Khó test rehash-on-login trong unit test — cần integration test với real DB |
| **Tổng** | **90/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do (trích URD) | Cách cải thiện |
|---|----------|----------|-----|-------------------|---------------|
| 1 | Clarity | -1 | FR-010 | "Semaphore hoặc mechanism tương đương" — ambiguous | Specify: `java.util.concurrent.Semaphore(permits)` |
| 2 | Completeness | -2 | FR-012 | Chưa define metric name/labels | Define: `password.migration.count{algorithm=argon2id\|bcrypt}` |
| 3 | Testability | -7 | FR-003 | Rehash-on-login cần DB state change verification | Specify integration test with `@SpringBootTest` + H2 |

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | Memory pressure: Argon2id dùng 64MB per hash — với 50 concurrent logins → 3.2GB | FR-010 | Semaphore(20) + monitor JVM heap |
| 2 | Risk | 🟡 | Migration ordering: Flyway migration phải chạy TRƯỚC code deployment | FR-007 | Deploy migration trước, code sau. Hoặc `setDefaultPasswordEncoderForMatches` làm safety net |

> Không phát hiện critical issues (🔴).

## 9. Open Questions

- **OQ-001**: Argon2id `memory-cost` default nên là 65536 KB (64 MB, OWASP standard) hay thấp hơn để phù hợp staging environment? DevOps cần confirm.

> Suggestion: Dùng 65536 cho production, override thấp hơn cho staging/dev via Spring profile.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain

Authentication & Security — Password hashing subsystem

### 10.2 Flow Type

Command (single step — encode/verify password, no multi-step transaction)

### 10.3 Candidate Services
- **auth-service**: Password encoding/verification nằm hoàn toàn trong auth-service
  - Evidence: `BCryptPasswordEncoder` → `shared/config/SecurityConfig.kt:112`
  - Evidence: `PasswordEncoder` injection → `auth/application/command/TokenGenerator.kt:39`, `auth/application/PasswordPolicyService.kt:29`

### Detection Evidence
- Keyword: `BCryptPasswordEncoder` → Module: `shared/config` → File: `SecurityConfig.kt:112`
- Keyword: `PasswordEncoder` → Module: `auth/application/command` → File: `TokenGenerator.kt:39`
- Keyword: `PasswordEncoder` → Module: `auth/application` → File: `PasswordPolicyService.kt:29`
- Keyword: `bcryptStrength` → Module: `shared/config` → File: `SecurityProperties.kt:79`
- Keyword: `bcrypt-strength` → Module: `resources` → File: `application-security.yml:59`

### 10.4 External Integrations

- BouncyCastle (`bcprov-jdk18on:1.80`) — Argon2id crypto implementation
- PostgreSQL — password hash storage (users, password_history tables)
- Flyway — database migration framework

### 10.5 Required Modules

- `shared/config/` — PasswordEncoderAutoConfiguration (NEW), SecurityProperties (MODIFY), SecurityConfig (MODIFY)
- `auth/application/` — PasswordUpgradeService (NEW)
- `src/main/resources/` — application-security.yml (MODIFY)
- `src/main/resources/db/migration/` — V__add_password_hash_prefix.sql (NEW)

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | User | Submit credentials (login/register/change-password) | Receive request |
| 2 | System | — | Route to LoginHandler/RegisterHandler/PasswordPolicyService |
| 3 | System | — | Call passwordEncoder.encode() hoặc passwordEncoder.matches() |
| 4 | System | — | DelegatingPasswordEncoder detect prefix → route to correct encoder |
| 5 | System | — | Argon2id or BCrypt execute hash/verify |
| 6 | System | — | (Login only) PasswordUpgradeService.upgradeIfNeeded() — rehash nếu cần |
| 7 | System | — | Return result (JWT tokens hoặc validation result) |

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | UC-001 | PasswordEncoderAutoConfiguration | Argon2PasswordEncoder | [ADD] NEW |
| FR-002 | UC-002 | PasswordEncoderAutoConfiguration | DelegatingPasswordEncoder | [ADD] NEW |
| FR-003 | UC-002 | PasswordUpgradeService | PasswordUpgradeService | [ADD] NEW |
| FR-004 | UC-005 | SecurityProperties | PasswordProperties.algorithm | [MODIFY] |
| FR-005 | UC-005 | SecurityProperties | PasswordProperties.Argon2Properties | [ADD] nested class |
| FR-006 | UC-004 | PasswordPolicyService | PasswordPolicyService | [REUSE] no changes |
| FR-007 | DB migration | V__add_password_hash_prefix | Flyway migration | [ADD] NEW |
| FR-008 | Build | build.gradle.kts | Gradle config | [MODIFY] |
| FR-009 | UC-002 EF | PasswordEncoderAutoConfiguration | DelegatingPasswordEncoder | [ADD] NEW |
| FR-010 | Enriched | PasswordEncoderAutoConfiguration | Semaphore wrapper | [ADD] NEW |
| FR-011 | Enriched | PasswordUpgradeService | AuditLogService | [REUSE] |
| FR-012 | Enriched | PasswordUpgradeService | Micrometer counter | [ADD] NEW |

### Change Impact Map (EXTEND)

```
FR-001 → [ADD] PasswordEncoderAutoConfiguration (shared/config/) → passwordEncoder @Bean
FR-002 → [ADD] PasswordEncoderAutoConfiguration (shared/config/) → DelegatingPasswordEncoder
FR-003 → [ADD] PasswordUpgradeService (auth/application/) → upgradeIfNeeded()
FR-004 → [MODIFY] SecurityProperties.PasswordProperties (shared/config/SecurityProperties.kt) → add algorithm field
FR-005 → [MODIFY] SecurityProperties.PasswordProperties (shared/config/SecurityProperties.kt) → add Argon2Properties nested class
FR-006 → [REUSE] PasswordPolicyService (auth/application/PasswordPolicyService.kt) → no changes needed
FR-007 → [ADD] V__add_password_hash_prefix.sql (db/migration/)
FR-008 → [MODIFY] build.gradle.kts → promote BouncyCastle
FR-009 → [ADD] PasswordEncoderAutoConfiguration → setDefaultPasswordEncoderForMatches
FR-010 → [ADD] ConcurrencyLimitedPasswordEncoder or Semaphore in auto-config
FR-011 → [REUSE] AuditLogService (shared/audit/) → logEvent(PASSWORD_REHASHED)
FR-012 → [ADD] MeterRegistry counter in PasswordUpgradeService
```

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations

Feature có độ phức tạp TRUNG BÌNH — thay đổi core security component nhưng tận dụng Spring Security infrastructure sẵn có. Risk chính là memory management với Argon2id trên Virtual Threads platform.

Điểm mạnh: Tất cả consumers đã inject `PasswordEncoder` interface (không phải `BCryptPasswordEncoder` trực tiếp) → swap encoder transparent, không cần thay đổi business logic.

### Related Features / Precedents

- `2026-09-08-performance-benchmark-testing`: JMH benchmark đã confirm BCrypt là bottleneck (252ms, 95.2% login latency)
- `2026-08-21-auth-core-features`: Password change flow sử dụng PasswordPolicyService — cần verify backward compatibility

### Integration Notes

- BouncyCastle `bcprov-jdk18on:1.80` đã có trong project (test scope) — chỉ cần promote lên implementation
- Spring Security `Argon2PasswordEncoder` constructor: `(saltLength, hashLength, parallelism, memory, iterations)`
- `DelegatingPasswordEncoder` cần hash prefix format: `{id}encodedPassword`
- Flyway migration cần chạy TRƯỚC code deployment để add `{bcrypt}` prefix

### Suggested Approach

1. **Modify** `SecurityProperties.PasswordProperties` — thêm `algorithm`, `argon2` fields
2. **Create** `PasswordEncoderAutoConfiguration` — DelegatingPasswordEncoder bean
3. **Remove** `passwordEncoder()` bean từ `SecurityConfig`
4. **Create** `PasswordUpgradeService` — rehash-on-login logic
5. **Modify** `build.gradle.kts` — promote BouncyCastle
6. **Create** Flyway migration — add prefix
7. **Modify** `application-security.yml` — add argon2 config
8. **Update** JMH benchmarks — add Argon2id benchmark for comparison

### Context from Confluence Images

N/A — source là local research documents, không có Confluence images.
