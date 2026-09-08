<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: argon2id-password-encoder

> **Change**: argon2id-password-encoder
> **Type**: EXTEND | **Flow**: Command
> **FRs**: 12/12 covered
> **Date**: 2026-09-08

---

## Phase 1: Configuration Layer (Foundation)

- [x] **Task 1: Mở rộng SecurityProperties.PasswordProperties**
  - File: [`SecurityProperties.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | Action: [MODIFY]
  - FR: FR-004 — Cấu hình algorithm qua YAML
  - FR: FR-005 — Tune Argon2id parameters qua YAML
  - FR: FR-010 — Giới hạn concurrent hashing
  - Pattern: `data class PasswordProperties` — thêm `algorithm`, `maxConcurrentHashes`, `argon2: Argon2Properties`
  - **Thay đổi cụ thể**:
    ```kotlin
    data class PasswordProperties(
        val algorithm: String = "argon2id",
        val bcryptStrength: Int = 12,
        val maxConcurrentHashes: Int = 20,
        val argon2: Argon2Properties = Argon2Properties(),
        val maxFailedAttempts: Int = 3,
        val lockDurationMinutes: Int = 15
    ) {
        data class Argon2Properties(
            val saltLength: Int = 16,
            val hashLength: Int = 32,
            val parallelism: Int = 1,
            val memoryCost: Int = 65536,
            val iterations: Int = 3
        )
    }
    ```
  - Dependencies: Không — leaf change

- [x] **Task 2: Thêm YAML configuration**
  - File: `application.yml` hoặc `application-security.yml` | Action: [MODIFY]
  - FR: FR-004, FR-005, FR-010
  - **Thay đổi cụ thể**: Thêm section `app.security.password.argon2.*` + `max-concurrent-hashes`
  - Dependencies: Task 1 (properties class phải có trước)

---

## Phase 2: Encoder Infrastructure (Core)

- [x] **Task 3: Tạo ConcurrencyLimitedPasswordEncoder**
  - File: `shared/config/ConcurrencyLimitedPasswordEncoder.kt` | Action: [NEW]
  - FR: FR-010 — Giới hạn concurrent hashing
  - Pattern: Decorator pattern wrapping `PasswordEncoder` interface
  - **Detail**: Semaphore-based wrapper cho `encode()` + `matches()`. `upgradeEncoding()` pass-through (O(1)).
  - Dependencies: Không — standalone class

- [x] **Task 4: Tạo PasswordEncoderAutoConfiguration**
  - File: `shared/config/PasswordEncoderAutoConfiguration.kt` | Action: [NEW]
  - FR: FR-001 — Mã hóa mật khẩu mới bằng Argon2id
  - FR: FR-002 — Backward compatible với BCrypt hashes
  - FR: FR-009 — Default fallback cho hash không có prefix
  - Pattern: `@Configuration` class với `@Bean passwordEncoder()`
  - **Detail**: Tạo `DelegatingPasswordEncoder` với `{argon2id}` default + `{bcrypt}` fallback. Wrap trong `ConcurrencyLimitedPasswordEncoder`. Dùng `setDefaultPasswordEncoderForMatches(BCryptPasswordEncoder)` cho bare hashes.
  - Dependencies: Task 1 (SecurityProperties), Task 3 (ConcurrencyLimited wrapper)

- [x] **Task 5: Remove passwordEncoder() bean từ SecurityConfig**
  - File: [`SecurityConfig.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | Action: [MODIFY]
  - **Thay đổi cụ thể**: Xóa lines 110-113 (`@Bean passwordEncoder()`), xóa imports `BCryptPasswordEncoder`, `PasswordEncoder`
  - Dependencies: Task 4 (new bean phải có trước — tránh missing bean)

---

## Phase 3: Migration Service (Application Layer)

- [x] **Task 6: Thêm AuditAction.PASSWORD_REHASHED**
  - File: [`AuditLogService.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt) | Action: [MODIFY]
  - FR: FR-011 — Logging khi rehash thành công
  - **Thay đổi cụ thể**: Thêm `PASSWORD_REHASHED` vào `enum class AuditAction` (line ~180)
  - Dependencies: Không — leaf change

- [x] **Task 7: Tạo PasswordUpgradeService**
  - File: `auth/application/PasswordUpgradeService.kt` | Action: [NEW]
  - FR: FR-003 — Rehash-on-login transparent
  - FR: FR-011 — Logging khi rehash thành công
  - FR: FR-012 — Monitoring migration progress
  - Pattern: `@Service` class — application layer
  - **Detail**:
    - Inject `PasswordEncoder`, `AuditLogService`, `MeterRegistry`
    - Method `upgradeIfNeeded(user: User, rawPassword: String)`
    - Check `passwordEncoder.upgradeEncoding(user.passwordHash.value)` → nếu true → encode + update `user.passwordHash`
    - Ghi audit: `AuditAction.PASSWORD_REHASHED`
    - Increment counter: `password.migration.rehash.total`
  - Dependencies: Task 4 (PasswordEncoder bean), Task 6 (AuditAction enum value)

- [x] **Task 8: Inject PasswordUpgradeService vào LoginHandler**
  - File: [`LoginHandler.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | Action: [MODIFY]
  - FR: FR-003 — Rehash-on-login transparent
  - **Thay đổi cụ thể**:
    - Thêm constructor param: `private val passwordUpgradeService: PasswordUpgradeService`
    - Thêm call SAU `user.resetFailedLogins()` (line 117), TRƯỚC `userPort.save(user)` (line 118):
      ```kotlin
      user.resetFailedLogins()
      // Upgrade password hash if using legacy algorithm (best-effort)
      passwordUpgradeService.upgradeIfNeeded(user, command.password)
      userPort.save(user) // saves both resetFailedLogins + upgraded hash
      ```
  - Dependencies: Task 7 (PasswordUpgradeService)

---

## Phase 4: Database Migration

- [x] **Task 9: Tạo Flyway migration V20**
  - File: `src/main/resources/db/migration/V20__add_password_hash_prefix.sql` | Action: [NEW]
  - FR: FR-007 — Database migration thêm prefix
  - **SQL**:
    ```sql
    -- Add {bcrypt} prefix to existing password hashes
    -- Required for DelegatingPasswordEncoder prefix-based routing
    UPDATE users
    SET password_hash = CONCAT('{bcrypt}', password_hash)
    WHERE password_hash NOT LIKE '{%}%'
      AND password_hash LIKE '$2a$%';

    UPDATE password_history
    SET password_hash = CONCAT('{bcrypt}', password_hash)
    WHERE password_hash NOT LIKE '{%}%'
      AND password_hash LIKE '$2a$%';
    ```
  - Dependencies: Không — chạy on app startup

---

## Phase 5: Build Configuration

- [x] **Task 10: Promote BouncyCastle dependency**
  - File: [`build.gradle.kts`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/build.gradle.kts) | Action: [MODIFY]
  - FR: FR-008 — BouncyCastle dependency promotion
  - **Thay đổi cụ thể**: 
    - Thêm: `implementation("org.bouncycastle:bcprov-jdk18on:1.80")`
    - Giữ: `testImplementation("org.bouncycastle:bcprov-jdk18on:1.80")` (hoặc remove vì `implementation` superset)
  - Dependencies: Không — build change

---

## Phase 6: Testing

- [x] **Task 11: Unit test PasswordEncoderAutoConfiguration**
  - File: `test/.../shared/config/PasswordEncoderAutoConfigurationTest.kt` | Action: [NEW]
  - FR: FR-001, FR-002, FR-004, FR-005, FR-009
  - **Test cases**:
    - Default algorithm = argon2id → hash has `{argon2id}` prefix
    - Switch algorithm = bcrypt → hash has `{bcrypt}` prefix
    - Bare BCrypt hash (no prefix) → matches() returns true
    - Argon2id parameters from YAML applied correctly
    - upgradeEncoding() returns true for `{bcrypt}` hash when default = argon2id

- [x] **Task 12: Unit test PasswordUpgradeService**
  - File: `test/.../auth/application/PasswordUpgradeServiceTest.kt` | Action: [NEW]
  - FR: FR-003, FR-011, FR-012
  - **Test cases**:
    - BCrypt hash → upgradeIfNeeded → hash updated to argon2id
    - Argon2id hash → upgradeIfNeeded → no change
    - Audit event recorded on upgrade
    - Micrometer counter incremented

- [x] **Task 13: Unit test ConcurrencyLimitedPasswordEncoder**
  - File: `test/.../shared/config/ConcurrencyLimitedPasswordEncoderTest.kt` | Action: [NEW]
  - FR: FR-010
  - **Test cases**:
    - encode() acquires and releases semaphore
    - matches() acquires and releases semaphore
    - upgradeEncoding() does NOT acquire semaphore
    - Concurrent operations limited to maxConcurrent

- [x] **Task 14: Integration test LoginHandler rehash**
  - File: `test/.../auth/integration/LoginRehashIntegrationTest.kt` | Action: [NEW]
  - FR: FR-003, FR-006
  - **Test cases**:
    - Login with BCrypt hash → password upgraded to argon2id in DB
    - Login with argon2id hash → no DB change
    - Password history check works cross-algorithm

- [x] **Task 15: Integration test Flyway migration**
  - File: `test/.../migration/V20MigrationTest.kt` | Action: [NEW]
  - FR: FR-007
  - **Test cases**:
    - After migration: all hashes have prefix `{%}`
    - Idempotent: running migration twice → no double prefix

---

## Self-Fix Tasks (added during implementation)

- [x] **Task 16: Fix ConcurrencyLimitedPasswordEncoder nullable signatures**
  - File: `ConcurrencyLimitedPasswordEncoder.kt` | Action: [MODIFY] | Lý do: Spring Security 6.x uses nullable params (CharSequence?, String?)

- [x] **Task 17: Add PasswordUpgradeService import to LoginHandler**
  - File: `LoginHandler.kt` | Action: [MODIFY] | Lý do: Missing import for PasswordUpgradeService

- [x] **Task 18: Fix nullable encode() return in PasswordUpgradeService**
  - File: `PasswordUpgradeService.kt` | Action: [MODIFY] | Lý do: encode() returns String? in Spring Security 6.x — PasswordHash needs non-null

- [x] **Task 19: Fix LoginHandlerTest — add missing mock params**
  - File: `LoginHandlerTest.kt` | Action: [MODIFY] | Lý do: loginEventRecorder, fingerprintService, passwordUpgradeService constructor params

---

## Dependency Graph

```
Task 1 (Properties) ──┐
                       ├── Task 4 (AutoConfig) ──── Task 5 (Remove old bean)
Task 3 (ConcurrencyLimited) ──┘                │
                                                │
Task 6 (AuditAction) ──┐                       │
                        ├── Task 7 (UpgradeService) ── Task 8 (LoginHandler)
Task 4 ────────────────┘

Task 9 (Flyway V20) ──── standalone
Task 10 (BouncyCastle) ── standalone
Task 2 (YAML) ──── depends on Task 1

Tasks 11-15 (Tests) ──── depend on Phase 1-5
```

## Execution Order (recommended)

1. Task 10 (BouncyCastle) — independent
2. Task 1 (Properties) → Task 2 (YAML)
3. Task 3 (ConcurrencyLimited) → Task 4 (AutoConfig) → Task 5 (Remove old bean)
4. Task 6 (AuditAction) → Task 7 (UpgradeService) → Task 8 (LoginHandler)
5. Task 9 (Flyway V20) — independent
6. Tasks 11-15 (Tests) — after all production code
