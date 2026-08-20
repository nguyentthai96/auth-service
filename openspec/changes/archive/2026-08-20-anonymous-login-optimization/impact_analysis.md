# Impact Analysis: anonymous-login-optimization

_Generated: 2025-01-20 (Merged — updated to MAINTENANCE scope)_

> **Type**: MAINTENANCE — post-implementation hardening (bug fixes, tests, observability)
> **Previous**: EXTEND scope (feature fully implemented — reassessed per pre_openspec §13)
> **Direction**: Approach D — Post-Implementation Hardening (brainstorm_notes.md)

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code cho MAINTENANCE scope (bug fixes + observability).
> BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng | Fix ID |
|---|------|-----------|-----------|--------|
| 1 | [LoginHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | L155-165 | 🔴 FIX-001: Replace `anonymousJti = ""` with actual JTI from command | FIX-001 |
| 2 | [RegisterHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt) | L34-36, L90-98 | 🔴 FIX-001: Replace `anonymousJti = ""` with actual JTI + 🟡 FIX-002: Remove ThreadLocal, return `RegisterResult` | FIX-001, FIX-002 |
| 3 | [LoginCommand.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt) | L10-19 | 🔴 FIX-001: Add `anonymousTokenJti: String? = null` field | FIX-001 |
| 4 | [RegisterCommand.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt) | L9-16 | 🔴 FIX-001: Add `anonymousTokenJti: String? = null` field | FIX-001 |
| 5 | [CqrsAuthController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) | L64-97 | 🔴 FIX-001: Extract JTI from anonymous token, pass to command. 🟡 FIX-002: Update register() to use `RegisterResult` | FIX-001, FIX-002 |
| 6 | [RequestDtos.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt) | L19-27 | 🔴 FIX-001: Add `anonymousToken: String? = null` to `LoginRequestDto` + `RegisterRequestDto` | FIX-001 |
| 7 | [AnonymousSessionHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt) | Full file | 🟡 FIX-003: Add Micrometer counter `auth.anonymous.sessions.created` | FIX-003 |
| 8 | [RenewAnonymousTokenHandler.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt) | Full file | 🟡 FIX-003: Add Micrometer counter `auth.anonymous.sessions.renewed` | FIX-003 |
| 9 | [SessionPromotionService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) | Full file | 🟡 FIX-003: Add Micrometer counter `auth.anonymous.sessions.promoted` + timer `auth.anonymous.promotion.duration` | FIX-003 |
| 10 | [AnonymousRateLimitService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt) | Full file | 🟡 FIX-003: Add Micrometer counter `auth.anonymous.rate_limited` | FIX-003 |
| 11 | [AnonymousSessionDataService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) | Full file | 🟡 FIX-003: Add Micrometer counters `auth.anonymous.data.stored`, `auth.anonymous.data.size_exceeded` | FIX-003 |

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. Ghi annotation `// ←` ở điểm sửa.

#### FIX-001: JTI Blacklisting — Current (BROKEN) vs Fixed

**CURRENT (LoginHandler.handle(), L155-165):**

```
⟶ handle(command: LoginCommand)
├── ...standard auth flow... (password, mfa, session policy, token gen, recordLogin)
├── command.anonymousSessionId != null?
│   ├── YES → sessionPromotionService.promoteSession(
│   │           sessionId = command.anonymousSessionId,
│   │           userId = user.id,
│   │           anonymousJti = ""  // ← 🔴 BUG: empty string, NOT the real JTI
│   │         )
│   │   └── SessionPromotionService.promoteSession()
│   │       ├── acquireLock("anon:lock:{sessionId}", 30s)
│   │       ├── verifySession → transferData → 
│   │       ├── tokenBlacklistRepository.save(
│   │       │     TokenBlacklistEntity(tokenJti = "", ...)  // ← 🔴 empty JTI saved
│   │       │   )
│   │       └── deleteSession + releaseLock
│   └── NO → promotionResult = null
└── return LoginResult.Success(response, promotionResult)
```

**FIXED:**

```
⟶ handle(command: LoginCommand)
├── ...standard auth flow...
├── command.anonymousSessionId != null?
│   ├── YES → sessionPromotionService.promoteSession(
│   │           sessionId = command.anonymousSessionId,
│   │           userId = user.id,
│   │           anonymousJti = command.anonymousTokenJti ?: ""  // ← ✅ real JTI from command
│   │         )
│   └── NO → promotionResult = null
└── return LoginResult.Success(response, promotionResult)
```

**Controller-level JTI extraction (CqrsAuthController):**

```
⟶ login(@RequestBody request: LoginRequestDto, ...)
├── anonymousTokenJti: String? = null
├── request.anonymousToken != null?  // ← ✅ NEW: client sends full anonymous JWT
│   ├── YES → try { claims = jwtService.parseAnonymousToken(request.anonymousToken) }
│   │         anonymousTokenJti = claims.id  // ← ✅ extract JTI safely
│   └── NO → skip
├── command = LoginCommand(
│       ..., 
│       anonymousSessionId = request.anonymousSessionId,
│       anonymousTokenJti = anonymousTokenJti  // ← ✅ pass to command
│   )
└── loginHandler.handle(command)
```

#### FIX-002: RegisterHandler ThreadLocal → RegisterResult

**CURRENT (RegisterHandler, L34-36, L90-98):**

```
⟶ class RegisterHandler : CommandHandler<RegisterCommand, Authegister(meterRegistry))
└── return result

⟶ AnonymousRateLimitService.checkRateLimit() — AFTER MODIFICATION
├── ...existing logic...
├── if (exceeded) meterRegistry.counter("auth.anonymous.rate_limited").increment()  // ← ✅ NEW
└── throw/return

⟶ AnonymousSessionDataService.storeData() — AFTER MODIFICATION
├── ...existing logic...
├── meterRegistry.counter("auth.anonymous.data.stored").increment()  // ← ✅ NEW
├── if (exceeded) meterRegistry.counter("auth.anonymous.data.size_exceeded").increment()  // ← ✅ NEW
└── result
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (11 files)

| # | File | Link | Fix | Change Description |
|---|------|------|-----|-------------------|
| 1 | `LoginHandler.kt` | [LoginHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt) | FIX-001 | Replace `anonymousJti = ""` with `command.anonymousTokenJti ?: ""` |
| 2 | `RegisterHandler.kt` | [RegisterHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt) | FIX-001, FIX-002 | JTI fix + remove ThreadLocal + return `RegisterResult` |
| 3 | `LoginCommand.kt` | [LoginCommand](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginCommand.kt) | FIX-001 | Add `anonymousTokenJti: String? = null` |
| 4 | `RegisterCommand.kt` | [RegisterCommand](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterCommand.kt) | FIX-001 | Add `anonymousTokenJti: String? = null` |
| 5 | `CqrsAuthController.kt` | [CqrsAuthController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt) | FIX-001, FIX-002 | Extract JTI from token, update register() for RegisterResult |
| 6 | `RequestDtos.kt` | [RequestDtos](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt) | FIX-001 | Add `anonymousToken: String? = null` to LoginRequestDto, RegisterRequestDto |
| 7 | `AnonymousSessionHandler.kt` | [AnonymousSessionHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/AnonymousSessionHandler.kt) | FIX-003 | Inject `MeterRegistry`, add counter |
| 8 | `RenewAnonymousTokenHandler.kt` | [RenewAnonymousTokenHandler](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenHandler.kt) | FIX-003 | Inject `MeterRegistry`, add counter |
| 9 | `SessionPromotionService.kt` | [SessionPromotionService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SessionPromotionService.kt) | FIX-003 | Inject `MeterRegistry`, add counter + timer |
| 10 | `AnonymousRateLimitService.kt` | [AnonymousRateLimitService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousRateLimitService.kt) | FIX-003 | Inject `MeterRegistry`, add counter |
| 11 | `AnonymousSessionDataService.kt` | [AnonymousSessionDataService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt) | FIX-003 | Inject `MeterRegistry`, add counters |

### 🟡 Indirect Impact — auth-service (1 file)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `JwtService.kt` | [JwtService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) | `parseAnonymousToken()` called from CqrsAuthController for JTI extraction — method already exists, no modification |

### 🟠 Cross-service Impact (0 files)

No cross-service impact. All changes are internal to auth-service.

### 🟢 Shared Utilities (0 files — no modification needed)

All existing shared utilities (RedisConfig, GlobalExceptionHandler, TokenBlacklistRepository) are used as-is.

### New Files

| # | File | Purpose | Fix |
|---|------|---------|-----|
| 1 | `RegisterResult.kt` | Sealed class replacing ThreadLocal | FIX-002 |
| 2 | `AnonymousSessionIntegrationTest.kt` | Integration tests | TEST-001 |
| 3 | `SessionPromotionIntegrationTest.kt` | Integration tests | TEST-001 |
| 4 | `AnonymousRateLimitServiceTest.kt` | Unit tests | TEST-002 |
| 5 | `JwtServiceAnonymousTest.kt` | Unit tests | TEST-002 |
| 6 | `AnonymousSessionDataServiceTest.kt` | Unit tests | TEST-002 |
| 7 | `SessionPromotionServiceTest.kt` | Unit tests | TEST-002 |

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| JTI extraction from token | [JwtService.parseAnonymousToken()](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt) | 100% | REUSE | 🟢 (0 callers) | Call from CqrsAuthController — method already exists |
| RegisterResult pattern | [LoginResult.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt) | 90% | REUSE pattern | 🟢 (0 callers) | Create RegisterResult mirroring LoginResult sealed class pattern |
| Micrometer counters | N/A (not yet used in anonymous feature) | 0% | NEW | 🟢 (0 callers) | Inject MeterRegistry, standard Spring Boot Actuator integration |
| Rate limit metrics | [LoginRateLimitService](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/LoginRateLimitService.kt) | 60% | REUSE pattern | 🟢 | Follow same pattern for anonymous rate limit counters |
| Token blacklisting | [TokenBlacklistRepository](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/adapter/out/persistence/repository/Repositories.kt) | 100% | REUSE | 🟢 (existing callers unchanged) | Already used — just fixing the JTI value passed |
| Test patterns | Existing test infrastructure | 100% | REUSE | 🟢 | Follow existing test patterns in auth-service |

**Summary:** No EXTRACT needed. All fixes are in-place modifications. RegisterResult is the only new production class.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies (for MAINTENANCE fixes)

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `JwtService` | @Service (injected into CqrsAuthController) | `parseAnonymousToken(token): Claims` | Already exists — called for JTI extraction in FIX-001 |
| `MeterRegistry` | Auto-configured (Spring Boot Actuator) | `counter(name, tags...)`, `Timer.start()`, `Timer.builder()` | FIX-003: inject into 5 anonymous service classes |
| `SessionPromotionService` | @Service (injected into Login/RegisterHandler) | `promoteSession(sessionId, userId, anonymousJti)` | Already exists — receives fixed JTI from FIX-001 |
| `TokenBlacklistRepository` | JpaRepository (injected into SessionPromotionService) | `save(entity)`, `existsByTokenJti(jti)` | Already used — no changes, receives correct JTI now |
| `CommandHandler<C, R>` | Interface (eventsourcing-utils) | `handle(command): R` | FIX-002: RegisterHandler changes return type to RegisterResult |

### Config Keys (no changes)

All existing `app.security.anonymous.*` config keys remain unchanged. No new config needed for MAINTENANCE fixes.

### Error Codes (no changes)

All AUTH_040-044 error codes already exist. No new error codes for MAINTENANCE fixes.

### Metrics Names (NEW — FIX-003)

| Metric Name | Type | Tags | Service |
|-------------|------|------|---------|
| `auth.anonymous.sessions.created` | Counter | — | `AnonymousSessionHandler` |
| `auth.anonymous.sessions.renewed` | Counter | — | `RenewAnonymousTokenHandler` |
| `auth.anonymous.sessions.promoted` | Counter | `status=[SUCCESS,PARTIAL,FAILED,CONFLICT]` | `SessionPromotionService` |
| `auth.anonymous.rate_limited` | Counter | — | `AnonymousRateLimitService` |
| `auth.anonymous.data.stored` | Counter | — | `AnonymousSessionDataService` |
| `auth.anonymous.data.size_exceeded` | Counter | — | `AnonymousSessionDataService` |
| `auth.anonymous.promotion.duration` | Timer | — | `SessionPromotionService` |
| `auth.anonymous.token.generation.duration` | Timer | — | `AnonymousSessionHandler` |

### DTO Changes (FIX-001)

| DTO | Change | Decision |
|-----|--------|----------|
| `LoginRequestDto` | Add `val anonymousToken: String? = null` | MODIFY — full JWT for server-side JTI extraction |
| `RegisterRequestDto` | Add `val anonymousToken: String? = null` | MODIFY — same pattern |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `jwtService.parseAnonymousToken(token).id` | `JwtService.parseAnonymousToken()` L154+ | grep_search | ✅ EXISTS |
| `meterRegistry.counter("...")` | Micrometer auto-configured via Spring Boot Actuator | `SecurityConfig` permitAll `/actuator/**` | ✅ AVAILABLE |
| `CommandHandler<RegisterCommand, RegisterResult>` | `CommandHandler<C, R>` generic interface | eventsourcing-utils | ⚠️ Assumption: sealed class accepted as R type parameter |
| `tokenBlacklistRepository.save(entity)` | `TokenBlacklistRepository.save()` | view_file | ✅ EXISTS |
