---
type: brainstorm_notes
change: argon2id-password-encoder
date: 2026-09-08
selected_direction: "Approach 1 — DelegatingPasswordEncoder + PasswordEncoderAutoConfiguration"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Argon2id Password Encoder Migration

## Date
2026-09-08

## Context
BCrypt (strength=12) chiếm 95.2% login latency (~252ms, JMH benchmark đo được). Mục tiêu: migrate sang Argon2id (OWASP 2024 recommended) với Bean auto-config pattern cho DevOps flexibility. Research phase đã hoàn tất với 6 artifacts.

---

## Approaches Considered

### Approach 1: DelegatingPasswordEncoder + AutoConfiguration (RECOMMENDED)

```
    ┌────────────────────────┐
    │  application.yml       │
    │  algorithm: argon2id   │
    │  memory-cost: 65536    │
    └──────────┬─────────────┘
               │
    ┌──────────▼─────────────┐
    │  PasswordEncoder       │
    │  AutoConfiguration     │
    │  @ConditionalOnProp    │
    └──────────┬─────────────┘
               │
    ┌──────────▼─────────────┐
    │ DelegatingPasswordEncoder│
    │ ┌──────┐ ┌───────────┐ │
    │ │bcrypt│ │argon2id ★ │ │
    │ └──────┘ └───────────┘ │
    │ (default = ★ argon2id) │
    └──────────┬─────────────┘
               │ PasswordEncoder interface
    ┌──────────▼─────────────┐
    │  Consumers (NO CHANGE) │
    │  ├── TokenGenerator    │
    │  └── PasswordPolicy    │
    │      Service           │
    └────────────────────────┘
```

**Pros:**
- ✅ Spring Security native pattern — `DelegatingPasswordEncoder` đã built-in
- ✅ Zero consumer code change — `PasswordEncoder` interface unchanged
- ✅ `upgradeEncoding()` method built-in → rehash detection "free"
- ✅ Backward compatible — prefix-based routing `{bcrypt}` vs `{argon2id}`
- ✅ Rollback = chỉ thay `algorithm: bcrypt` trong YAML
- ✅ DevOps tunable via profiles (`dev: bcrypt`, `prod: argon2id`)

**Cons:**
- ⚠️ Flyway migration cần chạy TRƯỚC code — thêm `{bcrypt}` prefix
- ⚠️ Memory pressure 64MB/hash — cần semaphore
- ⚠️ `setDefaultPasswordEncoderForMatches()` cần set đúng BCrypt cho hashes chưa có prefix

---

### Approach 2: Custom PasswordEncoder Wrapper (NOT RECOMMENDED)

```
    ┌────────────────────────┐
    │  SmartPasswordEncoder  │
    │  implements Password   │
    │  Encoder               │
    │  ┌──────────────────┐  │
    │  │ detect algorithm  │  │
    │  │ by hash pattern   │  │
    │  └──────────────────┘  │
    └────────────────────────┘
```

**Pros:**
- Không cần Flyway migration (detect by pattern `$2a$` vs `$argon2id$`)
- Không cần `{prefix}` format

**Cons:**
- ❌ Reinvent wheel — Spring Security đã giải quyết via DelegatingPasswordEncoder
- ❌ Pattern matching fragile — BCrypt `$2a$`, `$2b$`, `$2y$` variants
- ❌ Không có `upgradeEncoding()` — phải tự implement
- ❌ Violates reuse-first principle
- ❌ Khó maintain — custom code vs community-maintained

---

### Approach 3: Spring Security PasswordEncoderFactories (PARTIAL)

```kotlin
val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
// Default: bcrypt (Spring default), supports: argon2, scrypt, pbkdf2, etc.
```

**Pros:**
- One-liner setup
- Spring maintained

**Cons:**
- ❌ Default algorithm là BCrypt (Spring convention) — không phải Argon2id
- ❌ Argon2id params hardcoded — KHÔNG tunable via YAML
- ❌ Không có `@ConditionalOnProperty` switch
- 🔶 Có thể dùng làm base, override default + params

---

## Selected Direction

### Approach 1 — DelegatingPasswordEncoder + PasswordEncoderAutoConfiguration

**Lý do:**
1. **Reuse-first**: Tận dụng Spring Security infrastructure có sẵn
2. **Zero consumer change**: `TokenGenerator` + `PasswordPolicyService` inject `PasswordEncoder` interface → transparent swap
3. **Built-in upgrade detection**: `DelegatingPasswordEncoder.upgradeEncoding()` trả `true` nếu hash không phải default encoder → rehash detection free
4. **DevOps-friendly**: `@ConditionalOnProperty` + Spring profiles = operational flexibility

---

## Deep Dive — Design Decisions

### DD-001: Nơi inject PasswordUpgradeService

**Phân tích current flow (LoginHandler.handle()):**

```
LoginHandler.handle(command)
  │
  ├── 1. userPort.findByUsernameAndActive(username)
  ├── 2. Check account lock
  ├── 3. CAPTCHA check
  ├── 4. tokenGenerator.matchesPassword(raw, hash)  ◄── VERIFY PASSWORD
  │     └── passwordEncoder.matches(raw, hash)
  ├── 5. user.resetFailedLogins() + save
  ├── 6. Password expiry check
  ├── 7. MFA checkpoint
  ├── 8. Session policy
  ├── 9. Token generation
  └── 10. Return LoginResult.Success
```

**Câu hỏi: Inject rehash ở đâu?**

```
Option A: Sau bước 4 (ngay sau verify thành công)
  ✅ Earliest possible — rehash ngay lập tức
  ⚠️ Nếu rehash fail → login vẫn thành công (fire-and-forget)
  ⚠️ Tăng latency login thêm ~500ms cho lần đầu

Option B: Sau bước 5 (sau resetFailedLogins)
  ✅ Clean — tách biệt password verify vs password upgrade
  ✅ Failure không ảnh hưởng login flow
  ★ RECOMMENDED — align với existing pattern (save user → then upgrade)

Option C: Async sau khi return LoginResult
  ✅ Không tăng login latency
  ⚠️ Phức tạp — cần access rawPassword sau khi handler return
  ❌ rawPassword không available sau return → IMPOSSIBLE
```

**Decision: Option B** — inject `passwordUpgradeService.upgradeIfNeeded()` sau line 118 (`userPort.save(user)`) — login đã thành công, rehash là best-effort bonus.

```
  ├── 5. user.resetFailedLogins() + save
  ├── 5.5 ★ passwordUpgradeService.upgradeIfNeeded(user, rawPassword) ★
  ├── 6. Password expiry check
  ...
```

### DD-002: Semaphore scope — PasswordEncoder hay PasswordUpgradeService?

**Option A: Wrap trong PasswordEncoder (ConcurrencyLimitedPasswordEncoder)**
```
DelegatingPasswordEncoder
  └── ConcurrencyLimitedPasswordEncoder (decorator)
      ├── Semaphore.acquire()
      ├── delegate.encode(raw) / delegate.matches(raw, encoded)
      └── Semaphore.release()
```
- ✅ Tất cả password operations đều throttled
- ❌ Throttle cả `matches()` — nhưng BCrypt matches() cũng CPU-heavy
- ❌ Over-protection: `matches()` cho BCrypt hash cũ không cần Semaphore
- ⚠️ Complexity: decorator pattern + DelegatingPasswordEncoder

**Option B: Semaphore chỉ trong PasswordEncoderAutoConfiguration.encode()**
```
@Bean
fun passwordEncoder(): PasswordEncoder {
    val delegate = DelegatingPasswordEncoder(...)
    return object : PasswordEncoder by delegate {
        override fun encode(raw: CharSequence): String {
            semaphore.acquire()
            try { return delegate.encode(raw) }
            finally { semaphore.release() }
        }
    }
}
```
- ✅ Chỉ throttle `encode()` (Argon2id memory-heavy)
- ✅ `matches()` vẫn unrestricted (BCrypt matches chỉ CPU-heavy, không OOM)
- ✅ Simpler — no decorator class
- ⚠️ Kotlin delegation syntax hơi tricky

**Option C: Semaphore trong PasswordUpgradeService (narrow scope)**
```
PasswordUpgradeService.upgradeIfNeeded()
  └── semaphore.acquire() → encode() → semaphore.release()
```
- ✅ Narrowest scope — chỉ throttle rehash
- ❌ MISS: Register endpoint cũng gọi encode() → không throttled
- ❌ PasswordPolicyService.changePassword() cũng gọi encode() → không throttled

**Decision: Option B** — Semaphore chỉ trong `encode()` method. Lý do:
- `encode()` là operation duy nhất tạo Argon2id hash (64MB)
- `matches()` detect prefix → route tới đúng encoder → BCrypt matches chỉ CPU, Argon2id matches cũng dùng memory nhưng verified hash → fixed size
- Thực ra `matches()` cũng cần Semaphore khi verify Argon2id hash (vẫn cần 64MB) → **Reconsider: Option A safer**

**Updated Decision: Option A (Decorator)**
```
ConcurrencyLimitedPasswordEncoder implements PasswordEncoder {
    val delegate: PasswordEncoder
    val semaphore: Semaphore

    encode(raw) → semaphore { delegate.encode(raw) }
    matches(raw, encoded) → semaphore { delegate.matches(raw, encoded) }
    upgradeEncoding(encoded) → delegate.upgradeEncoding(encoded)  // no semaphore
}
```
Lý do: Argon2id `matches()` cũng allocate 64MB để re-compute hash rồi compare. Chỉ `upgradeEncoding()` là O(1) string check → không cần semaphore.

### DD-003: PasswordPolicyService — cần thay đổi gì?

**Phân tích:**
```kotlin
// Line 57: checkPasswordHistory — matches() call
history.none { passwordEncoder.matches(newPassword, it.passwordHash) }

// Line 69: changePassword — matches() call  
!passwordEncoder.matches(oldPassword, user.passwordHash)

// Line 86: changePassword — encode() call
val newHash = passwordEncoder.encode(newPassword)!!
```

Tất cả 3 calls đều qua `PasswordEncoder` interface → **DelegatingPasswordEncoder xử lý transparent**:
- `matches("{bcrypt}$2a$12$...")` → BCryptPasswordEncoder
- `matches("{argon2id}$argon2id$v=19$...")` → Argon2PasswordEncoder
- `encode(raw)` → default encoder (Argon2id)

**Decision: ZERO changes** cho PasswordPolicyService. Đây là beauty của interface-based design.

### DD-004: Password history — cross-algorithm concern

**Scenario:**
1. User đổi password 3 lần trước migration → 3 BCrypt hashes trong history
2. Migration chạy → prefix `{bcrypt}` cho tất cả
3. User login → hash mới = `{argon2id}$...`
4. User đổi password → history check phải match cả `{bcrypt}` và `{argon2id}`

**DelegatingPasswordEncoder.matches() handles this automatically:**
- Detect prefix → route tới đúng encoder → verify → boolean result
- History entries: mix `{bcrypt}` + `{argon2id}` → tất cả verify OK

**Decision: No special handling needed.** Chỉ cần đảm bảo Flyway migration add prefix cho password_history table cùng lúc.

### DD-005: PasswordUpgradeService — cần UserEntity hay User (domain model)?

**LoginHandler dùng domain model** `User`:
```kotlin
val user = userPort.findByUsernameAndActive(command.username)  // returns User
```

Nhưng **upgrade cần save raw hash to DB** → cần `UserEntity` hoặc `UserPort`:

**Option A: PasswordUpgradeService inject UserPort**
```kotlin
fun upgradeIfNeeded(userId: Long, currentHash: String, rawPassword: String) {
    if (passwordEncoder.upgradeEncoding(currentHash)) {
        val newHash = passwordEncoder.encode(rawPassword)
        userPort.updatePasswordHash(userId, newHash)
    }
}
```
- ✅ Clean Architecture — qua port
- ⚠️ Cần thêm method `updatePasswordHash()` vào UserPort

**Option B: PasswordUpgradeService inject UserRepository trực tiếp**
```kotlin
fun upgradeIfNeeded(user: UserEntity, rawPassword: String) {
    if (passwordEncoder.upgradeEncoding(user.passwordHash)) {
        user.passwordHash = passwordEncoder.encode(rawPassword)
        userRepository.save(user)
    }
}
```
- ⚠️ Bypass Clean Architecture layer
- ✅ Simpler — reuse existing save()
- ⚠️ LoginHandler dùng `User` (domain), không phải `UserEntity` (persistence)

**Option C: Dùng UserPort.save(User) — thay đổi User domain model**
```kotlin
fun upgradeIfNeeded(user: User, rawPassword: String) {
    if (passwordEncoder.upgradeEncoding(user.passwordHash.value)) {
        val newHash = PasswordHash(passwordEncoder.encode(rawPassword))
        user.upgradePasswordHash(newHash)  // domain method
        userPort.save(user)
    }
}
```
- ✅ Clean Architecture compliant
- ✅ Domain model owns password upgrade logic
- ⚠️ Cần thêm `upgradePasswordHash()` method vào `User` domain model

**Decision: Option C** — align với Clean Architecture. User domain model owns password hash upgrade decision. LoginHandler gọi:
```kotlin
passwordUpgradeService.upgradeIfNeeded(user, command.password)
userPort.save(user)  // already called at line 118
```

Wait — LoginHandler đã gọi `userPort.save(user)` ở line 118 (resetFailedLogins). Nếu upgrade thay đổi hash trên cùng User object → save sẽ persist cả 2 changes. Tuy nhiên, cần check liệu `userPort.save()` có save passwordHash field không.

**Revised approach:**
```kotlin
// LoginHandler line ~118
user.resetFailedLogins()

// Upgrade password hash if using legacy algorithm (best-effort)
passwordUpgradeService.upgradeIfNeeded(user, command.password)

userPort.save(user)  // saves both resetFailedLogins + upgraded hash
```

### DD-006: Flyway migration — safe rollback?

```sql
-- FORWARD: Add {bcrypt} prefix
UPDATE users SET password_hash = CONCAT('{bcrypt}', password_hash)
WHERE password_hash NOT LIKE '{%}%' AND password_hash LIKE '$2a$%';

-- ROLLBACK: Remove {bcrypt} prefix (IF NEEDED)
UPDATE users SET password_hash = REPLACE(password_hash, '{bcrypt}', '')
WHERE password_hash LIKE '{bcrypt}%';
```

**Risk: After migration, if code NOT deployed yet:**
- Old code uses `BCryptPasswordEncoder` directly → `{bcrypt}$2a$12$...` sẽ FAIL
- Vì BCrypt encoder expects `$2a$12$...` (no prefix)
- **Mitigation: Deploy code FIRST** (with `setDefaultPasswordEncoderForMatches(BCryptPasswordEncoder)`)
- Hoặc: Deploy migration AFTER code → `setDefaultPasswordEncoderForMatches` handles bare hashes

**Decision: Deploy order:**
1. Code deploy (with DelegatingPasswordEncoder + `setDefaultPasswordEncoderForMatches(BCrypt)`)
2. Flyway migration runs on startup → adds prefix
3. Both bare hashes và prefixed hashes work correctly

Vì Flyway chạy on app startup → code và migration deploy cùng lúc. `setDefaultPasswordEncoderForMatches` đảm bảo bare hashes (chưa migrate) vẫn verify được.

---

## GitNexus Findings

- **PasswordEncoder consumers**: `TokenGenerator` (encode + matches), `PasswordPolicyService` (encode + matches) — chỉ 2 production consumers
- **AuthService.kt**: Đã bị xóa (refactored to CQRS) — GitNexus index stale
- **LoginHandler blast radius**: 0 upstream (leaf node) — safe to modify
- **No external callers** of `passwordEncoder()` bean ngoài Spring DI

---

## Dependency Diagram — Current vs Proposed

```
CURRENT:
    SecurityConfig ──── @Bean passwordEncoder() ──── BCryptPasswordEncoder
         │                                              │
         └─── DI ──── TokenGenerator                   │
         └─── DI ──── PasswordPolicyService            │
                          (all use PasswordEncoder      │
                           interface)                   │
                                                        │
PROPOSED:                                               │
    PasswordEncoderAutoConfiguration ──── @Bean ────────▼
         │                                DelegatingPasswordEncoder
         │                                ├── {argon2id} → Argon2PasswordEncoder ★
         │                                ├── {bcrypt}   → BCryptPasswordEncoder
         │                                └── default    → BCryptPasswordEncoder (fallback)
         │
    SecurityConfig ──── REMOVE passwordEncoder() bean
         │
    LoginHandler ──── NEW: inject PasswordUpgradeService
         │
    PasswordUpgradeService (NEW)
         ├── passwordEncoder.upgradeEncoding()
         ├── passwordEncoder.encode()
         └── (User domain model saves)
```

## Open Questions for Design Phase

- [RESOLVED] VARCHAR(255) đủ cho Argon2id hash? → YES (110 chars max)
- [RESOLVED] PasswordPolicyService cần thay đổi? → NO (interface-based)
- [RESOLVED] Semaphore scope? → encode() + matches() via decorator
- [RESOLVED] Deploy order? → Code + migration cùng lúc (setDefaultPasswordEncoderForMatches safety net)
- [OPEN] `semaphore.permits` default = 20 — có cần expose qua YAML? Recommend: YES (`app.security.password.max-concurrent-hashes: 20`)
- [OPEN] JMH benchmark mới cho Argon2id — cần chạy SAU implement để so sánh latency profile

## Open Questions for URD Analysis

Không có — source là research artifacts, đã đủ chi tiết.
