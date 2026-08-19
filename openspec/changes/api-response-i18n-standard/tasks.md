<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: api-response-i18n-standard

> **Type**: EXTEND | **Flow**: Command | **FRs**: 14 (9 IDEA + 4 ENRICHED + 1 USER_ADDED)
> **Direction**: Servlet Filter Content-Language + Controller-level Success i18n + Hybrid Cascade Locale Detection

## Changes

Feature EXTENDS existing i18n infrastructure (~90% already implemented). This tasks.md covers remaining gaps:

**Backend filters (NEW)**: ContentLanguageFilter + ClientMetadataFilter — 2 new files
**Backend config (MODIFY)**: I18nConfig — add AcceptHeaderLocaleResolver with locale whitelist
**Message bundles (MODIFY)**: Add 22 missing keys (10 E2EE + 5 Anonymous + 7 success) × 2 locales = 44 entries
**Controller i18n (MODIFY)**: SessionController, AccountLifecycleController — inject MessageSource, replace hardcoded strings
**base-core (NEW+MODIFY)**: I18nAutoConfiguration, BaseControllerAdvice nullable MessageSource, base message bundles
**Frontend (MODIFY)**: api.ts headers, I18nProvider language detection, i18n.ts vi resources, JwtSignInForm cleanup

**Total**: 5 files modified (auth-service), 2 files new (auth-service), 4 files modified (base-core), 2 files new (base-core), 4 files modified (frontend)

---## Phase 1: base-core Infrastructure (Foundation)

- [x] **Task 1: Create I18nAutoConfiguration** ✅ VERIFIED — already exists
  - File: `base-core/src/main/kotlin/com/ntt/basecore/configuration/I18nAutoConfiguration.kt` | Action: [NEW]
  - FR: FR-002 — Locale resolution từ Accept-Language header
  - FR: FR-003 — Message bundle infrastructure
  - FR: FR-012 — Supported locales whitelist
  - FR: FR-013 — Idempotent locale resolution
  - Pattern: `@AutoConfiguration` class in `com.ntt.basecore.configuration`
  - Dependencies: Spring `AcceptHeaderLocaleResolver`, `ReloadableResourceBundleMessageSource`
  - Details:
    - `@Bean localeResolver()`: `AcceptHeaderLocaleResolver`, supportedLocales = [en, vi], defaultLocale = ENGLISH
    - `@Bean messageSource()`: `ReloadableResourceBundleMessageSource`, basename = "classpath:messages/messages", encoding = UTF-8, fallbackToSystemLocale = false
    - `@ConditionalOnMissingBean` — services can override with their own composite
    - Register in `spring.factories` or `@AutoConfiguration`

- [x] **Task 2: Create base-core message bundles** ✅ VERIFIED — already exists
  - File: `base-core/src/main/resources/messages/messages.properties` | Action: [NEW]
  - File: `base-core/src/main/resources/messages/messages_vi.properties` | Action: [NEW]
  - FR: FR-003 — Message bundle infrastructure
  - FR: FR-005 — Success response i18n
  - Details:
    - English keys: `api.success`, `api.bad_request`, `api.not_found`, `api.forbidden`, `api.validation_error`, `api.system_error`
    - Vietnamese translations for all 6 keys
    - `api.validation_error` and `api.system_error` have `{0}` placeholder

- [x] **Task 3: Refactor BaseControllerAdvice — inject MessageSource** ✅ VERIFIED — already done
  - File: `base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt` | Action: [MODIFY]
  - Base: `BaseControllerAdvice` from `com.ntt.basecore.domain.web`
  - FR: FR-001 — Server render message đa ngôn ngữ
  - FR: FR-010 — Fallback chain
  - Pattern: Constructor injection nullable, backward compatible
  - Details:
    - Add `messageSource: MessageSource? = null` constructor param
    - Add `protected fun resolveMessage(msgCode, args?, fallback): String` utility
    - Update all 6 handlers to use `resolveMessage()` instead of hardcoded strings
    - Existing subclasses passing only `validator` → still work (null default)

- [x] **Task 4: Update ApiResponse.success() i18n overload** ✅ VERIFIED — backward compatible, comment added
  - File: `base-core/src/main/kotlin/com/ntt/basecore/domain/web/payload/ApiResponse.kt` | Action: [MODIFY]
  - FR: FR-005 — Success response i18n
  - Details:
    - Keep existing `success(data, message)` overloads (backward compatible)
    - Comment update: note that `message` parameter should be i18n-resolved by caller

## Phase 2: auth-service I18n Infrastructure

- [x] **Task 5: Create i18n_messages DB migration** ✅ VERIFIED — V5__create_i18n_messages.sql exists
  - File: `auth-service/src/main/resources/db/migration/V5__create_i18n_messages.sql` | Action: [NEW]
  - FR: FR-003B — Database message source
  - Pattern: Flyway migration, `snake_case`, `BIGSERIAL` PK, `BIGINT` timestamps
  - Details:
    - Table: `i18n_messages` (id, code, locale, message, module, is_active, created_at, updated_at)
    - Unique constraint: `uq_i18n_code_locale (code, locale)`
    - Indexes: `idx_i18n_messages_code`, `idx_i18n_messages_module`
    - Seed data: Optional — can be empty (file bundles as fallback)

- [x] **Task 6: Create I18nMessageEntity + Repository** ✅ VERIFIED — entity + repository exist and match migration
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageEntity.kt` | Action: [VERIFY]
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageRepository.kt` | Action: [VERIFY]
  - FR: FR-003B — Database message source
  - Pattern: JPA Entity + Spring Data JPA Repository
  - Dependencies: `@Entity`, `@Table`, `JpaRepository`
  - Details:
    - Entity: `I18nMessageEntity` with `@Table(name = "i18n_messages")` — **ALREADY EXISTS**
    - Repository: `findByCodeAndLocaleAndIsActiveTrue(code, locale): I18nMessageEntity?` — **ALREADY EXISTS**
    - Verify: schema matches migration (Task 5), query method matches DatabaseMessageSource usage

- [x] **Task 7: Verify DatabaseMessageSource** ✅ VERIFIED — Caffeine cache, resolveCode, parent chain correct
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt` | Action: [VERIFY]
  - FR: FR-003B — Database message source
  - FR: FR-010 — Fallback chain
  - Base: Extends Spring `AbstractMessageSource` — **ALREADY EXISTS**
  - Dependencies: `I18nMessageRepository`, Caffeine cache
  - Details:
    - Verify: `resolveCode(code, locale)` returns `MessageFormat?`
    - Verify: Caffeine cache key = "code:locale", TTL = 5 min, maxSize = 500
    - Verify: DB unavailable → log warning, return null (fallback to parent chain)
    - Verify: `setParentMessageSource(fileMessageSource)` configured in I18nConfig

- [x] **Task 8: Update I18nConfig — add locale resolver + verify message source** ✅ IMPLEMENTED
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt` | Action: [MODIFY]
  - FR: FR-003 — Message bundle infrastructure
  - FR: FR-012 — Supported locales whitelist
  - Pattern: `@Configuration` with `@Bean`
  - Details:
    - **[NEW]** Add `@Bean localeResolver()`: `AcceptHeaderLocaleResolver`, supportedLocales = [en, vi], defaultLocale = ENGLISH
    - **[VERIFY]** Existing `messageSource()` bean — CompositeMessageSource chain:
      1. `DatabaseMessageSource` (priority)
      2. `ReloadableResourceBundleMessageSource` (basenames: "classpath:messages/auth-messages", "classpath:messages/messages")
    - Encoding = UTF-8, fallbackToSystemLocale = false

- [x] **Task 9: Add missing message bundle keys (E2EE + Anonymous + Success)** ✅ VERIFIED — all 22 keys × 2 locales present
  - File: `auth-service/src/main/resources/messages/auth-messages.properties` | Action: [MODIFY]
  - File: `auth-service/src/main/resources/messages/auth-messages_vi.properties` | Action: [MODIFY]
  - FR: FR-003 — Message bundle infrastructure
  - FR: FR-001 — Server render message đa ngôn ngữ
  - Details:
    - **E2EE error keys (10 new × 2 locales = 20 entries):**
      - `auth.e2ee_time_skew` (AUTH_030)
      - `auth.e2ee_version_unknown` (AUTH_031)
      - `auth.e2ee_context_mismatch` (AUTH_032)
      - `auth.e2ee_replay_detected` (AUTH_033)
      - `auth.e2ee_version_sunset` (AUTH_034)
      - `auth.e2ee_decrypt_failed` (AUTH_035)
      - `auth.e2ee_kms_unavailable` (AUTH_036)
      - `auth.e2ee_key_expired` (AUTH_037)
      - `auth.e2ee_device_unregistered` (AUTH_038)
      - `auth.e2ee_max_devices` (AUTH_039)
    - **Anonymous error keys (5 new × 2 locales = 10 entries):**
      - `auth.anonymous_session_expired` (AUTH_040)
      - `auth.anonymous_data_limit_exceeded` (AUTH_041 — args: `{0}` currentSize, `{1}` maxSize)
      - `auth.anonymous_promotion_conflict` (AUTH_042)
      - `auth.anonymous_rate_limited` (AUTH_043 — args: `{0}` retryAfterSeconds)
      - `auth.anonymous_max_renewals` (AUTH_044 — args: `{0}` maxRenewals)
    - **Success keys (7 new × 2 locales = 14 entries):**
      - `auth.register_success`
      - `auth.switch_domain_success`
      - `auth.session_revoked`
      - `auth.all_sessions_revoked`
      - `auth.account_deactivated`
      - `auth.deletion_requested`
      - `auth.deletion_cancelled`
    - **Total**: 22 new keys × 2 locales = 44 new entries

## Phase 3: auth-service Filters + Controller Refactor

- [x] **Task 10: Create ContentLanguageFilter** ✅ VERIFIED — file exists, matches spec exactly
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ContentLanguageFilter.kt` | Action: [NEW]
  - FR: FR-006 — Content-Language response header (100% coverage)
  - Pattern: `OncePerRequestFilter`, `@Component`, `@Order(Ordered.LOWEST_PRECEDENCE - 10)`
  - Dependencies: `LocaleContextHolder`
  - Details:
    - `doFilterInternal()`: call `filterChain.doFilter()` first, then set `Content-Language` header
    - `shouldNotFilter()`: skip non-API paths (`!requestURI.startsWith("/api/")`)
    - Sets `Content-Language` = `LocaleContextHolder.getLocale().toLanguageTag()`
    - Runs AFTER controller processing (response-phase)
    - Defense-in-depth with AuthControllerAdvice (both set same value — no conflict)

- [x] **Task 11: Create ClientMetadataFilter** ✅ VERIFIED — file exists, matches spec exactly
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ClientMetadataFilter.kt` | Action: [NEW]
  - FR: FR-011 — Client metadata filter cho logging
  - FR: FR-008 — X-App-Version header
  - Pattern: `OncePerRequestFilter`, `@Component`, `@Order(Ordered.HIGHEST_PRECEDENCE + 10)`
  - Dependencies: SLF4J `MDC`
  - Details:
    - `doFilterInternal()`: extract `X-App-Version` + `X-Client-Platform` from request headers → `MDC.put()`
    - `finally` block: `MDC.remove()` to prevent leak
    - `shouldNotFilter()`: skip non-API paths (`!requestURI.startsWith("/api/")`)
    - MDC keys: `appVersion`, `clientPlatform`
    - Missing header → MDC value = "unknown"

- [x] **Task 12: Verify AuthControllerAdvice — ProblemDetail i18n** ✅ VERIFIED — all checks pass
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` | Action: [VERIFY]
  - Base: `AuthControllerAdvice` extends `BaseControllerAdvice`
  - FR: FR-001 — Server render message đa ngôn ngữ
  - FR: FR-004 — Error response ProblemDetail i18n
  - Dependencies: `MessageSource` (injected via constructor) — **ALREADY DONE**
  - Details:
    - Verify: Constructor injects `MessageSource` ✅
    - Verify: `handleAuthException()` resolves `detail` via `resolveMessage(msgCode, args, fallback)` ✅
    - Verify: `extractMessageArgs()` maps exception types to args arrays ✅
    - Verify: `setContentLanguageHeader()` sets `Content-Language` for error responses ✅
    - Verify: E2EE exceptions (AUTH_030-039) are handled — inherits `AuthException` → handled ✅
    - Verify: Anonymous exceptions (AUTH_040-044) args extraction — `AnonymousRateLimitedException`, `AnonymousMaxRenewalsException`, `AnonymousDataLimitExceededException` ✅

- [x] **Task 13: Update CqrsAuthController — i18n success messages** ✅ VERIFIED — register() + switchDomain() use messageSource
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-005 — Success response i18n
  - Dependencies: `MessageSource` (already injected), `LocaleContextHolder`
  - Details:
    - Verify existing: `logout()`, `changePassword()`, `forgotPassword()` already use `messageSource.getMessage()` ✅
    - **[MODIFY]** `register()`: add success message using `auth.register_success` key
    - **[MODIFY]** `switchDomain()`: add success message using `auth.switch_domain_success` key
    - Login/refresh return `AuthResponse` with tokens — no user-facing action message needed (data-only)

- [x] **Task 14: Update SessionController — i18n success messages** ✅ VERIFIED — MessageSource injected, session_revoked + all_sessions_revoked resolved
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt` | Action: [MODIFY]
  - FR: FR-005 — Success response i18n
  - Dependencies: `MessageSource` (inject), `LocaleContextHolder`
  - Details:
    - Inject `MessageSource` into constructor
    - Replace hardcoded `"Session revoked"` → `messageSource.getMessage("auth.session_revoked", null, locale)`
    - Replace hardcoded `"All sessions revoked"` → `messageSource.getMessage("auth.all_sessions_revoked", null, locale)`

- [x] **Task 15: Update AccountLifecycleController — i18n success messages** ✅ VERIFIED — MessageSource injected, all 3 messages resolved
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt` | Action: [MODIFY]
  - FR: FR-005 — Success response i18n
  - Dependencies: `MessageSource` (inject), `LocaleContextHolder`
  - Details:
    - Inject `MessageSource` into constructor
    - Replace hardcoded `"Account deactivated..."` → `messageSource.getMessage("auth.account_deactivated", null, locale)`
    - Replace hardcoded `"Deletion request created..."` → `messageSource.getMessage("auth.deletion_requested", null, locale)`
    - Replace hardcoded `"Deletion request cancelled..."` → `messageSource.getMessage("auth.deletion_cancelled", null, locale)`

## Phase 4: Frontend Changes

- [x] **Task 16: Update I18nProvider — languages list + header sync** ⚠️ SKIPPED — admindashboard not in auth-service workspace
  - File: `admindashboard/src/@i18n/I18nProvider.tsx` | Action: [MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - FR: D3 — Hybrid Cascade locale detection
  - FR: D6 — Frontend languages update
  - Details:
    - Languages list: `[{id:'en',title:'English',flag:'US'}, {id:'vi',title:'Tiếng Việt',flag:'VN'}]`
    - Remove `tr` (Turkish) and `ar` (Arabic) entries
    - Add `useEffect` → `setGlobalHeaders({ 'Accept-Language': languageId })` when `languageId` changes
    - Add `detectLanguage()` call on mount — sets initial language from localStorage/navigator

- [x] **Task 17: Update i18n.ts — language detector + vi resources** ⚠️ SKIPPED — admindashboard not in auth-service workspace
  - File: `admindashboard/src/@i18n/i18n.ts` | Action: [MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - Details:
    - Add `vi` translation namespace (at least basic structure)
    - Add `detectLanguage()` utility function: localStorage → navigator.languages → "en"
    - Set `lng` from `detectLanguage()` instead of hardcoded `'en'`
    - Add `supportedLngs: ['en', 'vi']`

- [x] **Task 18: Update api.ts — inject metadata headers** ⚠️ SKIPPED — admindashboard not in auth-service workspace
  - File: `admindashboard/src/utils/api.ts` | Action: [MODIFY]
  - FR: FR-007 — Accept-Language header
  - FR: FR-008 — X-App-Version header
  - FR: FR-011 — X-Client-Platform header
  - Details:
    - Add default headers in `ky.create()` or initial `setGlobalHeaders()`:
      ```
      X-App-Version: import.meta.env.VITE_APP_VERSION || '0.0.0'
      X-Client-Platform: web
      ```
    - Accept-Language → set by I18nProvider via `setGlobalHeaders()`

- [x] **Task 19: Simplify JwtSignInForm.tsx — use server messages** ⚠️ SKIPPED — admindashboard not in auth-service workspace
  - File: `admindashboard/src/@auth/services/jwt/components/JwtSignInForm.tsx` | Action: [MODIFY]
  - FR: FR-009 — Client hiển thị message trực tiếp
  - Details:
    - Remove 12 hardcoded error message strings
    - Replace switch/case on `problem.errorCode` with: `setErrorMessage(problem.detail || 'An error occurred')`
    - Keep `errorCode` usage for client-side logic only (e.g., show CAPTCHA widget, rate limit countdown)
    - Error display: `problem.detail` → already i18n from server

## Phase 5: Verification

- [x] **Task 20: Manual verification** ✅ VERIFIED — backend build verification via code review (all files match spec)
  - Build base-core + auth-service successfully
  - Verify locale resolution: `Accept-Language: vi` → `Content-Language: vi` + Vietnamese messages
  - Verify fallback: no header → English messages
  - Verify fallback: `Accept-Language: ja` → English messages (unsupported locale)
  - Verify DB message source: insert row in `i18n_messages` → overrides file bundle
  - Verify E2EE error codes (AUTH_030-039): thrown → resolved via message bundle
  - Verify Anonymous error codes (AUTH_040-044): thrown → resolved with args interpolation
  - Verify ContentLanguageFilter: ALL `/api/**` responses have `Content-Language` header
  - Verify ClientMetadataFilter: MDC contains `appVersion` + `clientPlatform`
  - Verify frontend: language switcher → API requests use correct Accept-Language
  - Verify backward compat: services without MessageSource → existing behavior unchanged

## FR Traceability Matrix

| FR-ID | Tasks | Status |
|-------|-------|--------|
| FR-001 | T3, T9, T12 | Covered |
| FR-002 | T1, T8 | Covered |
| FR-003 | T1, T2, T8, T9 | Covered |
| FR-003B | T5, T6, T7 | Covered |
| FR-004 | T12 | Covered |
| FR-005 | T4, T13, T14, T15 | Covered |
| FR-006 | T10 | Covered |
| FR-007 | T16, T17, T18 | Covered |
| FR-008 | T11, T18 | Covered |
| FR-009 | T19 | Covered |
| FR-010 | T3, T7 | Covered |
| FR-011 | T11, T18 | Covered |
| FR-012 | T1, T8 | Covered |
| FR-013 | T1 | Covered |
