# SRS: argon2id-verify-optimization

> **Type**: MAINTENANCE | **Flow**: Command | **Version**: 1.0

## 1. Giới thiệu

### 1.1 Mục đích
Specification cho việc tối ưu hiệu năng Argon2id password verification bằng cách giảm iterations config từ 3→1.

### 1.2 Phạm vi
Thay đổi 1 dòng config YAML trong `application-security.yml`. Không thay đổi source code.

### 1.3 Tài liệu tham chiếu
- [pre_openspec.md](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/changes/argon2id-verify-optimization/pre_openspec.md)
- [brainstorm_notes.md](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/changes/argon2id-verify-optimization/brainstorm_notes.md)
- [Research: handoff_summary.md](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/argon2id-verify-optimization/handoff_summary.md)

## 2. Functional Requirements

### FR-001: Giảm Argon2id iterations config [IDEA]
- **Actor**: Admin/DevOps
- **Pre-condition**: `application-security.yml` tồn tại với `iterations: 3`
- **Action**: Thay đổi `app.security.password.argon2.iterations` từ `3` sang `1`
- **Post-condition**: `Argon2PasswordEncoder` bean được tạo với `iterations=1`
- **Validation**: `SecurityProperties.password.argon2.iterations == 1`
- **Acceptance criteria**: Config load thành công, không exception khi Spring Boot startup

### FR-002: Backward compatible hash verification [IDEA]
- **Actor**: System
- **Pre-condition**: Database chứa hashes với `t=3` (embedded trong hash string)
- **Action**: `Argon2PasswordEncoder.matches()` đọc `t` từ hash string, KHÔNG từ constructor config
- **Post-condition**: Verify đúng cả hash cũ (t=3) và hash mới (t=1)
- **Validation**: Login thành công cho user có hash t=3 sau khi config thay đổi sang t=1
- **Acceptance criteria**: Existing integration tests pass without changes

### FR-003: Transparent password rehash [IDEA]
- **Actor**: PasswordUpgradeService
- **Pre-condition**: User login thành công với hash t=3, config default là t=1
- **Action**: `upgradeEncoding()` detect `t=3 ≠ t=1` → `encode()` with t=1 → save new hash
- **Post-condition**: User's hash trong DB chuyển từ `$argon2id$v=19$m=65536,t=3,...` sang `$argon2id$v=19$m=65536,t=1,...`
- **Validation**: `password.migration.rehash.total` Micrometer counter tăng
- **Acceptance criteria**: Hash format mới xuất hiện trong DB sau login

### FR-004: Benchmark validation [IDEA]
- **Actor**: System
- **Pre-condition**: JMH benchmark suite chạy trên cấu hình `m=65536, t=1, p=1`
- **Action**: Chạy `Argon2idBenchmark.argon2id_balanced_verify`
- **Post-condition**: Throughput ≥ 10 ops/s
- **Validation**: JMH kết quả: 13.33 ± 0.374 ops/s ✅
- **Acceptance criteria**: Đã validated trong research phase

### FR-005: OWASP compliance audit [ENRICHED]
- **Actor**: System
- **Pre-condition**: Config mới: m=65536, t=1, p=1
- **Action**: Verify parameters đạt OWASP 2024 minimum
- **Post-condition**: `m=65536 ≥ 19456` ✅, `t=1 ≥ 1` ✅, `p=1 ≥ 1` ✅
- **Validation**: Manual verification against OWASP Password Storage Cheat Sheet
- **Acceptance criteria**: All 3 parameters meet OWASP minimum

### FR-006: Rollback capability [ENRICHED]
- **Actor**: Admin/DevOps
- **Pre-condition**: Config đã đổi sang `iterations: 1`, một số user hash đã re-encoded
- **Action**: Revert `iterations: 1` → `iterations: 3`, `PasswordUpgradeService` auto-rehash forward
- **Post-condition**: System hoạt động đúng với mixed hashes (t=1 và t=3)
- **Validation**: Login thành công cho cả hash t=1 và hash t=3
- **Acceptance criteria**: Rollback không yêu cầu migration script

## 3. Non-functional Requirements

| ID | Requirement | Target |
|---|---|---|
| NFR-001 | Verify latency | ≤ 80ms (single-thread) |
| NFR-002 | Memory per operation | 64MB (unchanged) |
| NFR-003 | Zero-downtime migration | No forced password reset |
| NFR-004 | Login capacity (8 threads) | ≥ 70 login/s |

## 4. FR Traceability

| FR-ID | Source | Affected Component | Status |
|---|---|---|---|
| FR-001 | Research handoff | `application-security.yml:67` | [MODIFY] |
| FR-002 | Research handoff | `Argon2PasswordEncoder` (Spring) | [REUSE] |
| FR-003 | Research handoff | `PasswordUpgradeService.kt` | [REUSE] |
| FR-004 | Research handoff | `Argon2idBenchmark.kt` | [REUSE] |
| FR-005 | Enriched | N/A (verification) | [REUSE] |
| FR-006 | Enriched | `application-security.yml` | [MODIFY] |

## 5. Constraints

- MUST maintain OWASP 2024 compliance (m ≥ 19456 KiB, t ≥ 1, p ≥ 1)
- MUST NOT change memory cost (64MB) — affects Semaphore sizing
- MUST NOT require forced password reset
- MUST NOT require database migration
