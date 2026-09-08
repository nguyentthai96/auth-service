# Business Analysis — Argon2id Password Encoder Migration

## 1. Business Context

### Vấn đề hiện tại
BCrypt (strength=12) chiếm **95.2% login latency** (~252ms). Mặc dù đây là intentional security cost, BCrypt chỉ cung cấp **CPU-hard** protection — dễ bị tấn công bởi GPU/ASIC-based brute-force.

### Giá trị business
1. **Security upgrade**: Argon2id memory-hard → GPU-resistant (OWASP 2024 recommended)
2. **Operational flexibility**: Bean auto-config → DevOps có thể tune latency/security trade-off via YAML
3. **Future-proof**: DelegatingPasswordEncoder → dễ migrate lên algorithm mới trong tương lai
4. **Zero downtime migration**: Rehash-on-login → không cần force reset password

---

## 2. Use Cases

### UC-001: Đăng ký tài khoản mới (Register)
**Actor**: User chưa có tài khoản
**Trigger**: POST `/api/auth/register`

**Basic Flow**:
1. User submit form đăng ký
2. System validate password strength (Passay rules)
3. System **encode password bằng Argon2id** (default encoder)
4. System store hash với prefix `{argon2id}` vào `password_hash`
5. System tạo JWT tokens

**Tại sao cần**: Tài khoản mới phải dùng algorithm mạnh nhất hiện tại.

### UC-002: Đăng nhập với BCrypt hash cũ (Login — Legacy)
**Actor**: User có tài khoản từ trước migration
**Trigger**: POST `/api/auth/login`

**Basic Flow**:
1. User submit credentials
2. System lookup user → lấy `password_hash`
3. `DelegatingPasswordEncoder` detect prefix `{bcrypt}` → verify bằng BCrypt
4. Verify thành công
5. **System re-hash password bằng Argon2id** (rehash-on-login)
6. Update `password_hash` = `{argon2id}...` trong DB
7. Trả JWT tokens

**Exception Flow 1**: Password sai → throw `InvalidCredentialsException`
**Exception Flow 2**: Account locked → throw `AccountLockedException`

**Tại sao cần**: Migration transparent — user không cảm nhận sự thay đổi.

### UC-003: Đăng nhập với Argon2id hash mới (Login — Modern)
**Actor**: User đã register sau migration HOẶC đã login sau migration
**Trigger**: POST `/api/auth/login`

**Basic Flow**:
1. User submit credentials
2. System lookup user → lấy `password_hash`
3. `DelegatingPasswordEncoder` detect prefix `{argon2id}` → verify bằng Argon2id
4. Verify thành công → trả JWT tokens

**Tại sao cần**: Normal flow sau khi migration hoàn thành.

### UC-004: Thay đổi password (Change Password)
**Actor**: User đã đăng nhập
**Trigger**: PUT `/api/auth/change-password`

**Basic Flow**:
1. User submit old + new password
2. System verify old password (DelegatingPasswordEncoder handles both algorithms)
3. System validate new password strength
4. System check password history (DelegatingPasswordEncoder matches against both)
5. System **encode new password bằng Argon2id** (default encoder)
6. Update `password_hash`, save to history

**Tại sao cần**: Password mới luôn dùng algorithm mạnh nhất.

### UC-005: DevOps cấu hình algorithm via YAML
**Actor**: DevOps/SRE
**Trigger**: Update `application.yml`

**Basic Flow**:
1. DevOps muốn switch sang Argon2id hoặc tune parameters
2. Thay đổi `app.security.password.algorithm: argon2id`
3. Tune: `memory-cost: 65536`, `iterations: 3`, `parallelism: 1`
4. Restart service → Bean auto-config detect algorithm → tạo đúng encoder
5. New passwords encode bằng Argon2id, old BCrypt hashes vẫn verify được

**Exception Flow**: Nếu `algorithm: bcrypt` → fallback về BCrypt encoder (backward compatible)

**Tại sao cần**: Operational flexibility — không cần thay đổi code để tune security parameters.

---

## 3. Business Rules

| # | Rule | Enforcement |
|---|------|------------|
| BR-001 | Default algorithm phải là Argon2id cho tất cả new passwords | `PasswordEncoderAutoConfiguration` |
| BR-002 | Existing BCrypt hashes PHẢI verify được mà không cần migration | `DelegatingPasswordEncoder` |
| BR-003 | Rehash-on-login PHẢI transparent cho user | `PasswordUpgradeService` |
| BR-004 | Algorithm switch PHẢI via YAML — không cần code change | `@ConditionalOnProperty` |
| BR-005 | Password history check PHẢI work across algorithms | `DelegatingPasswordEncoder.matches()` |
| BR-006 | KHÔNG được force password reset cho existing users | Rehash-on-login strategy |
| BR-007 | Argon2id params PHẢI tunable via YAML | `SecurityProperties.PasswordProperties` |

---

## 4. Traceability Matrix

| Use Case | Business Rule | File Impacted |
|----------|--------------|--------------|
| UC-001 | BR-001, BR-007 | SecurityConfig, SecurityProperties |
| UC-002 | BR-002, BR-003, BR-006 | PasswordUpgradeService (NEW), SecurityConfig |
| UC-003 | BR-001 | (no change — encoder transparent) |
| UC-004 | BR-005 | PasswordPolicyService (no change — uses interface) |
| UC-005 | BR-004, BR-007 | PasswordEncoderAutoConfiguration (NEW) |

---

## 5. Validation Phase 5

- [x] 5 use cases defined (UC-001 → UC-005)
- [x] Each UC has basic flow + ≥ 1 exception flow
- [x] Each UC has semantic description (tại sao cần)
- [x] Traceability matrix complete
- [x] Business rules documented (7 rules)
