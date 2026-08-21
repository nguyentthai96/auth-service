## Context

[CHANGED] Updated 2026-08-25 — reflects current codebase state where ~97% i18n infrastructure is implemented AND LoginRateLimitFilter i18n is resolved.

API response i18n infrastructure đã implement gần hoàn chỉnh: `I18nConfig` (AcceptHeaderLocaleResolver + CompositeMessageSource), `DatabaseMessageSource` (Caffeine cached), `AuthControllerAdvice` (ProblemDetail i18n), `ContentLanguageFilter` (100% Content-Language coverage), `ClientMetadataFilter` (MDC logging), `LoginRateLimitFilter` (i18n-enabled with own resolveLocale()), message bundles (55 keys × 2 locales), controller i18n (10 action endpoints across 3 controllers + 1 filter).

**Remaining gap**: 7 action endpoints across 5 controllers need success i18n messages. Frontend needs header injection and hardcoded message cleanup.

See `proposal.md` — Why for full motivation.

### Current Architecture Constraints
- Backend: Clean Architecture (port/adapter), CQRS (CommandHandler), Spring Boot + Kotlin
- auth-service: All i18n infrastructure EXISTING (`I18nConfig`, `DatabaseMessageSource`, `AuthControllerAdvice`, filters, bundles)
- Frontend: React 19, Ky HTTP client, i18next (đã setup en + vi)
- Existing: `setGlobalHeaders()` in api.ts, `I18nProvider` with language list, `ContentLanguageFilter`, `ClientMetadataFilter`

### Brainstorm Selected Direction
> Per-Controller MessageSource Injection (extend existing pattern) — extend the proven pattern from CqrsAuthController/SessionController/AccountLifecycleController to remaining 5 controllers.

## Goals / Non-Goals

**Goals:**
- Extend success i18n to ALL remaining action endpoints (7 endpoints across 5 controllers)
- Frontend injects Accept-Language + metadata headers on every API request
- Frontend displays server-rendered messages directly (no client-side message building)
- 100% success i18n coverage for action endpoints (17 total)

**Non-Goals:**
- Modifying existing i18n infrastructure (already complete)
- Adding i18n to data-only endpoints (GET lists, introspect, JWKS, anonymous, internal, RBAC/PBAC CRUD)
- Creating new filters or infrastructure classes (already done)
- Creating base controller class for i18n (rejected in brainstorm — per-controller injection is simpler)
- Admin UI for i18n message management (Phase 2)
- Adding locale `km` (Khmer) or other languages (future)

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
**Status**: ✅ IMPLEMENTED — 44 error codes + 11 success keys in both locales

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

**Choice**: Query/data endpoints do not include i18n `message` field. See brainstorm Q2 for full endpoint classification.
**Status**: ✅ CONFIRMED — login/refresh return `AuthResponse` with tokens (data), no forced message

### D10: Defense-in-depth Content-Language [EXISTING — NO CHANGE]

**Choice**: Both `ContentLanguageFilter` and `AuthControllerAdvice.setContentLanguageHeader()` set Content-Language
**Status**: ✅ IMPLEMENTED

### D11: LoginRateLimitFilter parses Accept-Language directly [EXISTING — NO CHANGE]

**Choice**: Filter uses own `resolveLocale()` with `Locale.LanguageRange.parse()` + `Locale.lookup()` — pre-DispatcherServlet context
**Status**: ✅ IMPLEMENTED in `LoginRateLimitFilter.kt:115-124`

### D12: Per-Controller MessageSource Injection [SELECTED — THE APPROACH] [NEW]

**Choice**: Inject `MessageSource` into each remaining controller's constructor. Call `messageSource.getMessage(key, args, defaultMsg, locale)` inline in each action endpoint.
**Why**: Proven pattern across 11 endpoints in 4 components (CqrsAuthController, SessionController, AccountLifecycleController, LoginRateLimitFilter). Each change is a surgical addition (constructor param + getMessage call). No new abstractions.
**Alternative 1**: Abstract BaseI18nController — forces single inheritance (Kotlin), existing controllers don't extend a base. Rejected.
**Alternative 2**: AOP/annotation-driven i18n — magic behavior, doesn't handle parameterized messages well. Rejected.

### D13: TokenController + RateLimitAdminController return type change [NEW]

**Choice**: Change return type from typed DTO (`RevokeSessionsResponse`, `UnlockResponse`) to `Map<String, Any>` with same fields + `message`
**Why**: Consistency with other controller patterns (CqrsAuthController, SessionController, AdminSessionController all use map-based responses). Adding `message` field to existing DTO would also work but adds coupling between DTO design and i18n concerns.
**Trade-off**: Loses compile-time type safety for these 2 endpoints. Acceptable because response is JSON serialized anyway.
**⚠️ OPEN QUESTION**: Design phase should confirm — map vs DTO with message field. Auto-resolving: use map for consistency with existing proven pattern.

### D14: AuthController (legacy) cleanup scope [NEW]

**Choice**: Add i18n to legacy `AuthController.changePassword()` and `forgotPassword()` for consistency, but lowest priority.
**Why**: Behind `@ConditionalOnProperty(cqrs.enabled=false)` — disabled by default. Cleanup reduces dead code English hardcoding.
**⚠️ OPEN QUESTION**: Design phase should decide — cleanup vs. removal. Auto-resolving: cleanup with i18n for completeness (lowest priority task).

## Component Mapping

### auth-service — Changes Needed [NEW]

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `MfaController` | `auth/adapter/in/web/MfaController.kt` | [MODIFY] | FR-005 |
| `TokenController` | `auth/adapter/in/web/TokenController.kt` | [MODIFY] | FR-005 |
| `SsoController` | `auth/adapter/in/web/SsoController.kt` | [MODIFY] | FR-005 |
| `AdminSessionController` | `auth/adapter/in/web/AdminSessionController.kt` | [MODIFY] | FR-005 |
| `RateLimitAdminController` | `auth/adapter/in/web/RateLimitAdminController.kt` | [MODIFY] | FR-005 |
| `auth-messages.properties` | `resources/messages/auth-messages.properties` | [MODIFY] | FR-005 |
| `auth-messages_vi.properties` | `resources/messages/auth-messages_vi.properties` | [MODIFY] | FR-005 |

### auth-service — Already Implemented (VERIFY ONLY)

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `AuthControllerAdvice` | `shared/exception/GlobalExceptionHandler.kt` | [EXISTING] | FR-001, FR-004 |
| `I18nConfig` | `shared/config/I18nConfig.kt` | [EXISTING] | FR-002, FR-003, FR-012 |
| `DatabaseMessageSource` | `shared/i18n/DatabaseMessageSource.kt` | [EXISTING] | FR-003, FR-010 |
| `ContentLanguageFilter` | `auth/adapter/in/web/filter/ContentLanguageFilter.kt` | [EXISTING] | FR-006 |
| `ClientMetadataFilter` | `auth/adapter/in/web/filter/ClientMetadataFilter.kt` | [EXISTING] | FR-011 |
| `LoginRateLimitFilter` | `auth/adapter/in/web/filter/LoginRateLimitFilter.kt` | [EXISTING] | FR-004 |
| `CqrsAuthController` | `auth/adapter/in/web/CqrsAuthController.kt` | [EXISTING] | FR-005 |
| `SessionController` | `auth/adapter/in/web/SessionController.kt` | [EXISTING] | FR-005 |
| `AccountLifecycleController` | `auth/adapter/in/web/AccountLifecycleController.kt` | [EXISTING] | FR-005 |

### admindashboard — Changes Needed

| Component | File | Action | FR |
|-----------|------|--------|-----|
| `api.ts` | `src/utils/api.ts` | [MODIFY] | FR-007, FR-008 |
| `I18nProvider.tsx` | `src/@i18n/I18nProvider.tsx` | [MODIFY] | FR-007 |
| `i18n.ts` | `src/@i18n/i18n.ts` | [MODIFY] | FR-007 |
| `JwtSignInForm.tsx` | `src/@auth/services/jwt/components/JwtSignInForm.tsx` | [MODIFY] | FR-009 |
| `LanguageSwitcher.tsx` | `src/components/theme-layouts/components/LanguageSwitcher.tsx` | [MODIFY] | D6 |
| `VN.svg` | `public/assets/images/flags/VN.svg` | [NEW] | D6 |

## Sequence Diagram

### Per-Controller i18n Pattern (THE APPROACH)

```
Frontend                          Controller                        MessageSource
   │                                    │                                │
   │──POST /api/auth/mfa/totp/confirm───▶                                │
   │  Accept-Language: vi               │                                │
   │                                    │                                │
   │                              AcceptHeaderLocaleResolver             │
   │                              → resolve("vi") → LocaleContextHolder │
   │                                    │                                │
   │                              confirmTotp(request)                   │
   │                              ├── mfaService.confirmTotp(userId, code)│
   │                              │                                      │
   │                              ├── val locale = LocaleContextHolder   │
   │                              │     .getLocale() → Locale("vi")     │
   │                              │                                      │
   │                              ├── messageSource.getMessage(──────────▶│
   │                              │     "auth.totp_confirmed",           │
   │                              │     null,                            │
   │                              │     "TOTP setup confirmed",          │
   │                              │     Locale("vi"))                    │
   │                              │◀─────────────────────────────────────│
   │                              │   "Xác nhận cài đặt TOTP            │
   │                              │    thành công"                       │
   │                              │                                      │
   │                              └── ResponseEntity.ok(mapOf(           │
   │                                    "success" to true,               │
   │                                    "message" to resolvedMessage))   │
   │                                    │                                │
   │                              ContentLanguageFilter (LOWEST-10)     │
   │                              → setHeader("Content-Language", "vi") │
   │                                    │                                │
   │◀─── 200 OK ───────────────────────│                                │
   │  Content-Language: vi              │                                │
   │  { "success": true,               │                                │
   │    "message": "Xác nhận cài đặt   │                                │
   │    TOTP thành công" }              │                                │
   │                                    │                                │
   │ show(response.message)             │                                │
```

### Full Request Flow (Existing — for reference)

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
   │                              └── [ERROR] → AuthControllerAdvice    │
   │                                    │     → resolveMessage(i18n)    │
   │                                    │                                │
   │                              ContentLanguageFilter (LOWEST-10)     │
   │                              → setHeader("Content-Language", "vi") │
   │                                    │                                │
   │◀─── Response ──────────────────────│                                │
   │  Content-Language: vi              │                                │
```
