# Impact Analysis: controller-request-context

_Generated: 2026-09-21 | Type: EXTEND | Confidence: HIGH_

## 1. Core Files (Files that will be created/modified/deleted)

### [NEW] Files — Infrastructure

| File | Package | Purpose |
|------|---------|---------|
| `RequestContext.kt` | `shared.web` | @RequestScope bean chứa tất cả request metadata |
| `RequestContextFilter.kt` | `shared.web` | OncePerRequestFilter populate RequestContext + MDC |
| `BaseController.kt` | `shared.web` | Abstract controller với response helpers + i18n |
| `AuthenticatedController.kt` | `shared.web` | Extends BaseController + currentUserId/jti/roles |
| `AdminController.kt` | `shared.web` | Extends AuthenticatedController + requireAdmin |

### [MODIFY] Files — Controller Migration

| File | Current Duplicate | After Migration |
|------|-------------------|-----------------|
| `CqrsAuthController.kt` | `getCurrentUserId()` + `extractClientIp()` + 5× `messageSource.getMessage()` | Extends BaseController, dùng requestContext |
| `SessionController.kt` | `getCurrentUserId()` + 2× `messageSource.getMessage()` | Extends AuthenticatedController |
| `MfaController.kt` | `getCurrentUserId()` + 2× `messageSource.getMessage()` | Extends AuthenticatedController |
| `DeviceController.kt` | `getCurrentUserId()` | Extends AuthenticatedController |
| `SsoController.kt` | `getCurrentUserId()` + 2× `messageSource.getMessage()` | Extends AuthenticatedController |
| `AccountLifecycleController.kt` | `getCurrentUserId()` + 3× `messageSource.getMessage()` | Extends AuthenticatedController |
| `AdminUnlockController.kt` | `getCurrentUserId()` | Extends AdminController |
| `AdminSessionController.kt` | 1× `messageSource.getMessage()` | Extends AdminController |
| `RateLimitAdminController.kt` | 1× `messageSource.getMessage()` | Extends AdminController |
| `TokenController.kt` | 1× `messageSource.getMessage()` | Extends AuthenticatedController |
| `AnonymousAuthController.kt` | `extractClientIp()` | Extends BaseController |
| `CaptchaController.kt` | — | Extends BaseController (minimal change) |
| `InternalApiController.kt` | — | Extends BaseController (minimal change) |
| `KeyExchangeController.kt` | — | Extends BaseController (minimal change) |
| `EventStoreController.kt` | — | Extends BaseController (minimal change) |
| `RbacControllers.kt` | — | 5 controllers extend BaseController/AuthenticatedController |
| `PolicyController.kt` | — | Extends BaseController |
| `AuditLogService.kt` | `getCurrentRequest()` + `getClientIp()` | Inject RequestContext, xóa 2 private methods |

### [DELETE] Files

| File | Reason |
|------|--------|
| `ClientMetadataFilter.kt` | Absorbed into RequestContextFilter |

### [EXCLUDE] Files

| File | Reason |
|------|--------|
| `SelfServiceUnlockController.kt` | Thymeleaf SSR — user sẽ tách frontend |
| `LoginRateLimitFilter.kt` | Pre-auth filter, cần extractClientIp riêng (filter-level concern) |

---

## 2. Call Tree (Dependency chain cho symbols bị modify)

```
getCurrentUserId() — 7 controllers
├── SecurityContextHolder.getContext().authentication?.principal
├── Cast to String → toLong()
└── Throw InvalidCredentialsException if null
  ↓ REFACTORED TO
RequestContext.requireUserId()
├── Read from RequestContext bean (pre-populated by filter)
└── Same throw behavior (InvalidCredentialsException)

extractClientIp() — 3 controllers + 1 AuditLogService
├── HttpServletRequest.getHeader("X-Forwarded-For")
├── Split(",").first().trim()
└── Fallback: request.remoteAddr
  ↓ REFACTORED TO
RequestContext.clientIp (String)
└── Pre-populated by RequestContextFilter (same logic, centralized)

messageSource.getMessage() — 18 calls across 10 controllers
├── messageSource.getMessage(key, args, default, locale)
├── locale = LocaleContextHolder.getLocale() ← also duplicated
  ↓ REFACTORED TO
BaseController.message(key, args)  → internal: messageSource.getMessage(key, args, requestContext.locale)
BaseController.okMessage(key)      → ResponseEntity.ok(mapOf("message" to message(key)))
```

---

## 3. Blast Radius

### Impact Level: 🟡 MEDIUM

| Metric | Value |
|--------|-------|
| Controllers modified | 17 (15 auth + 5 rbac merged in 1 file + 1 pbac) |
| Services modified | 1 (AuditLogService) |
| Filters created | 1 (RequestContextFilter) |
| Filters deleted | 1 (ClientMetadataFilter) |
| Total files touched | ~22 |
| Callers of AuditLogService | 8 services (~25 calls) — **NOT affected** (internal refactor only) |
| API contract changes | **ZERO** |
| Test impact | Existing tests should pass (behavioral equivalence) |

### Risk Assessment

| Risk | Level | Mitigation |
|------|-------|------------|
| @RequestScope + Virtual Threads | 🟡 | Test with `spring.threads.virtual.enabled=true` |
| Filter order change (ClientMetadata absorbed) | 🟢 | MDC keys still populated, just later in chain |
| AuditLogService — RequestContext null in non-web context | 🟡 | Use `ObjectProvider<RequestContext>` with fallback |
| Incremental migration break | 🟢 | Old/new can coexist — migration controller-by-controller |

---

## 4. Reuse Map

| Symbol | Occurrences | Decision | Action |
|--------|-------------|----------|--------|
| `getCurrentUserId()` | 7× (100% identical) | **EXTRACT** | → `AuthenticatedController.currentUserId()` |
| `extractClientIp()` | 3× controllers + 1× AuditLogService (100% identical) | **EXTRACT** | → `RequestContext.clientIp` (filter-populated) |
| `LocaleContextHolder.getLocale()` | ~18× in controllers | **EXTRACT** | → `RequestContext.locale` (filter-populated) |
| `messageSource.getMessage(key, args, default, locale)` | 18× | **EXTRACT** | → `BaseController.message(key, args)` |
| `ResponseEntity.ok(mapOf("message" to ...))` | ~12× | **EXTRACT** | → `BaseController.okMessage(key)` |
| `getClientIp()` (AuditLogService) | 1× (identical to extractClientIp) | **EXTRACT** | → `RequestContext.clientIp` (inject bean) |
| `getCurrentRequest()` (AuditLogService) | 1× | **DELETE** | No longer needed — RequestContext injected |
| `ClientMetadataFilter` logic | 1× (10 lines) | **ABSORB** | → Into RequestContextFilter |
| `setRefreshTokenCookie()` | 1× (CqrsAuthController only) | **KEEP** | Not common pattern |
| `extractAnonymousTokenJti()` | 1× (CqrsAuthController only) | **KEEP** | Feature-specific |

---

## 5. Context Snapshot

### Architecture State (Before)
```
Controller Layer (NO base class):
  ├── 17 standalone @RestController classes
  ├── Each: private getCurrentUserId(), extractClientIp()
  ├── Each: inject MessageSource, manually get locale
  ├── Each: inline ResponseEntity.ok(mapOf(...))
  └── No request context sharing between layers

Filter Layer:
  ├── ClientMetadataFilter [HIGHEST+10] → MDC: appVersion, platform
  ├── JwtAuthFilter [Security] → SecurityContext
  ├── LoginRateLimitFilter → extractClientIp (independent)
  ├── IdempotencyFilter → shared filter
  └── ContentLanguageFilter [LOWEST-10] → Response header

Service Layer:
  └── AuditLogService → getCurrentRequest() + getClientIp() (manual)
```

### Architecture State (After)
```
Controller Layer (BASE CLASS HIERARCHY):
  ├── BaseController (abstract)
  │   ├── RequestContext (injected, @RequestScope)
  │   ├── MessageSource (injected)
  │   ├── ok(body), created(body), okMessage(key), pagedResponse(page)
  │   └── message(key, args)
  ├── AuthenticatedController extends BaseController
  │   ├── currentUserId(), currentJti(), currentRoles()
  │   └── requireRole(role)
  ├── AdminController extends AuthenticatedController
  │   └── requireAdmin()
  └── 17 controllers extend appropriate base

Filter Layer:
  ├── JwtAuthFilter [Security] → SecurityContext
  ├── LoginRateLimitFilter → extractClientIp (independent, pre-auth)
  ├── RequestContextFilter [LOWEST-20] → Bean + ALL MDC
  │   ├── MDC: correlationId, userId, clientIp, appVersion, clientPlatform
  │   └── Bean: userId, jti, roles, clientIp, userAgent, locale, etc.
  ├── IdempotencyFilter → shared filter
  └── ContentLanguageFilter [LOWEST-10] → Response header

Service Layer:
  └── AuditLogService → RequestContext (injected, @RequestScope)
```

### Key Invariants Preserved
- API contract: **UNCHANGED** (zero endpoint changes)
- Exception behavior: Same `InvalidCredentialsException` when userId null
- MDC keys: Same keys (`appVersion`, `clientPlatform`, `correlationId`, `userId`, `clientIp`)
- Filter execution order: Functionally equivalent (MDC available before controllers)
- AuditLogService callers: **ZERO changes** required (internal refactor)
