<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: api-response-i18n-standard

> **Type**: EXTEND | **Flow**: Command | **FRs**: 13 (9 IDEA + 4 ENRICHED)

## Phase 1: base-core Infrastructure (Foundation)

- [x] **Task 1: Create I18nAutoConfiguration**
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

- [x] **Task 2: Create base-core message bundles**
  - File: `base-core/src/main/resources/messages/messages.properties` | Action: [NEW]
  - File: `base-core/src/main/resources/messages/messages_vi.properties` | Action: [NEW]
  - FR: FR-003 — Message bundle infrastructure
  - FR: FR-005 — Success response i18n
  - Details:
    - English keys: `api.success`, `api.bad_request`, `api.not_found`, `api.forbidden`, `api.validation_error`, `api.system_error`
    - Vietnamese translations for all 6 keys
    - `api.validation_error` and `api.system_error` have `{0}` placeholder

- [x] **Task 3: Refactor BaseControllerAdvice — inject MessageSource**
  - File: `base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt` | Action: [MODIFY]
  - Base: `BaseControllerAdvice` from `com.ntt.basecore.domain.web`
  - FR: FR-001 — Server render message đa ngôn ngữ
  - FR: FR-006 — Content-Language response header
  - FR: FR-010 — Fallback chain
  - Pattern: Constructor injection nullable, backward compatible
  - Details:
    - Add `messageSource: MessageSource? = null` constructor param
    - Add `protected fun resolveMessage(msgCode, args?, fallback): String` utility
    - Update all 6 handlers to use `resolveMessage()` instead of hardcoded strings
    - Set `Content-Language` header via `HttpServletResponse` parameter
    - Existing subclasses passing only `validator` → still work (null default)

- [x] **Task 4: Update ApiResponse.success() i18n overload**
  - File: `base-core/src/main/kotlin/com/ntt/basecore/domain/web/payload/ApiResponse.kt` | Action: [MODIFY]
  - FR: FR-005 — Success response i18n
  - Details:
    - Keep existing `success(data, message)` overloads (backward compatible)
    - Comment update: note that `message` parameter should be i18n-resolved by caller

## Phase 2: auth-service I18n Infrastructure

- [x] **Task 5: Create i18n_messages DB migration**
  - File: `auth-service/src/main/resources/db/migration/V5__create_i18n_messages.sql` | Action: [NEW]
  - FR: FR-003B — Database message source
  - Pattern: Flyway migration, `snake_case`, `BIGSERIAL` PK, `BIGINT` timestamps
  - Details:
    - Table: `i18n_messages` (id, code, locale, message, module, is_active, created_at, updated_at)
    - Unique constraint: `uq_i18n_code_locale (code, locale)`
    - Indexes: `idx_i18n_messages_code`, `idx_i18n_messages_module`
    - Seed data: Optional — can be empty (file bundles as fallback)

- [x] **Task 6: Create I18nMessageEntity + Repository**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageEntity.kt` | Action: [NEW]
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/I18nMessageRepository.kt` | Action: [NEW]
  - FR: FR-003B — Database message source
  - Pattern: JPA Entity + Spring Data JPA Repository
  - Dependencies: `@Entity`, `@Table`, `JpaRepository`
  - Details:
    - Entity: `I18nMessageEntity` with `@Table(name = "i18n_messages")`
    - Repository: `findByCodeAndLocaleAndIsActiveTrue(code, locale): I18nMessageEntity?`
    - Repository: `findByModuleAndLocaleAndIsActiveTrue(module, locale): List<I18nMessageEntity>`

- [x] **Task 7: Create DatabaseMessageSource**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/i18n/DatabaseMessageSource.kt` | Action: [NEW]
  - FR: FR-003B — Database message source
  - FR: FR-010 — Fallback chain
  - Base: Extend Spring `AbstractMessageSource`
  - Dependencies: `I18nMessageRepository`, Caffeine cache
  - Details:
    - Override `resolveCode(code, locale): MessageFormat?`
    - Caffeine cache: key = "code:locale", TTL = 5 min
    - DB unavailable → log warning, return null (fallback to parent chain)
    - `setParentMessageSource(fileMessageSource)` — chain fallback

- [x] **Task 8: Create auth-service I18n config + message bundles**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/config/I18nConfig.kt` | Action: [NEW]
  - File: `auth-service/src/main/resources/messages/auth-messages.properties` | Action: [NEW]
  - File: `auth-service/src/main/resources/messages/auth-messages_vi.properties` | Action: [NEW]
  - FR: FR-003 — Message bundle infrastructure
  - FR: FR-003B — Database message source
  - Pattern: `@Configuration` with `@Bean` overriding base-core default
  - Details:
    - Override base-core's `messageSource` bean with `CompositeMessageSource`:
      1. `DatabaseMessageSource` (priority)
      2. `ReloadableResourceBundleMessageSource` (basenames: "classpath:messages/auth-messages", "classpath:messages/messages")
    - Auth message bundles: 21 error codes (en + vi) from `AuthErrorCode.msgCode` keys
    - Include `auth.session_limit=Maximum active sessions ({0}) reached` with `{0}` placeholder

## Phase 3: auth-service Controller Refactor

- [x] **Task 9: Refactor AuthControllerAdvice — ProblemDetail i18n**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/GlobalExceptionHandler.kt` | Action: [MODIFY]
  - Base: `AuthControllerAdvice` extends `BaseControllerAdvice`
  - FR: FR-001 — Server render message đa ngôn ngữ
  - FR: FR-004 — Error response ProblemDetail i18n
  - FR: FR-006 — Content-Language response header
  - Dependencies: `MessageSource` (injected via super constructor)
  - Details:
    - Constructor: `AuthControllerAdvice(validator, messageSource)` → pass to `super(validator, messageSource)`
    - `handleAuthException()`: resolve `detail` via `resolveMessage(authError.msgCode, args, fallback)`
    - Map exception args: `RateLimitExceededException` → args = [retryAfterSeconds, dimension], `SessionLimitExceededException` → args = [maxSessions]
    - Set `Content-Language` header

- [x] **Task 10: Update CqrsAuthController — i18n success messages**
  - File: `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-005 — Success response i18n
  - Dependencies: `MessageSource`, `LocaleContextHolder`
  - Details:
    - Inject `MessageSource` into controller
    - Replace hardcoded "Success" strings: `messageSource.getMessage("api.success", null, LocaleContextHolder.getLocale())`
    - Or define auth-specific success keys: `auth.login_success`, `auth.token_refreshed`, etc.

## Phase 4: Frontend Changes

- [x] **Task 11: Update I18nProvider — languages list + header sync**
  - File: `admindashboard/src/@i18n/I18nProvider.tsx` | Action: [MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - FR: D3 — Hybrid Cascade locale detection
  - FR: D6 — Frontend languages update
  - Details:
    - Languages list: `[{id:'en',title:'English',flag:'US'}, {id:'vi',title:'Tiếng Việt',flag:'VN'}]`
    - Remove `tr` (Turkish) and `ar` (Arabic) entries
    - Add `useEffect` → `setGlobalHeaders({ 'Accept-Language': languageId })` when `languageId` changes
    - Add `detectLanguage()` call on mount — sets initial language from localStorage/navigator

- [x] **Task 12: Update i18n.ts — language detector + vi resources**
  - File: `admindashboard/src/@i18n/i18n.ts` | Action: [MODIFY]
  - FR: FR-007 — Client Accept-Language header
  - Details:
    - Add `vi` translation namespace (at least basic structure)
    - Add `detectLanguage()` utility function: localStorage → navigator.languages → "en"
    - Set `lng` from `detectLanguage()` instead of hardcoded `'en'`
    - Add `supportedLngs: ['en', 'vi']`

- [x] **Task 13: Update api.ts — inject metadata headers**
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

- [x] **Task 14: Simplify JwtSignInForm.tsx — use server messages**
  - File: `admindashboard/src/@auth/services/jwt/components/JwtSignInForm.tsx` | Action: [MODIFY]
  - FR: FR-009 — Client hiển thị message trực tiếp
  - Details:
    - Remove 12 hardcoded error message strings
    - Replace switch/case on `problem.errorCode` with: `setErrorMessage(problem.detail || 'An error occurred')`
    - Keep `errorCode` usage for client-side logic only (e.g., show CAPTCHA widget)
    - Error display: `problem.detail` → already i18n from server

## Phase 5: Verification

- [x] **Task 15: Manual verification**
  - Build base-core + auth-service successfully
  - Verify locale resolution: `Accept-Language: vi` → `Content-Language: vi` + Vietnamese messages
  - Verify fallback: no header → English messages
  - Verify DB message source: insert row in `i18n_messages` → overrides file bundle
  - Verify frontend: language switcher → API requests use correct Accept-Language
  - Verify backward compat: services without MessageSource → existing behavior unchanged
