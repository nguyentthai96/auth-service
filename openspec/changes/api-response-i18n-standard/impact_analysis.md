## Impact Analysis: api-response-i18n-standard

### 1. Core Files (Affected)

| # | File | Type | Action | Reason |
|---|------|------|--------|--------|
| 1 | `base-core/.../web/BaseControllerAdvice.kt` | Shared library | [MODIFY] | Inject `MessageSource`, resolve i18n messages in all error handlers |
| 2 | `base-core/.../payload/ApiResponse.kt` | Shared library | [MODIFY] | Update `success()` factory to accept i18n message |
| 3 | `base-core/.../configuration/BaseCoreServletAutoConfiguration.kt` | Config | [MODIFY] | Add `LocaleResolver` + `MessageSource` beans |
| 4 | `auth-service/.../exception/GlobalExceptionHandler.kt` | Service | [MODIFY] | Use `MessageSource` for `ProblemDetail.detail` i18n |
| 5 | `auth-service/.../web/CqrsAuthController.kt` | Controller | [MODIFY] | Pass i18n success messages |
| 6 | `admindashboard/src/utils/api.ts` | Frontend | [MODIFY] | Inject `Accept-Language`, `X-App-Version`, `X-Client-Platform` headers |
| 7 | `admindashboard/src/@i18n/I18nProvider.tsx` | Frontend | [MODIFY] | Update languages list, sync `Accept-Language` header |
| 8 | `admindashboard/src/@i18n/i18n.ts` | Frontend | [MODIFY] | Add language detector, `vi` resources |
| 9 | `admindashboard/src/@auth/.../JwtSignInForm.tsx` | Frontend | [MODIFY] | Replace hardcoded messages with `problem.detail` |

### 2. New Files

| # | File | Type | Purpose |
|---|------|------|---------|
| 1 | `base-core/.../configuration/I18nAutoConfiguration.kt` | Config | MessageSource + LocaleResolver beans |
| 2 | `base-core/src/main/resources/messages.properties` | Resource | Default (English) message bundle |
| 3 | `base-core/src/main/resources/messages_vi.properties` | Resource | Vietnamese message bundle |
| 4 | `auth-service/src/main/resources/messages.properties` | Resource | Auth-specific English messages |
| 5 | `auth-service/src/main/resources/messages_vi.properties` | Resource | Auth-specific Vietnamese messages |
| 6 | `auth-service/V5__create_i18n_messages.sql` | Migration | `i18n_messages` table for dynamic messages |
| 7 | `auth-service/.../I18nMessageEntity.kt` | Entity | JPA entity for i18n messages |
| 8 | `auth-service/.../I18nMessageRepository.kt` | Repository | JPA repository for i18n messages |
| 9 | `auth-service/.../DatabaseMessageSource.kt` | Service | Custom `MessageSource` backed by database |

### 3. Call Tree (BaseControllerAdvice refactor)

```
BaseControllerAdvice (MODIFIED)
├── constructor(validator, messageSource?)  ← NEW parameter (nullable)
├── handleForbidden()
│   └── MessageSource.getMessage("api.forbidden", args, locale)
├── handleBadRequestException()
│   └── MessageSource.getMessage("api.bad_request", args, locale)
├── handleNotFound()
│   └── MessageSource.getMessage("api.not_found", args, locale)
├── handleBusinessException()
│   └── MessageSource.getMessage(err.msgCode, args, locale)
├── processValidationError()
│   └── MessageSource.getMessage("api.validation_error", args, locale)
└── handleException()
    └── MessageSource.getMessage("api.system_error", args, locale)
         + correlationId in response

AuthControllerAdvice (MODIFIED) extends BaseControllerAdvice
├── constructor(validator, messageSource?)  ← pass to super
└── handleAuthException()
    └── MessageSource.getMessage(authError.msgCode, args, locale)
         → ProblemDetail.detail = resolved message
```

### 4. Blast Radius

| Level | Component | Impact |
|-------|-----------|--------|
| d=1 (direct) | `BaseControllerAdvice` → ALL services using base-core | 🟡 Medium — nullable `MessageSource`, backward compatible |
| d=1 (direct) | `ApiResponse.success()` → ALL controllers calling success | 🟢 Low — overload, existing calls unaffected |
| d=1 (direct) | `api.ts` → ALL frontend API calls | 🟢 Low — only adds headers, no breaking change |
| d=2 (indirect) | auth-service, other services | 🟢 Low — services opting in to `MessageSource` get i18n, others unchanged |

**Overall Risk**: 🟢 **LOW** — all changes backward compatible via nullable injection + method overloads.

### 5. Reuse Map

| Pattern | Source | Reuse |
|---------|--------|-------|
| `ErrorCodeBase.msgCode` | base-core | ✅ Direct key for MessageSource |
| `ApiResponse.errorLang()` | base-core | ✅ Wire MessageSource as resolver |
| `setGlobalHeaders()` | admindashboard/api.ts | ✅ Inject Accept-Language |
| `I18nProvider.changeLanguage()` | admindashboard/@i18n | ✅ Extend to sync header |
| `MfaRateLimitService` Redis pattern | auth-service | ⬜ Not applicable |
| Spring `AcceptHeaderLocaleResolver` | Spring Framework | ✅ Built-in, zero custom code |
| Spring `ReloadableResourceBundleMessageSource` | Spring Framework | ✅ Built-in for file bundles |
| Spring `AbstractMessageSource` | Spring Framework | ✅ Extend for DB-backed source |

### 6. Context Snapshot

**Design Decision (from brainstorm):**
- Dual MessageSource: Common static codes → `.properties` file, dynamic messages → database table `i18n_messages`
- Locale detection: Hybrid Cascade (`localStorage` → `navigator.languages` → `"en"`)
- Supported locales: `en` (default), `vi`
- Format: BCP 47 (RFC 5646)

**User Decisions:**
- base-core errors → i18n via `Accept-Language` header ✅
- `ApiResponse.success()` → i18n ✅
- Common codes → file, dynamic codes → DB table ✅
