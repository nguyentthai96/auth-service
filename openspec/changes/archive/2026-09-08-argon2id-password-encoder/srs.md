# Software Requirements Specification: argon2id-password-encoder

> **Change**: argon2id-password-encoder
> **Type**: EXTEND
> **Flow**: Command
> **Date**: 2026-09-08
> **FRs**: 12 (URD: 9, Enriched: 3)

---

## 1. Functional Requirements

### FR-001: Mã hóa mật khẩu mới bằng Argon2id [URD]
- **Priority**: P0
- **Actor**: System
- **Precondition**: User submit registration hoặc change-password request
- **Action**: Hệ thống encode mật khẩu bằng Argon2id (default encoder trong DelegatingPasswordEncoder)
- **Postcondition**: Hash output có prefix `{argon2id}` và format `$argon2id$v=19$m=...,t=...,p=...$salt$hash`
- **Acceptance**: Hash verify thành công khi so khớp raw password

### FR-002: Backward compatible với BCrypt hashes [URD]
- **Priority**: P0
- **Actor**: System
- **Precondition**: User đăng nhập với password hash cũ (BCrypt)
- **Action**: DelegatingPasswordEncoder detect prefix `{bcrypt}` → route tới BCryptPasswordEncoder.matches()
- **Postcondition**: Password verify thành công cho cả BCrypt và Argon2id hashes
- **Acceptance**: Login thành công với cả `{bcrypt}$2a$12$...` và `{argon2id}$argon2id$...`

### FR-003: Rehash-on-login transparent [URD]
- **Priority**: P0
- **Actor**: System
- **Precondition**: Login thành công + current hash là BCrypt (not default encoder)
- **Action**: `PasswordUpgradeService.upgradeIfNeeded()` re-encode password bằng Argon2id
- **Postcondition**: `password_hash` trong DB updated sang `{argon2id}...`
- **Acceptance**: Sau login thành công, DB query confirm hash prefix changed

### FR-004: Cấu hình algorithm qua YAML [URD]
- **Priority**: P1
- **Actor**: DevOps
- **Precondition**: application.yml chứa property `app.security.password.algorithm`
- **Action**: PasswordEncoderAutoConfiguration đọc property → tạo DelegatingPasswordEncoder với default encoder tương ứng
- **Postcondition**: New passwords encoded bằng algorithm được chỉ định
- **Acceptance**: Switch `algorithm: bcrypt` → new passwords có prefix `{bcrypt}`

### FR-005: Tune Argon2id parameters qua YAML [URD]
- **Priority**: P1
- **Actor**: DevOps
- **Precondition**: application.yml chứa `app.security.password.argon2.*`
- **Action**: Argon2PasswordEncoder khởi tạo với parameters từ YAML
- **Postcondition**: Hash output sử dụng configured params (memory-cost, iterations, parallelism)
- **Acceptance**: Thay đổi `memory-cost: 32768` → hash output chứa `m=32768`

### FR-006: Password history cross-algorithm [URD]
- **Priority**: P0
- **Actor**: System
- **Precondition**: User change password, history chứa mix BCrypt + Argon2id hashes
- **Action**: DelegatingPasswordEncoder.matches() verify new password against all history entries
- **Postcondition**: Password not in history → allow change
- **Acceptance**: `PasswordPolicyService.checkPasswordHistory()` correctly matches across algorithms

### FR-007: Database migration thêm prefix [URD]
- **Priority**: P0
- **Actor**: System (Flyway)
- **Precondition**: Existing password hashes không có prefix (`$2a$12$...`)
- **Action**: Flyway migration thêm `{bcrypt}` prefix: `CONCAT('{bcrypt}', password_hash)`
- **Postcondition**: Tất cả hashes trong `users` và `password_history` có prefix
- **Acceptance**: `SELECT COUNT(*) FROM users WHERE password_hash NOT LIKE '{%}%'` = 0

### FR-008: BouncyCastle dependency promotion [URD]
- **Priority**: P0
- **Actor**: System (Build)
- **Precondition**: BouncyCastle ở `testImplementation` scope
- **Action**: Promote sang `implementation` trong build.gradle.kts
- **Postcondition**: `org.bouncycastle:bcprov-jdk18on:1.80` available at runtime
- **Acceptance**: Application starts without `ClassNotFoundException` khi dùng Argon2PasswordEncoder

### FR-009: Default fallback cho hash không có prefix [URD]
- **Priority**: P0
- **Actor**: System
- **Precondition**: Hash không có prefix (trường hợp migration chưa chạy hoặc edge case)
- **Action**: DelegatingPasswordEncoder.setDefaultPasswordEncoderForMatches(BCryptPasswordEncoder)
- **Postcondition**: Bare BCrypt hashes (`$2a$12$...`) vẫn verify thành công
- **Acceptance**: Login với hash không prefix → không throw error

### FR-010: Giới hạn concurrent hashing [ENRICHED]
- **Priority**: P1
- **Actor**: System
- **Precondition**: Multiple concurrent password hash/verify operations
- **Action**: Semaphore-based wrapper giới hạn concurrent password operations
- **Postcondition**: Max concurrent hash operations = configurable (default 20)
- **Acceptance**: 50 concurrent logins → chỉ 20 Argon2id hash operations đồng thời, còn lại queued
- **Config**: `app.security.password.max-concurrent-hashes: 20`

### FR-011: Logging khi rehash thành công [ENRICHED]
- **Priority**: P2
- **Actor**: System
- **Precondition**: PasswordUpgradeService rehash thành công
- **Action**: Ghi audit log qua AuditLogService (existing)
- **Postcondition**: Log entry chứa userId, "PASSWORD_REHASHED", old algorithm, new algorithm
- **Acceptance**: `AuditAction.PASSWORD_REHASHED` event recorded

### FR-012: Monitoring migration progress [ENRICHED]
- **Priority**: P2
- **Actor**: DevOps
- **Precondition**: Application đang chạy với mixed hashes
- **Action**: Micrometer counter ghi nhận mỗi lần rehash thành công
- **Postcondition**: Counter `password.migration.rehash.total` incremented
- **Acceptance**: Actuator metrics endpoint hiển thị counter > 0

---

## 2. Non-Functional Requirements

| NFR | Requirement | Threshold |
|-----|------------|-----------|
| NFR-001 | Argon2id hash latency | ≤ 500ms (OWASP params) |
| NFR-002 | Memory per concurrent hash | ≤ 64MB (configurable) |
| NFR-003 | Migration strategy | Zero downtime — no forced reset |
| NFR-004 | Backward compatibility | 100% existing BCrypt hashes verifiable |
| NFR-005 | Rollback capability | algorithm: bcrypt → immediate rollback |

---

## 3. Business Rules

| # | Rule | Enforcement |
|---|------|------------|
| BR-001 | Default algorithm = Argon2id | PasswordEncoderAutoConfiguration |
| BR-002 | BCrypt hashes MUST verify without bulk migration | DelegatingPasswordEncoder |
| BR-003 | Rehash transparent to user | PasswordUpgradeService |
| BR-004 | Algorithm switch via YAML only | @ConfigurationProperties |
| BR-005 | Password history cross-algorithm | DelegatingPasswordEncoder.matches() |
| BR-006 | No forced password reset | Rehash-on-login strategy |
| BR-007 | Argon2id params tunable | SecurityProperties.Argon2Properties |

---

## 4. Traceability Matrix

| FR | Use Case | Business Rule | Component | Test |
|----|----------|--------------|-----------|------|
| FR-001 | UC-001 (Register) | BR-001, BR-007 | PasswordEncoderAutoConfiguration | Unit + JMH |
| FR-002 | UC-002 (Login legacy) | BR-002 | DelegatingPasswordEncoder | Integration |
| FR-003 | UC-002 (Login legacy) | BR-003, BR-006 | PasswordUpgradeService | Integration |
| FR-004 | UC-005 (DevOps config) | BR-004 | PasswordEncoderAutoConfiguration | Unit |
| FR-005 | UC-005 (DevOps config) | BR-007 | SecurityProperties.Argon2Properties | Unit |
| FR-006 | UC-004 (Change pwd) | BR-005 | PasswordPolicyService (no change) | Integration |
| FR-007 | DB migration | BR-002 | Flyway V__*.sql | Migration test |
| FR-008 | Build | BR-001 | build.gradle.kts | Build test |
| FR-009 | UC-002 edge case | BR-002 | DelegatingPasswordEncoder | Unit |
| FR-010 | Concurrent ops | — | ConcurrencyLimitedPasswordEncoder | Load test |
| FR-011 | Audit | — | PasswordUpgradeService + AuditLogService | Unit |
| FR-012 | Monitoring | — | PasswordUpgradeService + MeterRegistry | Unit |
