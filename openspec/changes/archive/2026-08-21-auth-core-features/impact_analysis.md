# Impact Analysis: auth-core-features — Testing, Hardening & Legacy Cleanup (v6)

_Generated: 2026-08-26_

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [AuthService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | L131-137 (login trusted device check) | **SECURITY FIX**: Replace naive hash comparison with `User.requiresMfa(hash, ttlDays)` for TTL enforcement. Add `@Deprecated` annotation on `login()` method. |
| 2 | [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | L14-15 (KDoc comment) | **DOC FIX**: Update stale comment from "TTL enforcement deferred to future migration" to reflect current implementation. |

### 1.2 NEW Files (Tests Only)

| # | File | Type | Priority |
|---|------|------|----------|
| 1 | `src/test/kotlin/.../auth/application/command/LoginHandlerTest.kt` | Unit test | HIGH |
| 2 | `src/test/kotlin/.../auth/application/event/EventServiceTest.kt` | Unit test | HIGH |
| 3 | `src/test/kotlin/.../auth/application/event/TokenEventRecorderTest.kt` | Unit test | HIGH |
| 4 | `src/test/kotlin/.../auth/application/CaptchaVerifierTest.kt` | Unit test | MEDIUM |
| 5 | `src/test/kotlin/.../auth/adapter/out/event/OutboxPollerTest.kt` | Integration test | HIGH |
| 6 | `src/test/kotlin/.../auth/integration/AdminSessionControllerTest.kt` | Integration test | MEDIUM |
| 7 | `src/test/kotlin/.../shared/filter/IdempotencyFilterTest.kt` | Integration test | MEDIUM |
| 8 | `src/test/kotlin/.../auth/integration/MfaRecoveryCodeFlowTest.kt` | Integration test | MEDIUM |
| 9 | `src/test/kotlin/.../auth/integration/LoginRateLimitFilterTest.kt` | Integration test | MEDIUM |
| 10 | `src/test/kotlin/.../auth/integration/PasswordExpiryLoginTest.kt` | Integration test | LOW |
| 11 | `src/test/kotlin/.../auth/integration/LegacyLoginTtlTest.kt` | Integration test | HIGH |

### 1.3 Existing Test Files — NO MODIFICATION NEEDED

All 20+ existing test files remain unchanged. The v5 tasks (UserTest TTL, MfaLoginFlowIntegrationTest TTL) were already completed.

---

## 2. Call Tree — LOGIC CẦN SỬA

### 2.1 AuthService.login() — TTL Alignment (Gap 1)

```
⟶ AuthService.login(request: LoginRequest)
├── userRepository.findByUsername(request.username) → userEntity: UserEntity
├── validate credentials (BCrypt match)
├── check account status (Active/Locked)
├── check mfaEnabled && mfaMethod != "NONE"
│   ├── BEFORE (current — SECURITY GAP):
│   │   ├── val trustedHash = request.trustedDeviceHash
│   │   ├── trustedHash != null && trustedHash == user.trustedDeviceHash?
│   │   │   ├── YES → skip MFA (⚠️ NO TTL CHECK — indefinite bypass)
│   │   │   └── NO → mfaService.initiateMfa(userId, mfaMethod)
│   │
│   └── AFTER (fixed):
│       ├── val domainUser = userEntityMapper.toDomain(userEntity)  // ← NEW
│       ├── domainUser.requiresMfa(request.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)  // ← CHANGE
│       │   ├── !mfaEnabled || mfaMethod == "NONE" → return false
│       │   ├── deviceHash == null || deviceHash != trustedDeviceHash → return true
│       │   ├── trustedDeviceSetAt == null → return true (safe default)
│       │   └── setAt.plus(ttlDays, DAYS).isBefore(now()) → true/false  // ← TTL check
│       ├── TRUE → mfaService.initiateMfa(userId, mfaMethod)
│       └── FALSE → continue to token generation
├── generate auth tokens
└── return AuthResponse
```

### 2.2 SecurityProperties.kt — KDoc Update (Gap 2)

```
⟶ SecurityProperties (KDoc/comment fix only)
├── BEFORE L14-15: "TTL enforcement deferred to future migration"
└── AFTER L14-15: "TTL enforcement implemented in User.requiresMfa() via trustedDeviceSetAt (V10)"
    └── No behavioral change — documentation only
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (2 production files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `AuthService.kt` | [AuthService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | MODIFY: login() trusted device check — use User.requiresMfa() with TTL. Add @Deprecated. |
| 2 | `SecurityProperties.kt` | [SecurityProperties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | MODIFY: KDoc update only. No behavioral change. |

### 🟡 Indirect Impact — auth-service (3 files, NO changes needed)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `AuthController.kt` | [AuthController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt) | No change. Calls AuthService.login() — same interface, improved behavior. |
| 2 | `UserEntityMapper.kt` | [UserEntityMapper](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/mapper/UserEntityMapper.kt) | No change. May need to be injected into AuthService if not already. toDomain() maps trustedDeviceSetAt correctly (v5 fix). |
| 3 | `User.kt` | [User](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt) | No change. requiresMfa(hash, ttlDays) already implemented. Used by AuthService after fix. |

### 🟠 Cross-service Impact (0 files)

No cross-service impact. All changes are internal to auth-service. The legacy login endpoint (`/api/auth/login`) maintains the same request/response contract.

### 🟢 Shared Utilities (0 changes)

No shared utility changes. No new exceptions, error codes, or DTOs.

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| `User.requiresMfa(hash, ttlDays)` | [User.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt) | 100% | **REUSE** | 🟢 LOW | Call from AuthService.login() |
| `UserEntityMapper.toDomain()` | [UserEntityMapper.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/mapper/UserEntityMapper.kt) | 100% | **REUSE** | 🟢 LOW | Import into AuthService |
| `securityProperties.mfa.trustedDeviceTtlDays` | [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | 100% | **REUSE** | 🟢 LOW | Already exists, inject if needed |
| Test infrastructure (Testcontainers, MockMvc) | Existing test files | 100% | **REUSE** | 🟢 LOW | Follow existing test patterns |

### No EXTRACT Candidates

No code duplication detected that warrants extraction. The legacy path fix reuses existing domain logic.

### No NEW Components

No new production classes, interfaces, or infrastructure needed.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `UserEntityMapper` | @Component (inject into AuthService if not present) | `.toDomain(UserEntity)` → `User` | Maps all fields including `trustedDeviceSetAt` (v5 fix applied) |
| `SecurityProperties` | @ConfigurationProperties (inject into AuthService if not present) | `.mfa.trustedDeviceTtlDays` (Long = 30) | Config key: `app.security.mfa.trusted-device-ttl-days` |
| `User` (domain model) | Pure Kotlin data class | `.requiresMfa(hash, ttlDays): Boolean` | TTL-aware, backward-compatible (default ttlDays=30) |

### AuthService Constructor — Verify Injections

```kotlin
// Current AuthService constructor (to verify):
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val mfaService: MfaService,
    // ... other deps ...
    // VERIFY: Are these already injected?
    // private val userEntityMapper: UserEntityMapper     ← NEEDED for toDomain()
    // private val securityProperties: SecurityProperties  ← NEEDED for ttlDays
)
```

If not injected, add both to constructor. Spring Boot will auto-wire.

### Config Keys

| Key | Source | Value | Nơi dùng |
|-----|--------|-------|---------|
| `app.security.mfa.trusted-device-ttl-days` | SecurityProperties.kt | `30` (default) | AuthService.login() → `user.requiresMfa(hash, ttlDays)` |

### Error Codes Thrown

No new error codes. Trusted device TTL expiry on legacy path triggers existing MFA challenge flow (returns `MfaRequiredResponse`).

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| No new DTOs needed | — | — | REUSE existing |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `userEntityMapper.toDomain(user)` | `UserEntity.toDomain(): User` | UserEntityMapper.kt | ✅ Exists (maps trustedDeviceSetAt) |
| `user.requiresMfa(hash, ttlDays)` | `User.requiresMfa(String?, Long): Boolean` | User.kt | ✅ Exists (TTL-aware) |
| `securityProperties.mfa.trustedDeviceTtlDays` | `val trustedDeviceTtlDays: Long = 30` | SecurityProperties.kt | ✅ Exists |
| `mfaService.initiateMfa(userId, method)` | `MfaService.initiateMfa(Long, String): MfaRequiredResponse` | MfaService.kt | ✅ Exists (already called) |

### Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|------------|
| AuthService constructor change breaks DI | LOW | LOW | Spring auto-wires. Compile-time check. |
| UserEntityMapper not available as bean | LOW | LOW | @Component annotation — auto-scanned |
| Legacy path tests discover additional gaps | MEDIUM | LOW | Fix inline during test writing |
| Event sourcing tests reveal hidden bugs | MEDIUM | MEDIUM | Fix and document in test results |

**Overall Risk Level**: 🟢 LOW — 2 production files changed (1 security fix, 1 doc update), 11 new test files (additive only).
