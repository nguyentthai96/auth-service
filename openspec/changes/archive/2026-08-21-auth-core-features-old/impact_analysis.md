# Impact Analysis: auth-core-features — Trusted Device Hardening & Cleanup (v5)

_Generated: 2026-08-25_

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [UserEntityMapper.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/mapper/UserEntityMapper.kt) | L28 (toDomain), L49 (toEntity) | **CRITICAL**: Map `trustedDeviceSetAt` in both directions — currently MISSING |
| 2 | [User.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt) | L73-76 (requiresMfa) | Replace hash-only check with TTL-aware logic using `ttlDays` parameter |
| 3 | [MfaService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | Trusted device save section (after trustedDeviceHash assignment) | Set `trustedDeviceSetAt = Instant.now()` alongside hash save |
| 4 | [LoginHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | requiresMfa call site | Pass `securityProperties.mfa.trustedDeviceTtlDays` param |
| 5 | [PasswordPolicyService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | After passwordChangedAt assignment | Clear `trustedDeviceHash` + `trustedDeviceSetAt` on pwd change |
| 6 | [UserPersistenceAdapter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/UserPersistenceAdapter.kt) | User construction (if manual mapping used) | Add `trustedDeviceSetAt` to domain model construction |
| 7 | [TokenStorePersistenceAdapter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt) | L46-49 (revokeAllForUser) | Fix stale TODO — delegate to `refreshTokenRepository.revokeAllByUserId()` |

### 1.2 NEW Files

No new files needed. V10 migration already applied. UserEntity field already exists.

### 1.3 Test Files to MODIFY

| # | File | Change |
|---|------|--------|
| 1 | `src/test/kotlin/com/ntt/authservice/auth/domain/model/UserTest.kt` | Add TTL expiry test scenarios for `requiresMfa()` |
| 2 | `src/test/kotlin/com/ntt/authservice/auth/integration/MfaLoginFlowIntegrationTest.kt` | Add TTL-aware trusted device integration test |

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. Ghi annotation `// ←` ở điểm quan trọng.

### 2.1 LoginHandler.handle() — TTL Check (Gap 1b)

```
⟶ LoginHandler.handle(command: LoginCommand)
├── userPort.findByUsername(command.username) → user: User
│   └── UserPersistenceAdapter → UserEntityMapper.toDomain()
│       └── trustedDeviceSetAt = this.trustedDeviceSetAt  // ← MUST FIX MAPPER (Gap 1a)
├── validate credentials (BCrypt match)
├── unlockIfExpired(now)
├── check account status (Active/Locked/etc.)
├── captcha check (if required)
├── password expiry check
├── user.requiresMfa(command.trustedDeviceHash, securityProperties.mfa.trustedDeviceTtlDays)  // ← MODIFY: add ttlDays param
│   ├── !mfaEnabled || mfaMethod == "NONE" → return false
│   ├── deviceHash == null || deviceHash != trustedDeviceHash → return true
│   ├── trustedDeviceSetAt == null → return true  // ← NEW: no timestamp = expired
│   └── setAt.plus(ttlDays, DAYS).isBefore(now()) → return true/false  // ← NEW: TTL check
├── MFA required? → generateMfaResult(userId, mfaMethod)
└── MFA not required → generate full auth response
```

### 2.2 MfaService.verifyMfa() — Save Timestamp (Gap 1b)

```
⟶ MfaService.verifyMfa(mfaToken, code, trustDevice, deviceHash)
├── jwtService.parseMfaToken(mfaToken) → claims (userId, method, exp)
├── verify code (OTP via Redis or TOTP via library)
├── rateLimitService.resetCounters(userId)
├── trustDevice && !deviceHash.isNullOrBlank()?
│   ├── user = userRepository.findById(userId)
│   ├── user.trustedDeviceHash = deviceHash
│   ├── user.trustedDeviceSetAt = Instant.now()  // ← NEW LINE (Gap 1b)
│   ├── userRepository.save(user)
│   └── auditLogService.logEvent(TRUSTED_DEVICE_SET)
├── auditLogService.logEvent(MFA_VERIFY_SUCCESS)
└── return authResponseBuilder(userId)
```

### 2.3 PasswordPolicyService.changePassword() — Clear Trust (Gap 1b)

```
⟶ PasswordPolicyService.changePassword(userId, oldPassword, newPassword, domainId)
├── user = userRepository.findById(userId)
├── validate old password (BCrypt match)
├── validatePasswordStrength(newPassword, domainId)
├── checkPasswordHistory(userId, newPassword, historyCount)
├── passwordEncoder.encode(newPassword) → newHash
├── save to password_history
├── user.passwordHash = newHash
├── user.passwordChangedAt = Instant.now()
├── user.trustedDeviceHash = null     // ← NEW (clear device trust on pwd change)
├── user.trustedDeviceSetAt = null    // ← NEW (clear device trust on pwd change)
├── userRepository.save(user)
├── pruneHistory(userId, historyCount)
└── auditLogService.logEvent(PASSWORD_CHANGED)
```

### 2.4 TokenStorePersistenceAdapter.revokeAllForUser() — Fix (Gap 2)

```
⟶ TokenStorePersistenceAdapter.revokeAllForUser(userId)
├── BEFORE: return 0  // ← stale TODO
└── AFTER:  return refreshTokenRepository.revokeAllByUserId(userId)  // ← FIX
            └── @Modifying @Query("UPDATE RefreshTokenEntity SET revoked=true WHERE userId=:userId AND revoked=false")
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (7 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `UserEntityMapper.kt` | [UserEntityMapper](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/mapper/UserEntityMapper.kt) | MODIFY: map trustedDeviceSetAt. Called by UserPersistenceAdapter. CRITICAL for data flow. |
| 2 | `User.kt` | [User](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt) | MODIFY: TTL logic in requiresMfa(). Called by LoginHandler.handle(). |
| 3 | `MfaService.kt` | [MfaService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | MODIFY: 1 line added (set timestamp). Called by MfaController. |
| 4 | `LoginHandler.kt` | [LoginHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | MODIFY: pass ttlDays param. Called by CqrsAuthController. |
| 5 | `PasswordPolicyService.kt` | [PasswordPolicyService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | MODIFY: 2 lines added (clear trust). Called by AuthController, CqrsAuthController. |
| 6 | `UserPersistenceAdapter.kt` | [UserPersistenceAdapter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/UserPersistenceAdapter.kt) | MODIFY: add trustedDeviceSetAt to manual mapping (if used). Implements UserPort. |
| 7 | `TokenStorePersistenceAdapter.kt` | [TokenStorePersistenceAdapter](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/TokenStorePersistenceAdapter.kt) | MODIFY: fix stale TODO. Implements TokenStore port. |

### 🟡 Indirect Impact — auth-service (4 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `AuthService.kt` | [AuthService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | No code change. Uses UserEntity.trustedDeviceHash independently. |
| 2 | `CqrsAuthController.kt` | [CqrsAuthController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) | No change. Calls LoginHandler.handle() with same LoginCommand signature. |
| 3 | `RevokeSessionsHandler.kt` | [RevokeSessionsHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt) | No change. Calls tokenStore.revokeAllForUser() via port. Now receives actual count. |
| 4 | `SecurityProperties.kt` | [SecurityProperties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | No change. `trustedDeviceTtlDays = 30` already exists. Now activated by LoginHandler. |

### 🟠 Cross-service Impact (0 files)

No cross-service impact. All changes are internal to auth-service. Trusted device is server-side only.

### 🟢 Shared Utilities (0 changes)

No shared utility changes. All exception classes and error codes already exist.

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| `requiresMfa()` hash compare | [User.kt:73-76](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/domain/model/User.kt#L73-L76) | 100% | **REUSE** (extend) | 🟢 LOW (1 caller) | Add TTL param + check |
| `trustedDeviceHash` save | [MfaService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt) | 100% | **REUSE** (extend) | 🟢 LOW (1 caller) | Add timestamp set |
| `changePassword()` flow | [PasswordPolicyService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt) | 100% | **REUSE** (extend) | 🟢 LOW (2 callers) | Add trust clear |
| `UserEntityMapper` mapping | [UserEntityMapper.kt:15-53](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/persistence/mapper/UserEntityMapper.kt#L15-L53) | 100% | **REUSE** (extend) | 🟢 LOW | Add 1 field mapping |
| `revokeAllByUserId()` repo method | [Repositories.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt) | 100% | **REUSE** | 🟢 LOW | Wire into adapter |
| `SecurityProperties.mfa.trustedDeviceTtlDays` | [SecurityProperties.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt) | 100% | **REUSE** | 🟢 LOW | Already exists, now activated |

### No EXTRACT Candidates

All changes in v5 are small field/parameter extensions. No code duplication warrants extraction.

### No NEW Components

All necessary components exist. Only modifications to existing components.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `UserRepository` | JpaRepository (injected in MfaService, PasswordPolicyService) | `findById(Long)`, `save(UserEntity)` | Used for trusted device save + password change |
| `RefreshTokenRepository` | JpaRepository (injected in TokenStorePersistenceAdapter) | `revokeAllByUserId(Long): Int` | Already exists in Repositories.kt |
| `SecurityProperties` | @ConfigurationProperties (injected in LoginHandler) | `.mfa.trustedDeviceTtlDays` (Long = 30) | Already exists, currently UNUSED |
| `AuditLogService` | @Service (injected in MfaService, PasswordPolicyService) | `logEvent(userId, action, ...)` | `TRUSTED_DEVICE_SET` action already exists in `AuditAction` enum |
| `java.time.temporal.ChronoUnit` | JDK (import) | `ChronoUnit.DAYS` | Already imported in `User.kt` (L7) |

### Config Keys

| Key | Source | Value | Nơi dùng |
|-----|--------|-------|---------|
| `app.security.mfa.trusted-device-ttl-days` | SecurityProperties.kt | `30` (default) | `LoginHandler.handle()` → `user.requiresMfa(hash, ttlDays)` |

### Error Codes Thrown

No new error codes. Trusted device TTL expiry triggers existing MFA challenge flow (returns `MfaRequiredResponse`).

| Error Code | Condition | Nơi throw |
|-----------|-----------|-----------|
| N/A | Expired device trust | Not thrown — `requiresMfa()` returns `true` → normal MFA flow |

### DTO Reuse Check

| DTO cần | Existing DTO | Match % | Decision |
|---|---|---|---|
| No new DTOs needed | — | — | REUSE existing |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `user.requiresMfa(hash, ttlDays)` | `User.requiresMfa(deviceHash: String?, ttlDays: Long): Boolean` | User.kt:73 (to be modified) | ✅ Backward-compatible |
| `refreshTokenRepository.revokeAllByUserId(userId)` | `fun revokeAllByUserId(@Param("userId") userId: Long): Int` | Repositories.kt | ✅ Already exists |
| `securityProperties.mfa.trustedDeviceTtlDays` | `val trustedDeviceTtlDays: Long = 30` | SecurityProperties.kt | ✅ Already exists |
| `user.trustedDeviceSetAt = Instant.now()` | `var trustedDeviceSetAt: Instant? = null` | UserEntity.kt:55 (already exists) | ✅ Standard JPA field |
| `auditLogService.logEvent(userId, TRUSTED_DEVICE_SET, ...)` | `AuditLogService.logEvent(...)` | AuditLogService.kt | ✅ Already exists |
