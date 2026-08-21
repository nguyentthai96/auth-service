---
type: brainstorm_notes
change: api-response-i18n-standard
date: 2026-08-25
selected_direction: "Per-Controller MessageSource Injection (extend existing pattern)"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: API Response i18n Standard

## Date
2026-08-25

## Context
Feature extends the existing i18n infrastructure (97% complete) to cover ALL action endpoints with success message i18n. Research (feature_research) confirmed that Spring native MessageSource is the optimal approach — 90%+ already implemented, no external libraries needed. The pre_openspec identified 13 FRs (9 URD + 4 enriched), of which 8 FRs are fully implemented, 1 partially (FR-005: 10/20+ endpoints done), and 4 not started (frontend: FR-007, FR-008, FR-009; and partial frontend awareness).

This brainstorm focuses on the **remaining ~10% gap** — specifically the approach for extending success i18n to remaining controllers and the frontend header injection strategy.

## Questions Asked & Answers

- Q1: What pattern should we use for adding i18n to remaining controllers? → A: Per-Controller MessageSource Injection. 3 controllers already use this pattern (CqrsAuthController, SessionController, AccountLifecycleController) across 10 endpoints. It's proven, explicit, and consistent. See "Approaches Considered" for full analysis.

- Q2: Which remaining endpoints actually need i18n success messages vs. which are data-only? → A: **Detailed endpoint analysis below.** After reviewing all 20 controllers in the codebase, the breakdown is:
  - **Action endpoints needing i18n**: MfaController (confirmTotp, regenerateRecoveryCodes), TokenController (revokeAllSessions), SsoController (linkIdentity, unlinkIdentity), AdminSessionController (forceRevokeUserSessions), RateLimitAdminController (adminUnlock)
  - **Data-only endpoints NOT needing i18n message**: All GET endpoints (listSessions, getStats, getProviders, getRecoveryCodeCount, listDomains, listRoles, etc.), POST endpoints returning structured data (introspect, verify, login/callback that return auth tokens, createAnonymousSession, renewAnonymousToken), JWKS, storeSessionData/getSessionData/deleteSessionData (204/data responses), key-exchange
  - **Data-returning action endpoints (skip i18n)**: MfaController.updateSettings (returns MfaSettingsResponse — the updated settings ARE the confirmation)
  - **RBAC/PBAC CRUD controllers**: These return domain entities, not user-facing messages. They use `ResponseEntity.status(CREATED).body(entity)` or `ResponseEntity.ok(entity)` — no `message` field. **Skip i18n for these.** If needed later, the pattern is trivially extensible.
  - **AuthController (legacy, non-CQRS)**: Has hardcoded English in `changePassword()` and `forgotPassword()` — but is disabled when `cqrs.enabled=true` (default). **Low priority**, but should be cleaned up for consistency.
  - **InternalApiController**: Service-to-service endpoints. Consumers are other services, not humans. **Skip i18n.**

- Q3: Should we create a helper/base class for MessageSource access? → A: No. The per-controller injection pattern adds exactly 1 constructor parameter and 1 line per endpoint. Creating a base class would force inheritance (Kotlin single inheritance constraint), add coupling, and violate YAGNI. The "boilerplate" is trivially small.

- Q4: How should the frontend inject Accept-Language headers? → A: Via ky's `beforeRequest` hook in `api.ts`. The pattern is already used for Authorization header. Sync `i18n.language` (react-i18next) → `Accept-Language` header. Language change event triggers header update.

- Q5: What about the hardcoded English warning in `MfaController.regenerateRecoveryCodes()`? → A: This is Issue #1 from pre_openspec. The string `"Save these codes — they will not be shown again."` must be moved to message bundles as `auth.recovery_codes_warning`. MessageSource will render the localized version.

- Q6: Should RBAC/PBAC controllers (DomainController, RoleController, GroupController, ResourceController, PolicyController, RolePermissionController, PermissionCheckController) get i18n? → A: **No.** These are admin CRUD endpoints returning structured domain objects. They don't have user-facing `message` fields. Error handling is already covered by AuthControllerAdvice (ProblemDetail with i18n). Success responses are entity payloads. Adding `message` would change their API contract unnecessarily.

- Q7: What about the `AuthController` (legacy, non-CQRS path)? → A: It has `mapOf("message" to "Password changed successfully")` hardcoded in English. Since it's behind `@ConditionalOnProperty(cqrs.enabled=false)` and disabled by default, it's **lowest priority**. But for consistency, it should either be migrated to i18n or removed entirely (since CqrsAuthController is the active path). **Decision: clean up with i18n for completeness**, but don't block the main feature on it.

- Q8: Should `AnonymousAuthController` endpoints get success messages? → A: No — after analysis:
  - `createAnonymousSession()` → Returns `AnonymousTokenResponse` (token, sessionId, expiresIn). **Data response, no message needed.**
  - `renewAnonymousToken()` → Returns `AnonymousTokenResponse`. **Data response, no message needed.**
  - `storeSessionData()` → Returns `204 No Content`. **No body, no message possible.**
  - `getSessionData()` → Returns `SessionDataResponse`. **Data response, no message needed.**
  - `deleteSessionData()` → Returns `204 No Content`. **No body, no message possible.**
  - **Conclusion: Skip AnonymousAuthController entirely.** All endpoints are data-only or no-content.

- Q9: For borderline endpoints (TokenController.revokeAllSessions, RateLimitAdminController.adminUnlock) that return typed DTOs, should we add i18n? → A: **Yes for action confirmations, no for data-returning updates.**
  - `TokenController.revokeAllSessions()` → Returns `RevokeSessionsResponse(revokedCount, userId)`. This is an action confirmation (revoking sessions). The existing `SessionController.revokeAllSessions()` already uses i18n for the same logical operation. **Add i18n for consistency** — wrap with message or add message to response map.
  - `RateLimitAdminController.adminUnlock()` → Returns `UnlockResponse(unlocked, userId)`. This is an admin action confirmation. **Add i18n** — wrap with message.
  - `MfaController.updateSettings()` → Returns `MfaSettingsResponse(mfaEnabled, mfaMethod)`. This returns the updated settings object — the data IS the confirmation. **Skip i18n.**

## Approaches Considered

### Approach 1: Per-Controller MessageSource Injection (SELECTED ✅)
- **Description**: Inject `MessageSource` into each remaining controller's constructor. Call `messageSource.getMessage(key, args, defaultMsg, locale)` inline in each action endpoint.
- **Pros**:
  - ✅ Proven pattern — already working in CqrsAuthController (5 endpoints), SessionController (2), AccountLifecycleController (3), LoginRateLimitFilter (1) = 11 endpoints
  - ✅ Explicit — every i18n call is visible in the controller method
  - ✅ Zero abstraction overhead — no new classes, interfaces, or annotations
  - ✅ Consistent with existing codebase style
  - ✅ Easy to test — inject mock MessageSource in unit tests
  - ✅ Minimal diff — just add constructor param + 1 line per endpoint
- **Cons**:
  - ⚠️ Minor repetition of `val locale = LocaleContextHolder.getLocale()` — but each call is 1 line
  - ⚠️ Each controller that needs i18n must import MessageSource — trivial

### Approach 2: Abstract Base Controller with i18n Support
- **Description**: Create `BaseI18nController` with `protected fun resolveMessage(key, args, defaultMsg): String` that internally reads `LocaleContextHolder.getLocale()` and calls `messageSource.getMessage()`.
- **Pros**:
  - ✅ DRY for locale resolution
  - ✅ Single import point
- **Cons**:
  - ❌ Forces single inheritance (Kotlin) — controllers can't extend other bases
  - ❌ Existing controllers (CqrsAuthController, SessionController, etc.) don't extend a base
  - ❌ Refactoring existing 3 controllers to extend base = unnecessary churn
  - ❌ Some controllers (MfaController) have no natural base class relationship
  - ❌ RBAC/PBAC controllers would inherit i18n capability they don't use
  - ❌ Over-engineering for what amounts to 1-line calls

### Approach 3: AOP/Annotation-driven i18n
- **Description**: Create `@I18nMessage("auth.totp_confirmed")` annotation. Spring AOP interceptor wraps response with resolved message.
- **Pros**:
  - ✅ Very clean controller methods — just annotate
  - ✅ Centralized message resolution
- **Cons**:
  - ❌ Magic behavior — harder to debug
  - ❌ Doesn't handle parameterized messages well (args need runtime context)
  - ❌ Requires custom response wrapping that doesn't match existing `ResponseEntity<Map<String, Any>>` pattern
  - ❌ Inconsistent with existing explicit `getMessage()` pattern
  - ❌ Overkill for ~7 remaining endpoints
  - ❌ AOP proxy complications in Kotlin

## Selected Direction

**Approach 1: Per-Controller MessageSource Injection** — extend the proven pattern from CqrsAuthController/SessionController/AccountLifecycleController.

**Reasoning**: The existing pattern is battle-tested across 11 endpoints in 4 components. The remaining work is ~7 action endpoints across 5 controllers. Each change is a surgical addition (constructor param + getMessage call). No new abstractions, no framework-level changes, no magic. The code reads exactly as it should: "resolve message, build response."

## Pre-classifications (preliminary)
- Feature type: EXTEND (97% infrastructure done, extending coverage to remaining endpoints)
- Flow type: Command (cross-cutting concern applied at controller layer)
- Affected modules:
  - `auth/adapter/in/web` — MfaController, TokenController, SsoController, AdminSessionController, RateLimitAdminController, AuthController (legacy)
  - `resources/messages` — new success message keys in auth-messages.properties + auth-messages_vi.properties
  - `admindashboard` — api.ts (Accept-Language header), I18nProvider.tsx (language sync), form components (remove hardcoded error messages)

## Codebase Investigation Findings

### Architecture Overview — i18n Pipeline

```
                           REQUEST FLOW
    ┌──────────┐    Accept-Language: vi-VN    ┌──────────────────────┐
    │  Client   │─────────────────────────────▶│ ClientMetadataFilter │
    │ (React)   │    X-App-Version: 1.0.0     │ (MDC logging)        │
    │           │    X-Client-Platform: web    │ HIGHEST_PRECEDENCE+10│
    └──────────┘                              └──────────┬───────────┘
                                                         │
                                              ┌──────────▼───────────┐
                                              │ LoginRateLimitFilter  │
                                              │ (own resolveLocale()) │
                                              │ → i18n ProblemDetail  │
                                              └──────────┬───────────┘
                                                         │
                                              ┌──────────▼───────────┐
                                              │ AcceptHeaderLocale    │
                                              │ Resolver (I18nConfig) │
                                              │ → LocaleContextHolder │
                                              │ supportedLocales:     │
                                ┌──────────▼─────────┐
                    │                 │ ErrorCodeBase.     │
                    │                 │ description        │
                    │                 │ (English hardcoded) │
                    │                 └────────────────────┘
                    │
         ┌──────────▼───────────┐
         │ ContentLanguageFilter │
         │ (LOWEST_PRECEDENCE-10)│
         │ sets Content-Language  │
         │ response header        │
         └──────────┬────────────┘
                    │
    ┌───────────────▼──────────┐
    │  Client receives:        │
    │  Success → message field │
    │  Error → ProblemDetail   │
    │  Header: Content-Language│
    └──────────────────────────┘
```

### Full Endpoint Classification

```
CONTROLLERS WITH I18N DONE (10 endpoints):
  ✅ CqrsAuthController        — register, logout, changePassword, forgotPassword, switchDomain (5)
  ✅ SessionController          — revokeSession, revokeAllSessions (2)
  ✅ AccountLifecycleController — deactivateAccount, requestDeletion, cancelDeletion (3)
  ✅ LoginRateLimitFilter       — rate limit error response (1 — filter, not controller)

CONTROLLERS NEEDING I18N (7 action endpoints):
  🔲 MfaController             — confirmTotp (1), regenerateRecoveryCodes (1) = 2
  🔲 TokenController           — revokeAllSessions (1) = 1
  🔲 SsoController             — linkIdentity (1), unlinkIdentity (1) = 2
  🔲 AdminSessionController    — forceRevokeUserSessions (1) = 1
  🔲 RateLimitAdminController  — adminUnlock (1) = 1
  Total: 7 action endpoints

CONTROLLERS — DATA-ONLY (skip i18n):
  ⬜ MfaController             — verifyMfa (returns auth response), setupTotp (returns data),
                                 resendOtp (returns data), getRecoveryCodeCount (returns data),
                                 updateSettings (returns MfaSettingsResponse — data IS confirmation)
  ⬜ TokenController           — introspect (returns RFC 7662 data), jwks (returns JWKS data)
  ⬜ SsoController             — ssoCallback (returns auth response), getProviders (returns list)
  ⬜ AdminSessionController    — listAllActiveSessions (returns paginated data),
                                 getSessionStats (returns stats)
  ⬜ RateLimitAdminController  — getLockInfo (returns data)
  ⬜ AnonymousAuthController   — ALL (data/token responses + 204s)
  ⬜ CaptchaController         — getChallenge (returns challenge data)
  ⬜ KeyExchangeController     — exchange (returns key data)
  ⬜ InternalApiController     — ALL (service-to-service, machine consumers)
  ⬜ RBAC controllers (5)      — ALL (CRUD entity responses)
  ⬜ PBAC PolicyController     — ALL (CRUD entity responses)
  ⬜ RolePermissionController  — ALL (CRUD entity responses)
  ⬜ SessionController         — getActiveSessions (returns list data)

LOW-PRIORITY CLEANUP:
  🟡 AuthController (legacy)   — changePassword, forgotPassword → hardcoded English strings
                                 (disabled by default via @ConditionalOnProperty cqrs.enabled=false)
```

### New Message Keys Needed

```properties
# auth-messages.properties (English)
auth.totp_confirmed=TOTP setup confirmed successfully
auth.recovery_codes_warning=Save these codes — they will not be shown again.
auth.sessions_revoked_for_user=All sessions revoked for user
auth.sso_identity_linked=SSO identity linked successfully
auth.sso_identity_unlinked=SSO identity unlinked successfully
auth.rate_limit_unlocked=Rate limit locks cleared successfully
auth.all_user_sessions_revoked=All sessions revoked

# auth-messages_vi.properties (Vietnamese)
auth.totp_confirmed=Xác nhận cài đặt TOTP thành công
auth.recovery_codes_warning=Lưu lại các mã này — chúng sẽ không được hiển thị lại.
auth.sessions_revoked_for_user=Đã thu hồi tất cả phiên cho người dùng
auth.sso_identity_linked=Liên kết danh tính SSO thành công
auth.sso_identity_unlinked=Hủy liên kết danh tính SSO thành công
auth.rate_limit_unlocked=Đã xóa khóa giới hạn tốc độ thành công
auth.all_user_sessions_revoked=Đã thu hồi tất cả phiên
```

### Response Pattern Analysis

```
Current patterns in controllers needing changes:

MfaController.confirmTotp():
  CURRENT:  return ResponseEntity.ok(mapOf("success" to true))
  CHANGE:   return ResponseEntity.ok(mapOf("success" to true,
              "message" to messageSource.getMessage("auth.totp_confirmed", null, "TOTP setup confirmed", locale)))
  PATTERN:  Add "message" to existing response map

MfaController.regenerateRecoveryCodes():
  CURRENT:  return ResponseEntity.ok(mapOf("codes" to codes, "count" to codes.size,
              "warning" to "Save these codes — they will not be shown again."))
  CHANGE:   "warning" value → messageSource.getMessage("auth.recovery_codes_warning", null, "Save these codes...", locale)
  PATTERN:  Replace hardcoded English with i18n call (Issue #1 from pre_openspec)

TokenController.revokeAllSessions():
  CURRENT:  return ResponseEntity.ok(RevokeSessionsResponse(revokedCount = count, userId = userId))
  CHANGE:   return ResponseEntity.ok(mapOf("revokedCount" to count, "userId" to userId,
              "message" to messageSource.getMessage("auth.all_user_sessions_revoked", null, "All sessions revoked", locale)))
  PATTERN:  Wrap DTO fields in map + add message
  NOTE:     SessionController.revokeAllSessions already uses i18n — consistency

SsoController.linkIdentity():
  CURRENT:  return ResponseEntity.ok(mapOf("linked" to true))
  CHANGE:   ADD "message" to messageSource.getMessage("auth.sso_identity_linked", null, "SSO identity linked", locale)
  PATTERN:  Add "message" to existing response map

SsoController.unlinkIdentity():
  CURRENT:  return ResponseEntity.ok(mapOf("linked" to false))
  CHANGE:   ADD "message" to messageSource.getMessage("auth.sso_identity_unlinked", null, "SSO identity unlinked", locale)
  PATTERN:  Add "message" to existing response map

AdminSessionController.forceRevokeUserSessions():
  CURRENT:  return ResponseEntity.ok(mapOf("message" to "All sessions revoked for user $userId", ...))
  CHANGE:   "message" → messageSource.getMessage("auth.sessions_revoked_for_user", arrayOf(userId), "All sessions revoked for user $userId", locale)
  PATTERN:  Replace hardcoded English with i18n call (already has "message" key in response)

RateLimitAdminController.adminUnlock():
  CURRENT:  return ResponseEntity.ok(UnlockResponse(unlocked = true, userId = userId))
  CHANGE:   return ResponseEntity.ok(mapOf("unlocked" to true, "userId" to userId,
              "message" to messageSource.getMessage("auth.rate_limit_unlocked", null, "Rate limit locks cleared", locale)))
  PATTERN:  Wrap DTO fields in map + add message
```

### Refined Endpoint List (after analysis):

```
DEFINITE i18n additions (7 action endpoints):
  1. MfaController.confirmTotp()                    → add "message" to response map
  2. MfaController.regenerateRecoveryCodes()         → change "warning" to i18n
  3. SsoController.linkIdentity()                    → add "message" to response map
  4. SsoController.unlinkIdentity()                  → add "message" to response map
  5. AdminSessionController.forceRevokeUserSessions() → change hardcoded "message" to i18n
  6. TokenController.revokeAllSessions()             → wrap in map + add message
  7. RateLimitAdminController.adminUnlock()           → wrap in map + add message

SKIP (data-returning, no user message):
  ⬜ MfaController.updateSettings()                 → returns MfaSettingsResponse (data IS confirmation)
  ⬜ MfaController.verifyMfa()                      → returns auth token response
  ⬜ MfaController.setupTotp()                      → returns TOTP setup data
  ⬜ MfaController.resendOtp()                      → returns MFA required response
  ⬜ All GET endpoints                              → returns query data
  ⬜ AnonymousAuthController (all)                  → data/204 responses
  ⬜ InternalApiController (all)                    → machine consumers
  ⬜ RBAC/PBAC controllers (all)                    → entity CRUD responses

LOW-PRIORITY (behind feature flag):
  🟡 AuthController.changePassword()                → hardcoded English, disabled by default
  🟡 AuthController.forgotPassword()                → hardcoded English, disabled by default
```

### Frontend Changes Required

```
FRONTEND — api.ts (header injection):
  1. Read i18n.language from react-i18next
  2. Set Accept-Language header in ky's beforeRequest hook
  3. Set X-App-Version from build config
  4. Set X-Client-Platform = "web"

FRONTEND — I18nProvider.tsx (language sync):
  1. On i18n.changeLanguage event → update Accept-Language header
  2. Ensure header matches i18n current language

FRONTEND — Form cleanup (remove hardcoded error messages):
  1. JwtSignInForm.tsx — show problem.detail from server instead of client error strings
  2. SignInPageForm.tsx — show problem.detail from server instead of client error strings
  3. Any other forms with hardcoded error message templates
```

## GitNexus Findings (if explored)
- Related processes: Not explored via GitNexus (grep-based analysis sufficient for this EXTEND feature)
- Key symbols:
  - `MessageSource` — injected in 3 controllers (CqrsAuthController, SessionController, AccountLifecycleController) + 1 filter (LoginRateLimitFilter)
  - `LocaleContextHolder.getLocale()` — used to get current locale in all i18n-enabled controllers
  - `AuthControllerAdvice.resolveMessage()` — central error i18n resolution
  - `I18nConfig` — CompositeMessageSource bean configuration
  - `DatabaseMessageSource` — Caffeine-cached DB message resolution
  - `ContentLanguageFilter` — sets Content-Language header on all /api/** responses
  - `ClientMetadataFilter` — extracts X-App-Version, X-Client-Platform → MDC
- Architecture insights: The i18n pipeline is fully functional for error responses (100% coverage via AuthControllerAdvice + ContentLanguageFilter). Success response i18n uses per-controller messageSource.getMessage() pattern — proven across 10 endpoints. No framework gaps.

## Open Questions for Design Phase
- [OPEN] Should `TokenController.revokeAllSessions()` and `RateLimitAdminController.adminUnlock()` change their return type from typed DTO to `Map<String, Any>` (breaking API contract), or should we add a `message` field to the DTO? Decision: wrap in map for consistency with other controller patterns. Design phase should confirm.
- [OPEN] Should `AuthController` (legacy non-CQRS) be cleaned up with i18n or removed entirely? It's behind a feature flag set to false by default. Design phase should decide scope — cleanup (add i18n) vs. removal (reduce dead code).
- [RESOLVED] Per-controller vs. base class vs. AOP → Per-controller injection selected (proven pattern, zero abstraction overhead).
- [RESOLVED] Which endpoints need i18n → 7 action endpoints identified, data-only endpoints excluded.
- [RESOLVED] MfaController.updateSettings() → Skip i18n (data response, settings ARE the confirmation).
- [RESOLVED] AnonymousAuthController → Skip entirely (all data/204 responses).
- [RESOLVED] RBAC/PBAC controllers → Skip entirely (entity CRUD, no user-facing messages).
- [RESOLVED] InternalApiController → Skip (machine consumers, not humans).

## Open Questions for URD Analysis
- None — all URD questions resolved in pre_openspec and research phases.
