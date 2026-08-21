# Impact Analysis: api-response-i18n-standard

_Generated: 2026-08-25 | Mode: DELTA (archive: 2026-08-22)_

> **[CHANGED] vs 2026-08-22**: LoginRateLimitFilter i18n gap is NOW RESOLVED. The remaining backend work is extending success i18n to 7 action endpoints across 5 controllers. Frontend changes still pending.
> **Classification**: EXTEND | **FR count**: 13

---

## 1. Core Files — NƠI SỬA

> Chỉ liệt kê files CẦN MODIFY code. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng |
|---|------|-----------|-----------|
| 1 | [MfaController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt) | L16-18, L49-53, L94-103 | Constructor — inject `MessageSource`. `confirmTotp()` — add i18n message. `regenerateRecoveryCodes()` — replace hardcoded "warning" with i18n |
| 2 | [TokenController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt) | L17-21, L56-60 | Constructor — inject `MessageSource`. `revokeAllSessions()` — add i18n message to response |
| 3 | [SsoController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt) | L16-19, L43-46, L49-53 | Constructor — inject `MessageSource`. `linkIdentity()` — add i18n message. `unlinkIdentity()` — add i18n message |
| 4 | [AdminSessionController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt) | L22-24, L80-86 | Constructor — inject `MessageSource`. `forceRevokeUserSessions()` — replace hardcoded English "message" with i18n |
| 5 | [RateLimitAdminController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt) | L18-20, L56-58 | Constructor — inject `MessageSource`. `adminUnlock()` — add i18n message to response |
| 6 | [auth-messages.properties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages.properties) | L48+ (append) | Add 7 new success message keys for remaining action endpoints |
| 7 | [auth-messages_vi.properties](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages_vi.properties) | L48+ (append) | Add matching Vietnamese translations for 7 new keys |

## [REMOVED] Files No Longer Needing Changes (vs 2026-08-22)

| # | File | Previous Action | Current Status |
|---|------|----------------|----------------|
| 1 | `LoginRateLimitFilter.kt` | MODIFY — inject MessageSource, add resolveLocale(), set Content-Language | ✅ ALREADY DONE — uses `messageSource.getMessage("auth.rate_limited", ...)` with `resolveLocale()` |
| 2 | `auth-messages.properties` L33 | MODIFY — update `auth.rate_limited` template | ✅ ALREADY DONE — template has `{0}`, `{1}` placeholders |
| 3 | `auth-messages_vi.properties` L33 | MODIFY — matching Vietnamese template | ✅ ALREADY DONE |

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. KHÔNG dùng Mermaid.

#### `MfaController.confirmTotp()` (L49-53) — ADD i18n message

```
⟶ POST /api/auth/mfa/totp/confirm (Accept-Language: vi)
├── getCurrentUserId() → userId
├── mfaService.confirmTotp(userId, request.code)
└── ResponseEntity.ok(mapOf("success" to true))
    └── [CHANGE] ADD "message" → messageSource.getMessage("auth.totp_confirmed", null, "TOTP setup confirmed", locale)
```

#### `MfaController.regenerateRecoveryCodes()` (L94-103) — REPLACE hardcoded warning

```
⟶ POST /api/auth/mfa/recovery-codes/regenerate
├── getCurrentUserId() → userId
├── mfaService.generateRecoveryCodes(userId) → codes
└── ResponseEntity.ok(mapOf("codes" to codes, "count" to codes.size, "warning" to ...))
    └── [CHANGE] "warning" value: HARDCODED "Save these codes..."
        → messageSource.getMessage("auth.recovery_codes_warning", null, "Save these codes...", locale)  // ← Issue #1 from pre_openspec
```

#### `TokenController.revokeAllSessions()` (L56-60) — ADD i18n message

```
⟶ POST /api/auth/sessions/{userId}/revoke-all
├── authService.revokeAllSessions(userId) → count
└── ResponseEntity.ok(RevokeSessionsResponse(revokedCount = count, userId = userId))
    └── [CHANGE] Wrap in map: mapOf("revokedCount" to count, "userId" to userId,
          "message" to messageSource.getMessage("auth.all_user_sessions_revoked", null, "All sessions revoked", locale))
```

#### `SsoController.linkIdentity()` (L43-46) — ADD i18n message

```
⟶ POST /api/auth/sso/link
├── getCurrentUserId() → userId
├── ssoAdapter.linkIdentity(userId, request.code, request.provider, request.redirectUri)
└── ResponseEntity.ok(mapOf("linked" to true))
    └── [CHANGE] ADD "message" → messageSource.getMessage("auth.sso_identity_linked", null, "SSO identity linked", locale)
```

#### `SsoController.unlinkIdentity()` (L49-53) — ADD i18n message

```
⟶ DELETE /api/auth/sso/unlink/{provider}
├── getCurrentUserId() → userId
├── ssoAdapter.unlinkIdentity(userId, provider)
└── ResponseEntity.ok(mapOf("linked" to false))
    └── [CHANGE] ADD "message" → messageSource.getMessage("auth.sso_identity_unlinked", null, "SSO identity unlinked", locale)
```

#### `AdminSessionController.forceRevokeUserSessions()` (L80-86) — REPLACE hardcoded message

```
⟶ DELETE /api/admin/sessions/user/{userId}
├── loginSessionService.getActiveSessions(userId) → sessions
├── loginSessionService.revokeAllSessions(userId, "ADMIN_FORCE_REVOKE")
└── ResponseEntity.ok(mapOf("message" to "All sessions revoked for user $userId", "revokedCount" to sessions.size))
    └── [CHANGE] "message" value: HARDCODED "All sessions revoked for user $userId"
        → messageSource.getMessage("auth.sessions_revoked_for_user", arrayOf(userId), "All sessions revoked for user $userId", locale)
```

#### `RateLimitAdminController.adminUnlock()` (L56-58) — ADD i18n message

```
⟶ DELETE /api/admin/rate-limit/locks/{userId}
├── rateLimitService.adminUnlock(userId)
└── ResponseEntity.ok(UnlockResponse(unlocked = true, userId = userId))
    └── [CHANGE] Wrap in map: mapOf("unlocked" to true, "userId" to userId,
          "message" to messageSource.getMessage("auth.rate_limit_unlocked", null, "Rate limit locks cleared", locale))
```

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (5 controller files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `MfaController.kt` | [MfaController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt) | Inject `MessageSource` constructor param. Modify `confirmTotp()` + `regenerateRecoveryCodes()` response maps to include i18n messages. |
| 2 | `TokenController.kt` | [TokenController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt) | Inject `MessageSource` constructor param. Modify `revokeAllSessions()` return type from typed DTO to map with i18n message. |
| 3 | `SsoController.kt` | [SsoController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt) | Inject `MessageSource` constructor param. Add `"message"` to `linkIdentity()` + `unlinkIdentity()` response maps. |
| 4 | `AdminSessionController.kt` | [AdminSessionController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt) | Inject `MessageSource` constructor param. Replace hardcoded English `"message"` in `forceRevokeUserSessions()` with i18n call. |
| 5 | `RateLimitAdminController.kt` | [RateLimitAdminController](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt) | Inject `MessageSource` constructor param. Modify `adminUnlock()` return type from typed DTO to map with i18n message. |

### 🟡 Indirect Impact — auth-service (2 files — message bundles only)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `auth-messages.properties` | [en bundle](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages.properties) | Append 7 new success message keys (auth.totp_confirmed, auth.recovery_codes_warning, etc.) |
| 2 | `auth-messages_vi.properties` | [vi bundle](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/resources/messages/auth-messages_vi.properties) | Matching Vietnamese translations for 7 new keys |

### 🟠 Cross-service Impact (0 files — backend)

No cross-service impact. All changes are auth-service controller-layer modifications. `MessageSource` bean already configured project-wide via `I18nConfig`.

> ⚠️ Frontend (admindashboard) impacts listed separately in `fe_tasks.md`.

### 🟢 Shared Utilities (0 files)

No shared utility extractions needed. The `messageSource.getMessage()` pattern is a direct Spring API call — no custom abstraction.

---

## 4. Reuse Map

> Follows `reuse_rules_compact.md` Step 2 Decision Framework.

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| MessageSource injection + getMessage() | `CqrsAuthController.kt` (5 endpoints), `SessionController.kt` (2), `AccountLifecycleController.kt` (3) | 100% | **REUSE** (pattern) | 🟢 (0 callers affected) | Constructor-inject `MessageSource`, call `getMessage(key, args, defaultMsg, locale)` inline — proven pattern |
| `LocaleContextHolder.getLocale()` locale resolution | `CqrsAuthController.kt:89`, `SessionController.kt:44`, `AccountLifecycleController.kt:40` | 100% | **REUSE** (pattern) | 🟢 (0 callers affected) | Use `val locale = LocaleContextHolder.getLocale()` — Spring standard |
| Map-based response with "message" field | `SessionController.revokeSession()`, `CqrsAuthController.register()` | 100% | **REUSE** (pattern) | 🟢 (0 callers affected) | `ResponseEntity.ok(mapOf("field" to value, "message" to resolvedMessage))` |
| `getCurrentUserId()` private helper | `MfaController.kt:105-107`, `SsoController.kt:55-57` | 100% | **REUSE** (existing — no change) | 🟢 (0 callers affected) | Already exists in each controller — no extraction needed |

> No EXTRACT operations needed. All 7 endpoint changes follow the identical proven pattern.
> Match = 100% → REUSE. Each change is a surgical 3-line addition per endpoint (import locale, resolve message, add to response).

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

> Agent đọc section này → đủ info bắt tay code, KHÔNG cần search thêm.

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `MessageSource` | Interface (Spring, inject into constructor) | `getMessage(code, args, defaultMessage, locale)` | CompositeMessageSource from I18nConfig: DB → file chain |
| `LocaleContextHolder` | Static utility (Spring) | `getLocale()` | Thread-local locale from AcceptHeaderLocaleResolver (DispatcherServlet sets it) |
| `MfaService` | Service (already injected in MfaController) | `confirmTotp()`, `generateRecoveryCodes()` | MFA operations |
| `SsoAdapter` | Service (already injected in SsoController) | `linkIdentity()`, `unlinkIdentity()` | SSO operations |
| `LoginSessionService` | Service (already injected in AdminSessionController) | `getActiveSessions()`, `revokeAllSessions()` | Session management |
| `MfaRateLimitService` | Service (already injected in RateLimitAdminController) | `adminUnlock()` | Rate limit admin |
| `AuthService` | Service (already injected in TokenController) | `revokeAllSessions()` | Token revocation |

### Config Keys

| Key | Source | Example Value | Nơi dùng |
|-----|--------|--------------|---------|
| Supported locales | `I18nConfig.kt:42-43` | `[en, vi]` | `AcceptHeaderLocaleResolver` → `LocaleContextHolder` |
| Default locale | `I18nConfig.kt:43` | `Locale.ENGLISH` | Fallback when no `Accept-Language` match |

### Error Codes Thrown

No new error codes thrown. All 44 existing error codes (AUTH_001-AUTH_044) already have i18n support via `AuthControllerAdvice`.

### DTO / Response Type Changes

| Endpoint | Current Return Type | Change | Breaking? |
|---|---|---|---|
| MfaController.confirmTotp() | `Map<String, Boolean>` | Add `"message"` key to map | ❌ No (additive) |
| MfaController.regenerateRecoveryCodes() | `Map<String, Any>` | Replace hardcoded "warning" value with i18n | ❌ No (same key, localized value) |
| TokenController.revokeAllSessions() | `RevokeSessionsResponse` (typed DTO) | Change to `Map<String, Any>` with same fields + `"message"` | ⚠️ Minor — same fields, adds `message` |
| SsoController.linkIdentity() | `Map<String, Boolean>` | Add `"message"` key to map | ❌ No (additive) |
| SsoController.unlinkIdentity() | `Map<String, Boolean>` | Add `"message"` key to map | ❌ No (additive) |
| AdminSessionController.forceRevokeUserSessions() | `Map<String, Any>` | Replace hardcoded "message" value with i18n | ❌ No (same key, localized value) |
| RateLimitAdminController.adminUnlock() | `UnlockResponse` (typed DTO) | Change to `Map<String, Any>` with same fields + `"message"` | ⚠️ Minor — same fields, adds `message` |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `messageSource.getMessage()` | `MessageSource.getMessage(String, Array<Any>?, String, Locale)` | Spring Framework | ✅ Verified — used in 11 existing endpoints |
| `LocaleContextHolder.getLocale()` | `LocaleContextHolder.getLocale(): Locale` | Spring Framework | ✅ Verified — used in all existing i18n controllers |
| `ResponseEntity.ok(mapOf(...))` | `ResponseEntity.ok(T): ResponseEntity<T>` | Spring Framework | ✅ Verified — standard pattern |
