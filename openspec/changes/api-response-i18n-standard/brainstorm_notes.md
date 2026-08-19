---
type: brainstorm_notes
change: api-response-i18n-standard
date: 2026-08-11
selected_direction: "Servlet Filter Content-Language + Controller-level Success i18n + Hybrid Cascade Locale Detection"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: API Response I18n Standard

## Date
2026-08-11

## Context
Feature chuẩn hóa API request/response giữa client-server: Server render message i18n hoàn chỉnh (đã interpolate params), client chỉ hiển thị. Dual format: `ApiResponse<T>` (success), `ProblemDetail` (error). Supported locales: `en` (default) + `vi`, auto-detect via `navigator.languages`. ~90% infrastructure already exists (I18nConfig, DatabaseMessageSource, AuthControllerAdvice, message bundles). Remaining: success i18n, Content-Language on all responses, client headers, missing message keys.

Research handoff (openspec/research/api-response-i18n-standard/) confirms:
- Build from scratch (Spring native) is recommended (score 8.60/10 vs 7.90 and 4.85)
- 90%+ infrastructure already implemented
- ~3 developer-days remaining work
- All 5 validation checks PASS

## Questions Asked & Answers

- Q1: Có cần thêm locale `km` (Khmer) ngay từ phase đầu?
  → A: Không. Hiện tại chỉ `en` (default) + `vi`. Dùng locale chuẩn quốc tế (BCP 47). Mở rộng sau.

- Q2: Locale detection mechanism?
  → A: User muốn "detection location để lấy ngôn ngữ nếu user không set". Dùng `navigator.languages` (browser tự detect theo OS locale), KHÔNG dùng IP geolocation. Persist vào localStorage.

- Q3: Content-Language header coverage — per-controller vs filter?
  → A: (Auto-decided) **Servlet Filter** approach for 100% coverage. NFR-004 requires 100% Content-Language on ALL responses. With 13+ controllers and growing, per-controller is fragile — new endpoints would miss the header. A single `ContentLanguageFilter` (OncePerRequestFilter) guarantees coverage with zero maintenance burden on controller developers.

- Q4: How to handle success message i18n for controllers that return data without user-facing messages?
  → A: (Auto-decided) **Two categories**: (1) Action endpoints (logout, change-password, deactivate, etc.) — add i18n `message` field via MessageSource. (2) Data query endpoints (get sessions, get policies, introspect token) — no user-facing message needed, just data. No need to force i18n messages on pure data endpoints.

- Q5: Should all controllers inject MessageSource directly, or use a helper/base class?
  → A: (Auto-decided) **Direct injection** in controllers that need success messages (CqrsAuthController already does this). A base class adds unnecessary coupling and wouldn't reduce code since only ~5 controllers need success messages. Spring DI makes this clean.

- Q6: Missing 12 message keys (E2EE AUTH_030-039 + Anonymous AUTH_040-044) — are they used in error responses?
  → A: Yes — AuthControllerAdvice handles AnonymousRateLimitedException, AnonymousMaxRenewalsException, AnonymousDataLimitExceededException (AUTH_041, 043, 044) with message interpolation args. E2EE exceptions also go through AuthControllerAdvice. All 12 codes MUST have message bundle entries.

- Q7: ClientMetadataFilter placement — same filter as ContentLanguageFilter or separate?
  → A: (Auto-decided) **Separate filters**, single responsibility. `ContentLanguageFilter` → response header. `ClientMetadataFilter` → request header → MDC. Different concerns, different lifecycle (request vs response). Both are OncePerRequestFilter.

## Approaches Considered

### Approach 1: Servlet Filter for Content-Language (SELECTED ✅ for Content-Language)
- **Mô tả**: A `ContentLanguageFilter` (OncePerRequestFilter) that sets `Content-Language` response header on EVERY response, regardless of controller. Uses `LocaleContextHolder.getLocale()`.
- **Pros**: 100% coverage guaranteed (NFR-004), zero maintenance for new controllers, single point of change, idempotent with AuthControllerAdvice (last-write wins but same value)
- **Cons**: Filter runs on non-API paths too (health checks, actuator) — mitigated with URL pattern `/api/**`
- **Trade-off analysis**: AuthControllerAdvice already sets Content-Language for errors — filter will set it again (same value). Minor redundancy, no functional impact.
- **Verdict**: ✅ Best for NFR-004 compliance

### Approach 2: Per-controller Content-Language (REJECTED ❌)
- **Mô tả**: Each controller method manually sets `Content-Language` header via HttpServletResponse parameter.
- **Pros**: Explicit, per-method control
- **Cons**: 13+ controllers with 40+ endpoints — high maintenance burden, easy to miss new endpoints, breaks NFR-004 compliance guarantee
- **Verdict**: ❌ Fragile at scale

### Approach 3: ResponseBodyAdvice for Content-Language (REJECTED ❌)
- **Mô tả**: Implement `ResponseBodyAdvice<Any>` that intercepts all @RestController responses and sets Content-Language header.
- **Pros**: Framework-level, automatic for all @RestController methods
- **Cons**: Doesn't cover raw servlet responses (e.g., LoginRateLimitFilter writes ProblemDetail directly), requires ServerHttpResponse cast, less predictable ordering with existing AuthControllerAdvice
- **Verdict**: ❌ Incomplete coverage — filter responses not intercepted

### Approach 4: Hybrid Cascade for Locale Detection (SELECTED ✅ for frontend)
- **Mô tả**: Priority cascade: (1) localStorage("user_language") → (2) navigator.languages → (3) default "en"
- **Pros**: Respects browser locale (≈ OS location), zero external dependency, privacy-friendly, persists user choice, deterministic
- **Cons**: Browser setting not 100% accurate → user switches once → remembered forever
- **Verdict**: ✅ Best balance of accuracy, privacy, and UX

### Approach 5: IP Geolocation for Locale Detection (REJECTED ❌)
- **Mô tả**: Server/client calls IP geolocation API → country → language mapping
- **Pros**: Real location awareness
- **Cons**: External dependency (API cost, rate limit), privacy concern (GDPR), inaccurate (VPN/proxy), language ≠ location (expat scenario)
- **Verdict**: ❌ Overengineered, privacy risk

## Selected Direction

**Three-pronged approach:**

1. **Servlet Filter for Content-Language** — `ContentLanguageFilter` (OncePerRequestFilter) sets `Content-Language` on ALL `/api/**` responses. Guarantees NFR-004 (100% coverage).

2. **Controller-level Success i18n** — Controllers that return user-facing action messages (logout, cha        │
    │   → ApiResponse / Map with i18n message              │
    │                                                      │
    │ ERROR PATH:                                          │
    │   AuthException thrown → AuthControllerAdvice        │
    │   → resolveMessage(msgCode, args, locale)            │
    │   → ProblemDetail with i18n detail                   │
    └──────────────────┬───────────────────────────────────┘
                       │
                       ▼
                  RESPONSE PATH
    ┌──────────────────────────────────────────────────────┐
    │ ContentLanguageFilter (NEW)                           │
    │ response.setHeader("Content-Language",                │
    │                    LocaleContextHolder.getLocale())   │
    └──────────────────┬───────────────────────────────────┘
                       │
                       ▼
    ┌────────────────────────────)             │
└─────────────────────────────────────────────────────────────────┘

CONTROLLERS NOT NEEDING i18n MESSAGES (data-only endpoints):
┌─────────────────────────────────────────────────────────────────┐
│ AnonymousAuthController   │ Returns token/session data only     │
│ TokenController           │ Returns introspection/revoke data   │
│ KeyExchangeController     │ Returns key exchange data           │
│ CaptchaController         │ Returns captcha challenge           │
│ SsoController             │ Returns auth response/status        │
│ AdminSessionController    │ Returns session count/stats         │
│ RateLimitAdminController  │ Returns rate limit status           │
│ PolicyController (pbac)   │ Returns policy CRUD data            │
│ RbacControllers (rbac)    │ Returns RBAC CRUD data              │
└─────────────────────────────────────────────────────────────────┘
```

### Message Bundle Keys — Gap Analysis

```
EXISTING KEYS (32 in en + vi bundles):
  ✅ AUTH_001-021 error messages (21 keys)
  ✅ 5 success messages (login, logout, password_changed, token_refreshed, password_reset_sent)

MISSING KEYS — ERROR CODES (12 keys needed):
  ❌ auth.e2ee_time_skew             (AUTH_030)
  ❌ auth.e2ee_version_unknown       (AUTH_031)
  ❌ auth.e2ee_context_mismatch      (AUTH_032)
  ❌ auth.e2ee_replay_detected       (AUTH_033)
  ❌ auth.e2ee_version_sunset        (AUTH_034)
  ❌ auth.e2ee_decrypt_failed        (AUTH_035)
  ❌ auth.e2ee_kms_unavailable       (AUTH_036)
  ❌ auth.e2ee_key_expired           (AUTH_037)
  ❌ auth.e2ee_device_unregistered   (AUTH_038)
  ❌ auth.e2ee_max_devices           (AUTH_039)
  ❌ auth.anonymous_session_expired  (AUTH_040)
  ❌ auth.anonymous_data_limit_exceeded (AUTH_041 — has interpolation args!)
  # Note: AUTH_042-044 already in AuthErrorCode but NOT in message bundles

MISSING KEYS — SUCCESS MESSAGES (6 keys needed):
  ❌ auth.register_success
  ❌ auth.refresh_success → already exists as auth.token_refreshed ✅
  ❌ auth.switch_domain_success
  ❌ auth.session_revoked
  ❌ auth.all_sessions_revoked
  ❌ auth.account_deactivated
  ❌ auth.deletion_requested
  ❌ auth.deletion_cancelled

TOTAL MISSING: ~17 new message keys × 2 locales = ~34 entries
```

### Filter Ordering

```
Spring Filter Chain (relevant filters):

  1. SecurityFilterChain (Spring Security — authentication)
  2. LoginRateLimitFilter (existing — pre-auth rate limiting)
  3. ClientMetadataFilter (NEW — X-App-Version/X-Client-Platform → MDC)
  4. ContentLanguageFilter (NEW — Content-Language response header)
  5. Controller dispatch

Note: ContentLanguageFilter wraps the response, so it sets
Content-Language AFTER controller processing (in doFilter's
post-processing phase). This works because LocaleContextHolder
is available after AcceptHeaderLocaleResolver runs.
```

### Frontend Architecture

```
First visit:
  navigator.languages → ["vi", "en"] → detect("vi")
  → i18n.changeLanguage("vi")
  → localStorage.set("user_language", "vi")
  → api.setGlobalHeaders({ "Accept-Language": "vi" })

Return visit:
  localStorage.get("user_language") → "vi"
  → i18n.changeLanguage("vi")
  → api.setGlobalHeaders({ "Accept-Language": "vi" })

Language switcher:
  user clicks "English"
  → i18n.changeLanguage("en")
  → localStorage.set("user_language", "en")
  → api.setGlobalHeaders({ "Accept-Language": "en" })
```

### Design Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | `navigator.languages` for auto-detect, NOT IP geolocation | Privacy, zero cost, no external dependency. Browser reflects OS locale ≈ location |
| D2 | Persist language in `localStorage` | Survive page refresh, no server roundtrip |
| D3 | Supported locales: `["en", "vi"]` only | User requirement — extensible later |
| D4 | `en` default locale | International standard, safe fallback |
| D5 | BCP 47 locale format | IETF standard, Spring native support via AcceptHeaderLocaleResolver |
| D6 | `ContentLanguageFilter` for 100% Content-Language coverage | NFR-004 requires 100%. Filter > per-controller (13+ controllers, fragile) |
| D7 | `ClientMetadataFilter` for MDC logging | Separate filter, single responsibility, request-phase concern |
| D8 | Direct MessageSource injection in controllers | Only ~5 controllers need success messages. Base class adds coupling without reducing code |
| D9 | Data-only endpoints skip i18n messages | Query endpoints return structured data, not user-facing action confirmations |
| D10 | Keep AuthControllerAdvice Content-Language (redundant with filter) | Filter guarantees coverage; advice-level is defense-in-depth. Filter's last-write wins with same value |
| D11 | AcceptHeaderLocaleResolver with supported locale whitelist | Configure in I18nConfig/WebMvcConfig — reject unsupported locales, fallback to `en` |
| D12 | E2EE + Anonymous message keys MUST be added to bundles | AuthControllerAdvice handles these exceptions — missing keys cause English fallback instead of proper localization |

## Pre-classifications (preliminary)
- Feature type: EXTEND
- Flow type: Command (cross-cutting concern affecting all API endpoints)
- Affected modules:
  - **auth-service/shared/config**: I18nConfig (existing), locale resolver config (NEW)
  - **auth-service/shared/exception**: AuthControllerAdvice (existing, no change needed)
  - **auth-service/shared/i18n**: DatabaseMessageSource (existing), I18nMessageEntity (existing)
  - **auth-service/auth/adapter/in/web**: Controllers (extend success i18n for ~5 controllers)
  - **auth-service/auth/adapter/in/web/filter**: ContentLanguageFilter (NEW), ClientMetadataFilter (NEW)
  - **auth-service/resources/messages**: Message bundles (add ~17 missing keys × 2 locales)
  - **admindashboard/src/utils**: api.ts (header injection)
  - **admindashboard/src/@i18n**: I18nProvider.tsx, i18n.ts (language sync + detection)
  - **admindashboard/src/.../forms**: Remove hardcoded error messages

## Codebase Findings

### Verified Infrastructure (Already Implemented)
| Component | File | Status | Lines |
|-----------|------|--------|-------|
| I18nConfig | `shared/config/I18nConfig.kt` | ✅ DONE | 41 |
| DatabaseMessageSource | `shared/i18n/DatabaseMessageSource.kt` | ✅ DONE | 57 |
| I18nMessageEntity | `shared/i18n/I18nMessageEntity.kt` | ✅ DONE | — |
| I18nMessageRepository | `shared/i18n/I18nMessageRepository.kt` | ✅ DONE | — |
| AuthControllerAdvice | `shared/exception/GlobalExceptionHandler.kt` | ✅ DONE | 140 |
| auth-messages.properties | `resources/messages/auth-messages.properties` | ✅ DONE | 44 keys (en) |
| auth-messages_vi.properties | `resources/messages/auth-messages_vi.properties` | ✅ DONE | 44 keys (vi) |
| AuthErrorCode enum | `shared/exception/AuthErrorCode.kt` | ✅ DONE | 33 codes (AUTH_001-044) |
| CqrsAuthController MessageSource | `auth/adapter/in/web/CqrsAuthController.kt` | ✅ PARTIAL | 3/7 endpoints use messageSource |

### Key Pattern: How Success i18n Works (proven pattern in CqrsAuthController)
```kotlin
// Already working — lines 185, 197, 205
val message = messageSource.getMessage("auth.logout_success", null, LocaleContextHolder.getLocale())
return ResponseEntity.ok(mapOf("message" to message))
```

### Issues Identified (Hardcoded Messages)
1. **SessionController** — 2 hardcoded English strings: "Session revoked", "All sessions revoked"
2. **AccountLifecycleController** — 3 hardcoded English strings: "Account deactivated...", "Deletion request created...", "Deletion request cancelled..."
3. **AuthController** (legacy, disabled by CQRS flag) — 2 hardcoded strings: "Password changed successfully", "If the email exists..."
4. **CqrsAuthController** — login/register/refresh/switch-domain return data objects without explicit success messages
5. **E2EE error codes** (AUTH_030-039) — 10 codes in AuthErrorCode but NO message bundle entries
6. **Anonymous error codes** (AUTH_040-044) — 5 codes in AuthErrorCode but NO message bundle entries for AUTH_040/042
7. **No AcceptHeaderLocaleResolver** explicitly configured — Spring uses default behavior (no supported locales whitelist)
8. **Content-Language** only set by AuthControllerAdvice for error responses — 0% success response coverage

### Message Interpolation Requirements (from AuthControllerAdvice.extractMessageArgs)
```kotlin
// These exceptions have message args that need {0}, {1} in bundle:
RateLimitExceededException → [retryAfterSeconds, dimension]  // "Too many attempts ({1}). Wait {0}s"
SessionLimitExceededException → [maxSessions]                // "Max {0} sessions reached"
MfaAccountLockedException → [retryAfterSeconds]              // "Locked. Wait {0}s"
AnonymousRateLimitedException → [retryAfterSeconds]          // "Rate limited. Wait {0}s"
AnonymousMaxRenewalsException → [maxRenewals]                // "Max {0} renewals exceeded"
AnonymousDataLimitExceededException → [currentSize, maxSize] // "Data {0} exceeds limit {1}"
```

## GitNexus Findings (if explored)
- Not explored via GitNexus (codebase investigated directly via grep/view_file)
- Key codebase findings documented above

## Open Questions for Design Phase
- [RESOLVED] Content-Language approach → Servlet Filter (ContentLanguageFilter)
- [RESOLVED] Which controllers need success i18n → 5 controllers (~10 endpoints with hardcoded messages)
- [RESOLVED] Missing message keys count → ~17 new keys × 2 locales
- [RESOLVED] Filter ordering → ClientMetadataFilter before controller, ContentLanguageFilter wraps response
- [OPEN] Should `AcceptHeaderLocaleResolver` be configured in I18nConfig or in a separate WebMvcConfigurer? → Design phase will decide placement
- [OPEN] Frontend cleanup scope — how many forms have hardcoded error messages? → Need frontend codebase scan during implementation
- [OPEN] Should login/register success responses include i18n `message` field in AuthResponse DTO, or keep as data-only? → Design phase decision based on frontend needs

## Open Questions for URD Analysis
- Không có — đã đủ thông tin từ research + pre_openspec + codebase scan

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|:-:|:-:|----|
| ContentLanguageFilter conflicts with AuthControllerAdvice Content-Language setting | LOW | LOW | Both set same value from LocaleContextHolder. Last-write wins. No conflict. |
| Missing message key at runtime (E2EE/Anonymous) | MEDIUM | LOW | Triple fallback: DB → file → ErrorCodeBase.description. Add all 17 keys in this feature. |
| AcceptHeaderLocaleResolver not whitelist-configured, accepts any locale | MEDIUM | LOW | Configure supported locales `[en, vi]` with `en` fallback. Otherwise Spring returns whatever client sends. |
| New controllers added without i18n awareness | LOW | LOW | ContentLanguageFilter covers Content-Language automatically. Only action-message endpoints need manual MessageSource. |
| Frontend not sending Accept-Language correctly | LOW | LOW | Server defaults to `en` — backward compatible. ky beforeRequest hook is proven pattern. |

## Implementation Estimate

| Phase | Work Item | Effort |
|-------|-----------|--------|
| **Phase 1: Backend filters** | ContentLanguageFilter, ClientMetadataFilter, AcceptHeaderLocaleResolver config | 0.5 day |
| **Phase 2: Message bundles** | Add 17 missing message keys (en + vi) for E2EE, Anonymous, and success messages | 0.5 day |
| **Phase 3: Controller i18n** | Wire MessageSource into SessionController, AccountLifecycleController; extend CqrsAuthController for remaining endpoints | 1 day |
| **Phase 4: Frontend** | api.ts headers, I18nProvider language detection, cleanup hardcoded error messages | 1 day |
| **Phase 5: Testing** | Integration tests for locale-based response verification | 0.5 day |
| **TOTAL** | | **3.5 developer-days** |
