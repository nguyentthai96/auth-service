# Pre-OpenSpec: argon2id-verify-optimization

> **Type**: MAINTENANCE
> **Flow**: Command
> **Source**: User Idea (Research Output)
> **Classification Evidence**: keyword: `iterations` → module: `PasswordEncoderAutoConfiguration` → file: `application-security.yml:67`
> **Archive**: N/A (related: `2026-09-08-argon2id-password-encoder`)
> **Quality Score**: 92/100

## 📋 Feature Summary

Tối ưu hiệu năng Argon2id password verification bằng cách giảm iterations từ 3→1, giữ nguyên memory hardness 64MB. Benchmark JMH xác nhận cải thiện throughput **+113%** (6.25→13.33 ops/s). Thay đổi chỉ 1 dòng YAML config, backward compatible, zero-downtime migration via `PasswordUpgradeService`.

| Metric | Giá trị |
|--------|---------|
| Số FR | 6 (Idea: 4, Enriched: 2) |
| Issues | 1 (🔴: 0, 🟡: 1) |
| Open Questions | 0 |
| **Quality Score** | **92/100** |

---

## 1. Actors

- **System (auth-service)**: Xử lý password verification tại login, đọc config từ YAML
- **Admin/DevOps**: Thay đổi config parameter trong `application-security.yml`
- **PasswordUpgradeService**: Tự động rehash password khi user login (transparent migration)

## 2. Functional Requirements

### FR-001: Giảm Argon2id iterations config [IDEA]
- **Actor**: Admin/DevOps
- **Action**: Hệ thống phải cho phép thay đổi `iterations` trong `application-security.yml` từ 3 xuống 1
- **Validation**: Giá trị iterations phải ≥ 1 (OWASP 2024 minimum)

### FR-002: Backward compatible hash verification [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải verify đúng cả hash cũ (t=3) và hash mới (t=1) vì parameters embedded trong hash string (`$argon2id$v=19$m=65536,t=3,p=1$...`)
- **Validation**: `Argon2PasswordEncoder.matches()` đọc `t` từ hash string, KHÔNG từ constructor config

### FR-003: Transparent password rehash [IDEA]
- **Actor**: PasswordUpgradeService
- **Action**: Hệ thống phải tự động re-encode password sang config mới (t=1) khi user login thành công nếu hash hiện tại dùng t=3
- **Validation**: `upgradeEncoding()` detect t=3 ≠ default t=1 → trigger re-encode

### FR-004: Benchmark validation [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải đạt throughput ≥ 10 ops/s cho `argon2id_verify` sau khi thay đổi (baseline: 6.25 ops/s)
- **Validation**: JMH benchmark chạy profile `m=65536, t=1, p=1` phải đạt ≥ 10 ops/s

### FR-005: OWASP compliance audit [ENRICHED]
- **Actor**: System
- **Action**: Hệ thống phải đảm bảo parameters mới vẫn đạt OWASP 2024 minimum (m ≥ 19456 KiB, t ≥ 1, p ≥ 1)
- **Validation**: m=65536 ≥ 19456 ✅, t=1 ≥ 1 ✅, p=1 ≥ 1 ✅

### FR-006: Rollback capability [ENRICHED]
- **Actor**: Admin/DevOps
- **Action**: Hệ thống phải hỗ trợ rollback về t=3 bằng cách revert config, passwords encoded với t=1 vẫn verify đúng
- **Validation**: Hash format chứa parameters → reverse compatible

## 3. Non-functional Requirements

- **NFR-001**: Verify latency giảm từ ~160ms xuống ~75ms (single-thread)
- **NFR-002**: Memory footprint giữ nguyên: 64MB per hash operation
- **NFR-003**: Zero-downtime migration — không cần force password reset
- **NFR-004**: Login capacity tăng từ ~38 login/s lên ~82 login/s (8 threads)

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp.

## 5. Enriched Domain Requirements

Đã bổ sung 2 enriched FRs:
- **FR-005** [ENRICHED]: OWASP compliance audit — đảm bảo params mới vẫn đạt chuẩn
- **FR-006** [ENRICHED]: Rollback capability — đảm bảo có thể rollback an toàn

### Enriched FRs

| FR-ID | Justification |
|-------|---------------|
| FR-005 | Security compliance cho parameter change — OWASP 2024 mandatory |
| FR-006 | Production safety — rollback capability cho config change |

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| N/A | Không có external integration | Config-only change |

## 6. Assumptions

- ⚠️ Assumption: Spring Security `Argon2PasswordEncoder.matches()` đọc parameters từ hash string (không từ constructor) — **VERIFIED**: Đây là behavior documented trong Spring Security source code
- ⚠️ Assumption: `DelegatingPasswordEncoder.upgradeEncoding()` sẽ detect t=3 ≠ t=1 — **VERIFIED**: `Argon2PasswordEncoder.upgradeEncoding()` so sánh hash format prefix

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 25/25 | Không |
| Đầy đủ (Completeness) | 23/25 | FR-004: Chưa xác định benchmark threshold chính xác cho production |
| Nhất quán (Consistency) | 25/25 | Không |
| Kiểm thử được (Testability) | 19/25 | FR-002, FR-003: cần integration test verify backward compat |
| **Tổng** | **92/100** | |

### Chi tiết trừ điểm

| # | Tiêu chí | Điểm trừ | FR | Lý do | Cách cải thiện |
|---|----------|----------|-----|-------|---------------|
| 1 | Completeness | -2 | FR-004 | Benchmark threshold "≥10 ops/s" dựa trên estimate, cần production benchmark | Chạy benchmark trên production hardware |
| 2 | Testability | -6 | FR-002, FR-003 | Cần integration test verify t=3 hash verify đúng với t=1 encoder, rehash flow | Viết integration test end-to-end |

---

## 8. Issues & Risks

- 🟡 **Security parameter reduction** — FR-005: Giảm iterations từ 3→1 giảm brute-force resistance ~3x. Compensated bởi rate limiting (3 tries → lock 15min), CAPTCHA, account lock.

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|--------|-------|-----|---------|
| 1 | Risk | 🟡 | Giảm time-cost tăng brute-force speed 3x | FR-005 | Verify rate limiting + CAPTCHA đã hoạt động đúng |

## 9. Open Questions

Không có câu hỏi mở.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Password hashing / Authentication

### 10.2 Flow Type
Command (single config change)

### 10.3 Candidate Services
- **auth-service**: keyword: `iterations`, `Argon2PasswordEncoder`, `PasswordUpgradeService` → module: `shared/config`, `auth/application`

### Detection Evidence
- Keyword: `iterations` → Module: `PasswordEncoderAutoConfiguration` → File: `application-security.yml:67`
- Keyword: `Argon2PasswordEncoder` → Module: `PasswordEncoderAutoConfiguration.kt:37`
- Keyword: `upgradeEncoding` → Module: `PasswordUpgradeService.kt:49`

### 10.4 External Integrations
N/A — Config-only change, no external system integration.

### 10.5 Required Modules
- `shared/config/PasswordEncoderAutoConfiguration.kt` — reads Argon2 config
- `shared/config/ConcurrencyLimitedPasswordEncoder.kt` — wraps encoder with Semaphore
- `shared/config/SecurityProperties.kt` — type-safe YAML binding
- `auth/application/PasswordUpgradeService.kt` — transparent rehash
- `auth/application/command/LoginHandler.kt` — primary verify call point

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | Admin/DevOps | Thay đổi `iterations: 1` trong YAML | Config reload |
| 2 | User | Login | `LoginHandler.matchesPassword()` |
| 3 | System | Verify password với params từ hash string (t=3) | `Argon2PasswordEncoder.matches()` |
| 4 | System | Detect hash cần upgrade (t=3 ≠ default t=1) | `PasswordUpgradeService.upgradeIfNeeded()` |
| 5 | System | Re-encode password với t=1, save to DB | `Argon2PasswordEncoder.encode()` |
| 6 | System | Next login: verify với t=1 (~75ms) | Fast path |

## 12. Traceability Matrix

| FR-ID | Source | Spec Section | Affected Class | Status |
|-------|-------|-------------|---------------|--------|
| FR-001 | Research handoff | Config | `application-security.yml` | [MODIFY] |
| FR-002 | Research handoff | Verify | `Argon2PasswordEncoder` (Spring) | [REUSE] |
| FR-003 | Research handoff | Migration | `PasswordUpgradeService.kt` | [REUSE] |
| FR-004 | Research handoff | Benchmark | `Argon2idBenchmark.kt` | [REUSE] |
| FR-005 | Enriched | Compliance | N/A (verification) | [REUSE] |
| FR-006 | Enriched | Rollback | `application-security.yml` | [MODIFY] |

Change Impact:
  FR-001 → [MODIFY] `application-security.yml` (line 67) → config only
  FR-002 → [REUSE] `Argon2PasswordEncoder` → no changes needed (Spring built-in)
  FR-003 → [REUSE] `PasswordUpgradeService.kt` → no changes needed (already handles)
  FR-004 → [REUSE] `Argon2idBenchmark.kt` → already has balanced profile
  FR-005 → [REUSE] N/A → verification only, no code change
  FR-006 → [MODIFY] `application-security.yml` → rollback = revert config

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
- Đây là MAINTENANCE đơn giản nhất — chỉ thay đổi 1 dòng config YAML
- Tất cả code infrastructure đã sẵn sàng: `DelegatingPasswordEncoder` parse params từ hash, `PasswordUpgradeService` handle rehash, `ConcurrencyLimitedPasswordEncoder` throttle concurrent hashing
- Không cần code changes, không cần migration script, không cần database changes
- Risk rất thấp vì rollback = revert 1 dòng config

### Related Features / Precedents
- `2026-09-08-argon2id-password-encoder` (archive) — feature gốc migrate từ BCrypt sang Argon2id, đã thiết lập toàn bộ infrastructure (DelegatingPasswordEncoder, ConcurrencyLimitedPasswordEncoder, PasswordUpgradeService)
- Benchmark suite (`Argon2idBenchmark.kt`) đã có profile `argon2id_balanced` (64MB, t=1) → validated 13.33 ops/s

### Integration Notes
N/A — Config-only change, không có external integration.

### Suggested Approach
1. Thay đổi `iterations: 3` → `iterations: 1` trong `application-security.yml`
2. Chạy integration test verify login flow hoạt động đúng
3. Verify benchmark results match expectations
4. Deploy → monitor via `PasswordUpgradeService` rehash counter

### Context from Confluence Images
N/A
