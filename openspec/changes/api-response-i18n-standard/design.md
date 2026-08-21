## Context

[CHANGED] Updated 2026-08-27 — reflects current codebase state where **100% backend i18n infrastructure is implemented** (18/18 action endpoints + all filters + all message bundles). All issues from previous archives (2026-08-22, 2026-08-25) are RESOLVED.

API response i18n infrastructure is FULLY IMPLEMENTED: `I18nConfig` (AcceptHeaderLocaleResolver + CompositeMessageSource), `DatabaseMessageSource` (Caffeine cached), `AuthControllerAdvice` (ProblemDetail i18n), `ContentLanguageFilter` (100% Content-Language coverage), `ClientMetadataFilter` (MDC logging), `LoginRateLimitFilter` (i18n-enabled with own resolveLocale()), message bundles (55+ keys × 2 locales), controller i18n (18 action endpoints across 9 controllers + 1 filter).

**Remaining scope**: Backend integration tests + Frontend header injection + form cleanup (admindashboard — external repo).

See `proposal.md` — Why for full motivation.

### Current Architecture Constraints
- Backend: Clean Architecture (port/adapter), CQRS (CommandHandler), Spring Boot + Kotlin
- auth-service: All i18n infrastructure EXISTING and COMPLETE — no source code changes needed
- Frontend: React 19, Ky HTTP client, i18next (đã setup en + vi) — in `admindashboard` repo (EXTERNAL)
- Existing: `setGlobalHeaders()` in api.ts, `I18nProvider` with language list, `ContentLanguageFilter`, `ClientMetadataFilter`

### Brainstorm Selected Direction
> Frontend-Only Completion (backend 100% done, close remaining frontend gaps) — focus on backend integration tests and document frontend contract as external integration specs.

## Goals / Non-Goals

**Goals:**
- Verify 100% backend i18n coverage with integration tests (representative endpoint sampling)
- Document frontend integration contract (headers, response consumption patterns, example code)
- Frontend injects Accept-Language + metadata headers on every API request (external repo)
- Frontend displays server-rendered messages directly — no client-side message building (external repo)
- 100% success i18n coverage for action endpoints (18/18 — ACHIEVED)

**Non-Goals:**
- Modifying any backend source code (already 100% complete)
- Adding i18n to data-only endpoints (GET lists, introspect, JWKS, anonymous, internal, RBAC/PBAC CRUD)
- Creating new filters or infrastructure classes (already done)
- Creating base controller class for i18n (rejected in brainstorm — per-controller injection proven)
- Admin UI for i18n message management (Phase 2)
- Adding locale `km` (Khmer) or other languages (future)
- Legacy `AuthController` cleanup (disabled by default, leave as-is per brainstorm Q4)

## Decisions

### D1: Dual MessageSource — File + Database [EXISTING — NO CHANGE]

**Choice**: `CompositeMessageSource` chain: DatabaseMessageSource → FileMessageSource
**Status**: ✅ IMPLEMENTED in `I18nConfig.kt:49-63`

### D2: BaseControllerAdvice — Nullable MessageSource Injection [EXISTING — NO CHANGE]

**Choice**: Constructor parameter `messageSource: MessageSource? = null`
**Status**: ✅ IMPLEMENTED — backward compatible

### D3: Frontend Locale Detection — Hybrid Cascade [EXISTING — NO CHANGE]

**Choice**: `localStorage("user_language")` → `navigator.languages` → `"en"` fallback
**Status**: ✅ IMPLEMENTED in `I18nProvider.tsx` and `i18n.ts`

### D4: Message Key Convention [EXISTING — NO CHANGE]

**Choice**: `AuthErrorCode.msgCode` = message bundle key (1:1 mapping) for errors. Success keys follow `auth.{action_name}` convention.
**Status**: ✅ IMPLEMENTED — 62+ error codes + 18+ success keys in both locales

### D5: Content-Language Response Header — Servlet Filter [EXISTING — NO CHANGE]

**Choice**: `ContentLanguageFilter` (OncePerRequestFilter) sets `Content-Language` on ALL `/api/**` responses
**Status**: ✅ IMPLEMENTED

### D6: Frontend Languages List [EXISTING — NO CHANGE]

**Choice**: `['en', 'vi']`
**Status**: ✅ IMPLEMENTED in `I18nProvider.tsx`

### D7: ClientMetadataFilter — MDC Logging [EXISTING — NO CHANGE]

**Choice**: Separate `ClientMetadataFilter` extracts `X-App-Version`, `X-Client-Platform` → MDC
**Status**: ✅ IMPLEMENTED

### D8: AcceptHeaderLocaleResolver with Locale Whitelist [EXISTING — NO CHANGE]

**Choice**: `AcceptHeaderLocaleResolver` bean with supported locales `[en, vi]`, default `en`
**Status**: ✅ IMPLEMENTED

### D9: Data-only Endpoints Skip i18n Messages [EXISTING — NO CHANGE]

**Choice**: Query/data endpoints do not include i18n `message` field.
**Status**: ✅ CONFIRMED — login/refresh return `AuthResponse` with tokens (data), no forced message

### D10: Defense-in-depth Content-Language [EXISTING — NO CHANGE]

**Choice**: Both `ContentLanguageFilter` and `AuthControllerAdvice.setContentLanguageHeader()` set Content-Language
**Status**: ✅ IMPLEMENTED

### D11: LoginRateLimitFilter parses Accept-Language directly [EXISTING — NO CHANGE]

**Choice**: Filter uses own `resolveLocale()` with `Locale.LanguageRange.parse()` + `Locale.lookup()` — pre-DispatcherServlet context
**Status**: ✅ IMPLEMENTED in `LoginRateLimitFilter.kt:115-124`

### D12: Per-Controller MessageSource Injection [EXISTING — COMPLETED] [CHANGED]

**Choice**: Inject `MessageSource` into each controller's constructor. Call `messageSource.getMessage(key, args, defaultMsg, locale)` inline in each action endpoint.
**Status**: ✅ IMPLEMENTED across ALL 9 controllers (18 action endpoints) + 1 filter. Pattern proven.
**[CHANGED] vs archive (2026-08-25)**: Was "5 remaining controllers". NOW all controllers done: MfaController (2), SsoController (2), AdminSessionController (1), RateLimitAdminController (1), TokenController (1) — completed between archive date and now.

### D13: Map-based response with message field [EXISTING — COMPLETED] [CHANGED]

**Choice**: Action endpoints return `Map<String, Any>` with same data fields + `message` field containing i18n message.
**Status**: ✅ IMPLEMENTED — consistent across all 9 controllers. TokenController and RateLimitAdminController converted from typed DTOs to maps.
**Trade-off**: Loses compile-time type safety for response. Acceptable — response is JSON serialized anyway.

### D14: Legacy AuthController — Leave as-is [EXISTING — DECIDED] [CHANGED]

**Choice**: Leave legacy `AuthController` (non-CQRS) with hardcoded English messages. Do NOT add i18n or remove.
**Why**: Behind `@ConditionalOnProperty(cqrs.enabled=false)` — disabled by default. Risk > benefit. Pattern trivially extensible if needed later.
**Status**: ✅ DECIDED — no action (per brainstorm Q4 analysis)

### D15: Backend Integration Tests — Sample-based [NEW]

**Choice**: Write integration tests for 3-4 representative endpoints covering all i18n code paths, NOT exhaustive tests for all 18 endpoints.
**Why**: All 18 endpoints follow the identical pattern (constructor-inject MessageSource, call getMessage). Testing 3-4 proves the pattern works; testing all 18 would be redundant.
**Test scenarios**:
1. Success i18n: `POST /api/auth/register` with `Accept-Language: vi` → Vietnamese success message
2. Error i18n: Invalid login with `Accept-Language: vi` → Vietnamese ProblemDetail.detail
3. Fallback: Request without `Accept-Language` → English message (default)
4. Content-Language header: Verify header present on ALL response types
5. Unsupported locale: `Accept-Language: ja` → English fallback

### D16: Frontend Contract as External Integration Spec [NEW]

**Choice**: Document frontend requirements as integration specs within OpenSpec artifacts. Do NOT generate frontend code in auth-service workspace.
**Why**: admindashboard is a separate repo. Generating code here cannot be applied. Frontend developers follow the API contract + example code snippets.

## Component Mapping

### auth-service — ALL IMPLEMENTED (VERIFY + TEST ONLY)

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `AuthControllerAdvice` | `shared/exception/GlobalExceptionHandler.kt` | [EXISTING] | FR-001, FR-004 |
| `I18nConfig` | `shared/config/I18nConfig.kt` | [EXISTING] | FR-002, FR-003, FR-012 |
| `DatabaseMessageSource` | `shared/i18n/DatabaseMessageSource.kt` | [EXISTING] | FR-003, FR-010 |
| `ContentLanguageFilter` | `auth/adapter/in/web/filter/ContentLanguageFilter.kt` | [EXISTING] | FR-006 |
| `ClientMetadataFilter` | `auth/adapter/in/web/filter/ClientMetadataFilter.kt` | [EXISTING] | FR-011 |
| `LoginRateLimitFilter` | `auth/adapter/in/web/filter/LoginRateLimitFilter.kt` | [EXISTING] | FR-004, FR-005 |
| `CqrsAuthController` | `auth/adapter/in/web/CqrsAuthController.kt` | [EXISTING] | FR-005 (5 endpoints) |
| `SessionController` | `auth/adapter/in/web/SessionController.kt` | [EXISTING] | FR-005 (2 endpoints) |
| `AccountLifecycleController` | `auth/adapter/in/web/AccountLifecycleController.kt` | [EXISTING] | FR-005 (3 endpoints) |
| `MfaController` | `auth/adapter/in/web/MfaController.kt` | [EXISTING] | FR-005 (2 endpoints) |
| `SsoController` | `auth/adapter/in/web/SsoController.kt` | [EXISTING] | FR-005 (2 endpoints) |
| `AdminSessionController` | `auth/adapter/in/web/AdminSessionController.kt` | [EXISTING] | FR-005 (1 endpoint) |
| `RateLimitAdminController` | `auth/adapter/in/web/RateLimitAdminController.kt` | [EXISTING] | FR-005 (1 endpoint) |
| `TokenController` | `auth/adapter/in/web/TokenController.kt` | [EXISTING] | FR-005 (1 endpoint) |
| `auth-messages.properties` | `resources/messages/auth-messages.properties` | [EXISTING] | FR-003, FR-005 |
| `auth-messages_vi.properties` | `resources/messages/auth-messages_vi.properties` | [EXISTING] | FR-003, FR-005 |
| `I18nIntegrationTest` | `src/test/kotlin/.../web/I18nIntegrationTest.kt` | [NEW] | FR-001–FR-006, FR-010–FR-013 |

### admindashboard — Changes Needed (EXTERNAL REPO)

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `api.ts` | `src/utils/api.ts` | [MODIFY] | FR-007, FR-008 |
| `I18nProvider.tsx` | `src/@i18n/I18nProvider.tsx` | [MODIFY] | FR-007 |
| `i18n.ts` | `src/@i18n/i18n.ts` | [MODIFY] | FR-007 |
| `JwtSignInForm.tsx` | `src/@auth/services/jwt/components/JwtSignInForm.tsx` | [MODIFY] | FR-009 |
| `LanguageSwitcher.tsx` | `src/components/theme-layouts/components/LanguageSwitcher.tsx` | [MODIFY] | D6 |
| `VN.svg` | `public/assets/images/flags/VN.svg` | [NEW] | D6 |

## Sequence Diagram

### Full Request Flow (IMPLEMENTED)

```
Frontend                          Server (Spring)                    Database
   │                                    │                                │
   │──API Request────────────────────────▶                                │
   │  Accept-Language: vi               │                                │
   │  X-App-Version: 1.0.0             │                                │
   │  X-Client-Platform: web           │                                │
   │                                    │                                │
   │                              ClientMetadataFilter (HIGHEST+10)     │
   │                              → MDC.put(appVersion, clientPlatform) │
   │                                    │                                │
   │                              AcceptHeaderLocaleResolver             │
   │                              → resolve("vi") → LocaleContextHolder │
   │                                    │                                │
   │                              Controller dispatch                   │
   │                              ├── [SUCCESS] messageSource.getMessage │
   │                              │   → ResponseEntity(message=i18n)    │
   │                              └── [ERROR] → AuthControllerAdvice    │
   │                                    │     → resolveMessage(i18n)    │
   │                                    │     → ProblemDetail(detail=   │
   │                                    │       i18nMessage)            │
   │                                    │                                │
   │                              ContentLanguageFilter (LOWEST-10)     │
   │                              → setHeader("Content-Language", "vi") │
   │                                    │                                │
   │◀─── Response ──────────────────────│                                │
   │  Content-Language: vi              │                                │
   │                                    │                                │
   │ SUCCESS: show(response.message)    │                                │
   │ ERROR: show(problem.detail)        │                                │
```

### Integration Test Verification Flow

```
Test                              MockMvc                           Spring Context
  │                                    │                                │
  │──mockMvc.perform(                  │                                │
  │    post("/api/auth/register")      │                                │
  │    .header("Accept-Language","vi") │                                │
  │    .content(registerRequest))──────▶                                │
  │                                    │                                │
  │                              AcceptHeaderLocaleResolver → vi       │
  │                              CqrsAuthController.register()         │
  │                              messageSource.getMessage(             │
  │                                "auth.register_success",null,       │
  │                                "Registration successful", vi)      │
  │                              → "Đăng ký thành công"               │
  │                                    │                                │
  │◀─── andExpect(                     │                                │
  │       status().isOk,               │                                │
  │       jsonPath("$.message")        │                                │
  │         .value("Đăng ký thành      │                                │
  │          công"),                    │                                │
  │       header("Content-Language",   │                                │
  │         "vi"))────────────────────│                                │
```
