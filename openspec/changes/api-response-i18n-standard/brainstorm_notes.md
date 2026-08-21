---
type: brainstorm_notes
change: api-response-i18n-standard
date: 2026-08-27
selected_direction: "Frontend-Only Completion (backend 100% done, close remaining frontend gaps)"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: API Response i18n Standard

## Date
2026-08-27

## Context
This is the **third iteration** of the api-response-i18n-standard brainstorm. Previous brainstorm (2026-08-25) identified 7 action endpoints needing i18n and established the per-controller MessageSource injection pattern. Since then, all 7 remaining backend endpoints have been implemented — bringing backend coverage from 10/18 to **18/18 action endpoints**. The feature is now at **99%+ backend completion**.

**Current state (DELTA from 2026-08-25 brainstorm):**
- [CHANGED] Backend: 18/18 action endpoints now use `messageSource.getMessage()` (was 10/18)
- [CHANGED] All 7 endpoints identified in previous brainstorm are DONE: MfaController (2), SsoController (2), AdminSessionController (1), RateLimitAdminController (1), TokenController (1)
- [CHANGED] All issues from previous archives RESOLVED (MfaController.regenerateRecoveryCodes now uses MessageSource, LoginRateLimitFilter fully i18n-enabled)
- [UNCHANGED] Frontend FR-007, FR-008, FR-009 still not started
- [UNCHANGED] Message bundles complete: 77 lines each (en + vi), 55+ keys covering all error codes + success messages
- [UNCHANGED] All infrastructure fully implemented (I18nConfig, DatabaseMessageSource, ContentLanguageFilter, ClientMetadataFilter, AuthControllerAdvice)

**This brainstorm focuses on**: The remaining **frontend-only work** (~0.5 developer-day) and the question of legacy `AuthController` cleanup.

## Questions Asked & Answers

- Q1: What is the remaining scope now that backend is 100% done? → A: **Frontend-only**. Three FRs remain:
  - FR-007: Frontend sends `Accept-Language` header via ky's `beforeRequest` hook
  - FR-008: Frontend sends `X-App-Version`, `X-Client-Platform` headers
  - FR-009: Frontend removes hardcoded error messages, shows `problem.detail` directly
  All three are admindashboard (React) changes, not auth-service backend changes.

- Q2: Is the admindashboard frontend in this auth-service workspace? → A: **No.** The admindashboard is a separate frontend project. The `api.ts` and form components referenced in pre_openspec are in the admindashboard repo, not under `services/auth-service/`. This means:
  - The auth-service OpenSpec should document the frontend requirements as **external integration specs**
  - The actual frontend implementation may need a separate task/ticket
  - Backend acceptance criteria can be verified independently with integration tests (using `Accept-Language` header directly)

- Q3: Should the OpenSpec generate frontend implementation code or just specs? → A: **Specs only for frontend contract.** The OpenSpec should:
  1. Define the request header contract (what headers the frontend must send)
  2. Define the response contract (what fields the frontend should display)
  3. Provide example code snippets for `api.ts` and form components
  4. Backend tests should verify the server-side behavior independently

- Q4: What about the legacy `AuthController` (non-CQRS, disabled by default)? → A: **Three options analyzed:**

  | Option | Effort | Risk | Benefit |
  |--------|--------|------|---------|
  | A. Add i18n to AuthController | 0.5h | LOW | Consistency for edge-case users |
  | B. Remove AuthController entirely | 1h | MEDIUM (breaking if someone enables non-CQRS) | Reduces dead code |
  | C. Leave as-is (lowest priority) | 0h | NONE | No effort but inconsistency remains |

  **Decision: Option C — Leave as-is.** Reasoning:
  - AuthController is disabled by default (`@ConditionalOnProperty(cqrs.enabled=false)`)
  - CqrsAuthController is the active path and already fully i18n-enabled
  - The hardcoded English in AuthController (`"Password changed successfully"`, `"If the email exists..."`) only affects users who explicitly disable CQRS mode
  - Cleaning up or removing requires touching the feature-flagged code path — risk > benefit
  - If needed later, the pattern is trivially extensible (add `messageSource` constructor param + `getMessage()`)

- Q5: Should we add integration tests for i18n behavior in this OpenSpec iteration? → A: **Yes — backend acceptance tests.** The backend is complete, so tests should verify:
  1. Error response returns localized `ProblemDetail.detail` based on `Accept-Language` header
  2. Success response returns localized `message` field based on `Accept-Language` header
  3. Missing `Accept-Language` → fallback to English
  4. Unsupported locale (e.g., `ja`) → fallback to English
  5. `Content-Language` header present on all responses
  These can all be written as `@WebMvcTest` or `MockMvc` integration tests.

- Q6: Are there any missing message keys or bundle gaps? → A: **No.** After checking both bundles:
  - `auth-messages.properties` (en): 77 lines, 55+ keys
  - `auth-messages_vi.properties` (vi): 77 lines, 55+ keys
  - All AuthErrorCode entries (AUTH_001 through AUTH_062) have corresponding message keys
  - All success message keys used in controllers exist in both bundles
  - All E2EE error keys (e2ee_*) present
  - All anonymous session error keys (anonymous_*) present
  **Zero gaps.** Message bundles are complete.

- Q7: Is there any concern about the `Content-Language` header being set twice (ContentLanguageFilter + AuthControllerAdvice)? → A: **No concern.** Both set the same value from `LocaleContextHolder.getLocale()`. This is defense-in-depth:
  - `ContentLanguageFilter` (LOWEST_PRECEDENCE - 10) ensures 100% coverage on ALL `/api/**` responses
  - `AuthControllerAdvice.setContentLanguageHeader()` ensures error responses have it even if filter ordering changes
  - `LoginRateLimitFilter` explicitly sets it for rate-limit responses (pre-DispatcherServlet)
  - Last-write-wins with identical value → no conflict

- Q8: Should the `LoginRateLimitFilter` be refactored to use `LocaleContextHolder` instead of its own `resolveLocale()`? → A: **No.** The `LoginRateLimitFilter` runs BEFORE Spring MVC's `DispatcherServlet`, which means `LocaleContextHolder` is not populated at that point. The filter correctly implements its own locale resolution (`Locale.LanguageRange.parse()` + supported locales whitelist = [en, vi]) that mirrors `I18nConfig` settings. This is intentional and correct.

## Approaches Considered

### Approach 1: Frontend Contract Specification Only (SELECTED ✅)
- **Description**: The OpenSpec for this iteration specifies the frontend contract (headers to send, fields to display) but does NOT generate frontend code. Backend acceptance tests verify server-side behavior. Frontend implementation is documented as an external integration requirement.
- **Pros**:
  - ✅ Backend is 100% done — can be shipped/tested independently
  - ✅ Frontend is a different repo — shouldn't be in auth-service OpenSpec
  - ✅ Clear separation of concerns
  - ✅ Backend integration tests can verify all i18n behavior
  - ✅ Frontend task can be tracked separately
- **Cons**:
  - ⚠️ Frontend work needs to be tracked/assigned separately
  - ⚠️ Full E2E verification requires both backend + frontend to be complete

### Approach 2: Full-Stack Implementation (including frontend code)
- **Description**: Generate both backend tests and frontend implementation code (api.ts changes, form cleanup) in the auth-service OpenSpec.
- **Pros**:
  - ✅ Complete feature in one OpenSpec
  - ✅ Full traceability
- **Cons**:
  - ❌ Frontend code is in a different repository — can't be applied from auth-service
  - ❌ Mixing concerns across repos
  - ❌ Frontend dev workflow is different (React/Vite vs Spring Boot)

### Approach 3: Backend Tests + Frontend Spec Document
- **Description**: Generate backend integration tests AND a separate frontend integration spec document (Markdown) that frontend developers can follow.
- **Pros**:
  - ✅ Backend verified with tests
  - ✅ Frontend has clear spec to follow
  - ✅ Keeps repos separated but documented
- **Cons**:
  - ⚠️ Extra documentation artifact to maintain
  - ⚠️ Frontend may deviate from spec without enforcement

## Selected Direction

**Approach 1: Frontend Contract Specification Only** — Focus the OpenSpec on backend completion (tests) and document frontend requirements as integration specs within the design document.

**Reasoning**: The backend is 100% complete with all 18 action endpoints using `messageSource.getMessage()`, all message bundles populated (en + vi), and all infrastructure filters/configs in place. The remaining work is entirely frontend (admindashboard repo). Generating frontend code in the auth-service OpenSpec is architecturally wrong — it crosses repo boundaries. The design document should specify the frontend contract clearly (request headers, response consumption patterns, example code snippets) so a frontend developer or separate task can implement it.

## Pre-classifications (preliminary)
- Feature type: EXTEND (backend infrastructure 100% done, extending with tests + frontend contract)
- Flow type: Command (cross-cutting concern applied at controller + filter layer)
- Affected modules:
  - `auth-service` (backend — **COMPLETE**, needs integration tests only):
    - `shared/config/I18nConfig.kt` — ✅ DONE
    - `shared/exception/GlobalExceptionHandler.kt` — ✅ DONE
    - `shared/i18n/DatabaseMessageSource.kt` — ✅ DONE
    - `auth/adapter/in/web/*Controller.kt` — ✅ DONE (18/18 action endpoints)
    - `auth/adapter/in/web/filter/*Filter.kt` — ✅ DONE (ContentLanguage, ClientMetadata, LoginRateLimit)
    - `resources/messages/auth-messages*.properties` — ✅ DONE (77 lines en + vi)
  - `admindashboard` (frontend — **NOT STARTED**, external repo):
    - `api.ts` — inject Accept-Language, X-App-Version, X-Client-Platform headers
    - `I18nProvider.tsx` — sync language change → header update
    - Form components — remove hardcoded error messages, show server messages directly

## Codebase Investigation Findings

### Current Backend Architecture (FULLY IMPLEMENTED)

```
                           REQUEST FLOW (100% IMPLEMENTED)
    ┌──────────┐    Accept-Language: vi-VN    ┌──────────────────────┐
    │  Client   │─────────────────────────────▶│ ClientMetadataFilter │ ✅
    │ (React)   │    X-App-Version: 1.0.0     │ X-App-Version → MDC  │
    │           │    X-Client-Platform: web    │ HIGHEST_PREC + 10    │
    └──────────┘                              └──────────┬───────────┘
                                                         │
                                              ┌──────────▼───────────┐
                                              │ LoginRateLimitFilter  │ ✅
                                              │ own resolveLocale()   │
                                              │ → i18n ProblemDetail  │
                                              │ messageSource.getMsg()│
                                              └──────────┬───────────┘
                                                         │
                                              ┌──────────▼───────────┐
                                              │ AcceptHeaderLocale    │ ✅
                                              │ Resolver (I18nConfig) │
                                              │ supported: [en, vi]   │
                                              │ → LocaleContextHolder │
                                              └──────────┬───────────┘
                                                         │
                                    ┌────────────────────┼────────────────────┐
                                    │                    │                    │
                         ┌──────────▼──┐      ┌─────────▼────┐    ┌─────────▼──────┐
                         │  SUCCESS     │      │  ERROR        │    │  FILTER ERROR   │
                         │  Controller  │      │  Controller   │    │  LoginRateLimit │
                         │  getMessage()│      │  → Exception  │    │  getMessage()   │
                         │  18/18 ✅    │      │  → Advice     │    │  1/1 ✅         │
                         └──────────┬───┘      └─────────┬────┘    └─────────┬──────┘
                                    │                    │                    │
                                    │          ┌─────────▼────────┐          │
                                    │          │AuthControllerAdv.│ ✅       │
                                    │          │resolveMessage()  │          │
                                    │          │→MessageSource    │          │
                                    │          │→ProblemDetail    │          │
                                    │          └─────────┬────────┘          │
                                    │                    │                    │
                         ┌──────────▼────────────────────▼────────────────────▼──┐
                         │           CompositeMessageSource (I18nConfig)          │ ✅
                         │    ┌─────────────────┐    ┌──────────────────────┐    │
                         │    │DatabaseMessage   │───▶│ ReloadableResource   │    │
                         │    │Source (Caffeine  │miss│ BundleMessageSource  │    │
                         │    │5min TTL,max500)  │    │ auth-messages_*.prop │    │
                         │    └─────────────────┘    └──────────┬───────────┘    │
                         │                                      │miss            │
                         │                           ┌──────────▼───────────┐    │
                         │                           │ ErrorCodeBase.       │    │
                         │                           │ description (EN)     │    │
                         │                           └──────────────────────┘    │
                         └──────────────────────────────────────────────────────┘
                                    │
                         ┌──────────▼───────────┐
                         │ ContentLanguageFilter │ ✅
                         │ LOWEST_PREC - 10      │
                         │ Content-Language: vi   │
                         │ all /api/** responses  │
                         └──────────┬────────────┘
                                    │
                         ┌──────────▼──────────────┐
                         │  Client receives:        │
                         │  Success → message field │ (localized)
                         │  Error → ProblemDetail   │ (localized detail)
                         │  Header: Content-Language│
                         └──────────────────────────┘
```

### Complete Endpoint Coverage Map (18/18 + 1 filter = ALL DONE)

```
CONTROLLERS WITH I18N ✅ COMPLETE (18 action endpoints):
  ✅ CqrsAuthController         — register, logout, changePassword, forgotPassword, switchDomain (5)
  ✅ SessionController           — revokeSession, revokeAllSessions (2)
  ✅ AccountLifecycleController  — deactivateAccount, requestDeletion, cancelDeletion (3)
  ✅ MfaController               — confirmTotp, regenerateRecoveryCodes (2) [NEW since 2026-08-25]
  ✅ SsoController               — linkIdentity, unlinkIdentity (2) [NEW since 2026-08-25]
  ✅ AdminSessionController      — forceRevokeUserSessions (1) [NEW since 2026-08-25]
  ✅ RateLimitAdminController    — adminUnlock (1) [NEW since 2026-08-25]
  ✅ TokenController             — revokeAllSessions (1) [NEW since 2026-08-25]
  ✅ LoginRateLimitFilter        — writeRateLimitResponse (1 — filter, not controller)

DATA-ONLY ENDPOINTS (skip i18n — correct decision):
  ⬜ MfaController              — verifyMfa, setupTotp, resendOtp, getRecoveryCodeCount, updateSettings
  ⬜ TokenController            — introspect, jwks
  ⬜ SsoController              — ssoCallback, getProviders
  ⬜ AdminSessionController     — listAllActiveSessions, getSessionStats
  ⬜ RateLimitAdminController   — getLockInfo
  ⬜ AnonymousAuthController    — ALL (data/token responses + 204s)
  ⬜ CaptchaController          — getChallenge
  ⬜ KeyExchangeController      — exchange
  ⬜ InternalApiController      — ALL (service-to-service)
  ⬜ RBAC/PBAC controllers      — ALL (CRUD entity responses)
  ⬜ SessionController          — getActiveSessions

LOW-PRIORITY (disabled by default):
  🟡 AuthController (legacy)    — changePassword, forgotPassword → hardcoded English
                                  Decision: LEAVE AS-IS (see Q4 analysis)
```

### Message Bundle Coverage (COMPLETE)

```
auth-messages.properties (English — 77 lines, 55+ keys):
  ✅ Authentication errors: 6 keys (invalid_credentials, account_locked, token_expired, etc.)
  ✅ CAPTCHA errors: 2 keys (captcha_required, captcha_failed)
  ✅ Policy errors: 2 keys (policy_evaluation_failed, write_not_allowed)
  ✅ MFA errors: 4 keys (mfa_code_invalid, mfa_token_expired, mfa_max_attempts, mfa_rate_limited)
  ✅ SSO errors: 3 keys (sso_token_invalid, sso_user_not_provisioned, sso_identity_conflict)
  ✅ Password errors: 2 keys (password_policy_violation, password_expired)
  ✅ Rate limiting & session: 2 keys with interpolation (rate_limited {0}{1}, session_limit {0})
  ✅ Success messages: 17 keys (login, logout, register, password_changed, session_revoked, etc.)
  ✅ E2EE errors: 10 keys (e2ee_time_skew, e2ee_version_unknown, etc.)
  ✅ Anonymous session errors: 5 keys (anonymous_session_expired, anonymous_data_limit, etc.)

auth-messages_vi.properties (Vietnamese — 77 lines, 55+ keys):
  ✅ Mirrors English bundle exactly — all 55+ keys translated
  ✅ MessageFormat placeholders match English bundle ({0}, {1})
  ✅ No orphan keys, no missing translations
```

## GitNexus Findings (if explored)
- Related processes: Not explored via GitNexus (grep-based codebase scan sufficient)
- Key symbols verified via grep:
  - `messageSource.getMessage` → 19 occurrences across 9 controllers + 1 filter (ALL action endpoints covered)
  - `ContentLanguageFilter` → confirmed implementation at `auth/adapter/in/web/filter/ContentLanguageFilter.kt` (44 lines)
  - `ClientMetadataFilter` → confirmed implementation at `auth/adapter/in/web/filter/ClientMetadataFilter.kt` (57 lines)
  - `I18nConfig` → confirmed at `shared/config/I18nConfig.kt` (66 lines, CompositeMessageSource chain)
  - `AuthControllerAdvice` → confirmed at `shared/exception/GlobalExceptionHandler.kt` (128 lines, MessageSource + ProblemDetail)
- Architecture insights: The i18n pipeline is **fully functional** for both success and error responses with 100% Content-Language header coverage via defense-in-depth (filter + advice + filter).

## Open Questions for Design Phase
- [RESOLVED] All backend action endpoints covered → 18/18 done (was 10/18 in previous brainstorm)
- [RESOLVED] Message bundle gaps → Zero gaps (55+ keys in both en + vi)
- [RESOLVED] Per-controller vs. base class vs. AOP → Per-controller injection confirmed and working across all 9 controllers
- [RESOLVED] Legacy AuthController → Leave as-is (disabled by default, lowest priority)
- [RESOLVED] LoginRateLimitFilter own resolveLocale() → Correct design (pre-DispatcherServlet, LocaleContextHolder not available)
- [RESOLVED] Content-Language double-setting → No conflict (defense-in-depth, same value)
- [OPEN] Frontend implementation scope — should the OpenSpec include frontend implementation tasks or document them as external integration specs? → **Decision: Document as external integration specs** with example code snippets. Auth-service OpenSpec focuses on backend tests.
- [OPEN] Should backend integration tests be comprehensive (all 18 endpoints) or sample-based (representative endpoints)? → **Decision: Sample-based** — test 3-4 representative endpoints (register success i18n, login error i18n, rate limit i18n, missing Accept-Language fallback) plus Content-Language header assertion.

## Open Questions for URD Analysis
- None — all URD questions resolved in pre_openspec and research phases. Zero open questions in pre_openspec.
