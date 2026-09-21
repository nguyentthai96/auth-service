# Design: controller-request-context

_Type: EXTEND | Flow: Command | Date: 2026-09-21_

## 1. Architecture Overview

```
   HTTP Request (headers: Authorization, X-Correlation-ID, X-App-Version, ...)
       │
       ▼
┌──────────────────────────┐
│ JwtAuthFilter            │  [SecurityFilterChain]
│ → SecurityContext:       │  userId, jti, roles, authorities
│   principal=userId       │
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│ RequestContextFilter     │  @Order(LOWEST_PRECEDENCE - 20)
│ → Populate @RequestScope │  RequestContext bean
│   bean from:             │
│   - SecurityContext      │  (userId, jti, roles)
│   - HttpServletRequest   │  (headers → ip, ua, device, correlation)
│ → Set MDC keys:          │  correlationId, userId, clientIp,
│   appVersion, platform   │  (absorbed from ClientMetadataFilter)
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│ ContentLanguageFilter    │  @Order(LOWEST_PRECEDENCE - 10)
│ → Response header only   │  (unchanged)
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│ DispatcherServlet        │
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│ Controller               │  extends BaseController / AuthenticatedController / AdminController
│ ├ requestContext.userId  │  ← from @RequestScope bean
│ ├ ok(body)               │  ← response helper
│ ├ okMessage(key)         │  ← i18n response helper
│ ├ message(key, args)     │  ← i18n resolution
│ └ request.toCommand(ctx) │  ← DTO→Command extension function
└──────────┬───────────────┘
           ▼
┌──────────────────────────┐
│ Handler.handle(command)  │  Pure business logic
└──────────────────────────┘
```

## 2. Component Design

### 2.1 RequestContext (@RequestScope Bean)

**Package**: `com.ntt.authservice.shared.web`
**Lifecycle**: Created per HTTP request, destroyed after response

```kotlin
@Component
@RequestScope
class RequestContext {

    // === Authentication (from SecurityContext) ===
    var userId: Long? = null
    var username: String? = null
    var jti: String? = null
    var roles: Set<String> = emptySet()
    var authenticated: Boolean = false

    // === Request metadata (from HttpServletRequest headers) ===
    var clientIp: String = "unknown"
    var userAgent: String? = null
    var deviceFingerprint: String? = null

    // === Correlation & tracing ===
    var correlationId: String = UUID.randomUUID().toString()
    var requestId: String = UUID.randomUUID().toString()

    // === Locale ===
    var locale: Locale = Locale.getDefault()

    // === Anonymous session (optional) ===
    var anonymousSessionId: String? = null
    var anonymousTokenJti: String? = null

    // === Client metadata (absorbed from ClientMetadataFilter) ===
    var appVersion: String = "unknown"
    var clientPlatform: String = "unknown"

    // === Helper methods ===
    fun requireUserId(): Long =
        userId ?: throw InvalidCredentialsException()

    fun requireJti(): String =
        jti ?: throw InvalidCredentialsException()

    fun hasRole(role: String): Boolean = roles.contains(role)

    fun isAdmin(): Boolean =
        hasRole("ROLE_ADMIN") || hasRole("ROLE_SUPER_ADMIN")
}
```

**Design decisions:**
- Mutable `var` thay vì immutable `val` — vì filter populate sau khi bean khởi tạo
- `@RequestScope` đảm bảo thread-safety (1 instance per request)
- `requireUserId()` throw `InvalidCredentialsException` — giữ behavior tương đương code cũ

### 2.2 RequestContextFilter

**Package**: `com.ntt.authservice.shared.web`
**Order**: `LOWEST_PRECEDENCE - 20` (sau Security, trước ContentLanguageFilter)

```kotlin
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
class RequestContextFilter(
    private val requestContext: ObjectProvider<RequestContext>
) : OncePerRequestFilter() {

    override fun doFilterInternal(request, response, chain) {
        val ctx = requestContext.ifAvailable ?: run {
            chain.doFilter(request, response)
            return
        }

        try {
            // 1. Authentication from SecurityContext
            populateFromSecurityContext(ctx)

            // 2. Request metadata from headers
            populateFromHeaders(ctx, request)

            // 3. Client metadata (absorbed from ClientMetadataFilter)
            populateClientMetadata(ctx, request)

            // 4. MDC propagation (consolidated — single source)
            setMdc(ctx)

            chain.doFilter(request, response)
        } finally {
            clearMdc()
        }
    }

    override fun shouldNotFilter(request) = !request.requestURI.startsWith("/api/")
}
```

**Design decisions:**
- `ObjectProvider<RequestContext>` thay vì direct inject — graceful handling khi context unavailable (background threads, non-web contexts)
- `shouldNotFilter`: Skip non-API paths (Actuator, static resources) — giữ consistent với ClientMetadataFilter cũ
- MDC cleanup trong `finally` — prevent thread-pool leaks

### 2.3 Base Controller Hierarchy

```
BaseController (abstract)
├── requestContext: RequestContext
├── messageSource: MessageSource
├── ok(body): ResponseEntity<T>
├── ok(body, messageKey): ResponseEntity<*>
├── okMessage(messageKey, args): ResponseEntity<*>
├── created(body): ResponseEntity<T>
├── noContent(): ResponseEntity<Void>
├── message(key, vararg args): String
└── pagedResponse(page: Page<T>): ResponseEntity<*>

AuthenticatedController (abstract) extends BaseController
├── currentUserId(): Long    → requestContext.requireUserId()
├── currentJti(): String     → requestContext.requireJti()
├── currentRoles(): Set<String> → requestContext.roles
└── requireRole(role): Unit  → throw AccessDeniedException if missing

AdminController (abstract) extends AuthenticatedController
└── requireAdmin(): Unit     → requireRole("ROLE_ADMIN") or "ROLE_SUPER_ADMIN"
```

**Package**: `com.ntt.authservice.shared.web`

**Constructor pattern** (Kotlin):
```kotlin
abstract class BaseController(
    protected val requestContext: RequestContext,
    protected val messageSource: MessageSource
)

abstract class AuthenticatedController(
    requestContext: RequestContext,
    messageSource: MessageSource
) : BaseController(requestContext, messageSource)

abstract class AdminController(
    requestContext: RequestContext,
    messageSource: MessageSource
) : AuthenticatedController(requestContext, messageSource)
```

**Design decisions:**
- Constructor injection (Spring Boot rules — no field @Autowired)
- `protected` visibility cho requestContext/messageSource — subclass có thể access trực tiếp
- `AuthenticatedController` không thêm field mới — chỉ helper methods dùng requestContext

### 2.4 Controller Migration Pattern

**Before** (SessionController):
```kotlin
@RestController
@RequestMapping("/auth/sessions")
class SessionController(
    private val loginSessionService: LoginSessionService,
    private val messageSource: MessageSource
) {
    @DeleteMapping("/{id}")
    fun revoke(@PathVariable id: Long): ResponseEntity<*> {
        val userId = getCurrentUserId()
        val revoked = loginSessionService.revokeSession(id, userId, "MANUAL")
        val locale = LocaleContextHolder.getLocale()
        val message = messageSource.getMessage("auth.session_revoked", null, "...", locale)
        return ResponseEntity.ok(mapOf("message" to message))
    }
    private fun getCurrentUserId(): Long { ... } // DUPLICATE
}
```

**After**:
```kotlin
@RestController
@RequestMapping("/auth/sessions")
class SessionController(
    requestContext: RequestContext,
    messageSource: MessageSource,
    private val loginSessionService: LoginSessionService
) : AuthenticatedController(requestContext, messageSource) {

    @DeleteMapping("/{id}")
    fun revoke(@PathVariable id: Long): ResponseEntity<*> {
        val revoked = loginSessionService.revokeSession(id, currentUserId(), "MANUAL")
        return if (revoked) okMessage("auth.session_revoked") else ResponseEntity.notFound().build()
    }
    // NO private getCurrentUserId() — inherited from AuthenticatedController
}
```

### 2.5 AuditLogService Migration

**Before**:
```kotlin
@Service
class AuditLogService(...) {
    fun logEvent(userId: Long?, action: AuditAction, ...) {
        val request = getCurrentRequest()           // ← manual HttpServletRequest
        val ipAddress = request?.let { getClientIp(it) } ?: "unknown"  // ← duplicate
        val userAgent = request?.getHeader("User-Agent") ?: "unknown"
        ...
    }
    private fun getCurrentRequest(): HttpServletRequest? { ... }  // ← DELETE
    private fun getClientIp(request: HttpServletRequest): String { ... }  // ← DELETE
}
```

**After**:
```kotlin
@Service
class AuditLogService(
    private val requestContext: ObjectProvider<RequestContext>,  // ← inject
    ...
) {
    fun logEvent(userId: Long?, action: AuditAction, ...) {
        val ctx = requestContext.ifAvailable
        val ipAddress = ctx?.clientIp ?: "unknown"         // ← from RequestContext
        val userAgent = ctx?.userAgent ?: "unknown"        // ← from RequestContext
        ...
    }
    // getCurrentRequest() → DELETED
    // getClientIp() → DELETED
}
```

**Design decisions:**
- `ObjectProvider<RequestContext>` (not direct inject) — AuditLogService có thể được gọi từ non-web context (Kafka consumer, scheduled tasks) → graceful fallback
- Callers (8 services, ~25 calls) — **ZERO changes required**

### 2.6 DTO→Command Extension Function Convention

**Convention**: Extension function on DTO class, enriching with RequestContext data.

```kotlin
// In DtoMappers.kt or alongside DTO file
fun LoginRequestDto.toCommand(ctx: RequestContext, anonymousTokenJti: String? = null) = LoginCommand(
    username = username,
    password = password,
    domainCode = domainCode,
    captchaToken = captchaToken,
    trustedDeviceHash = trustedDeviceHash,
    ipAddress = ctx.clientIp,
    userAgent = ctx.userAgent,
    deviceFingerprint = ctx.deviceFingerprint,
    anonymousSessionId = anonymousSessionId,
    anonymousTokenJti = anonymousTokenJti,
    correlationId = ctx.correlationId
)
```

## 3. File Layout

```
src/main/kotlin/com/ntt/authservice/
├── shared/
│   └── web/                           ← [NEW PACKAGE]
│       ├── RequestContext.kt           ← [NEW] @RequestScope bean
│       ├── RequestContextFilter.kt     ← [NEW] OncePerRequestFilter
│       ├── BaseController.kt           ← [NEW] Abstract base
│       ├── AuthenticatedController.kt  ← [NEW] + currentUserId/jti/roles
│       └── AdminController.kt          ← [NEW] + requireAdmin
├── auth/adapter/in/web/
│   ├── CqrsAuthController.kt          ← [MODIFY] extends BaseController
│   ├── SessionController.kt           ← [MODIFY] extends AuthenticatedController
│   ├── MfaController.kt               ← [MODIFY] extends AuthenticatedController
│   ├── DeviceController.kt            ← [MODIFY] extends AuthenticatedController
│   ├── SsoController.kt               ← [MODIFY] extends AuthenticatedController
│   ├── AccountLifecycleController.kt  ← [MODIFY] extends AuthenticatedController
│   ├── TokenController.kt             ← [MODIFY] extends AuthenticatedController
│   ├── AdminUnlockController.kt       ← [MODIFY] extends AdminController
│   ├── AdminSessionController.kt      ← [MODIFY] extends AdminController
│   ├── RateLimitAdminController.kt    ← [MODIFY] extends AdminController
│   ├── AnonymousAuthController.kt     ← [MODIFY] extends BaseController
│   ├── CaptchaController.kt           ← [MODIFY] extends BaseController
│   ├── InternalApiController.kt       ← [MODIFY] extends BaseController
│   ├── KeyExchangeController.kt       ← [MODIFY] extends BaseController
│   ├── EventStoreController.kt        ← [MODIFY] extends BaseController
│   ├── dto/DtoMappers.kt              ← [NEW] extension functions
│   └── filter/
│       └── ClientMetadataFilter.kt    ← [DELETE] absorbed into RequestContextFilter
├── rbac/adapter/in/web/
│   └── RbacControllers.kt             ← [MODIFY] 5 controllers extend BaseController
├── pbac/adapter/in/web/
│   └── PolicyController.kt            ← [MODIFY] extends BaseController
└── shared/audit/
    └── AuditLogService.kt             ← [MODIFY] inject RequestContext

[EXCLUDE]
├── SelfServiceUnlockController.kt     ← Thymeleaf SSR (user tách frontend)
└── LoginRateLimitFilter.kt            ← Pre-auth filter (giữ extractClientIp riêng)
```

## 4. Controller → Base Class Mapping

| Controller | Base Class | Reason |
|------------|-----------|--------|
| CqrsAuthController | BaseController | Has both public + auth endpoints (login, register = public; switch-domain = auth) |
| AnonymousAuthController | BaseController | Public endpoints, no auth needed |
| CaptchaController | BaseController | Public endpoint |
| InternalApiController | BaseController | Service-to-service, no user auth |
| KeyExchangeController | BaseController | E2EE key exchange, no user auth |
| EventStoreController | BaseController | Event query, minimal auth |
| SessionController | AuthenticatedController | All endpoints need userId |
| MfaController | AuthenticatedController | Most endpoints need userId (verify uses mfaToken) |
| DeviceController | AuthenticatedController | All endpoints need userId |
| SsoController | AuthenticatedController | Link/unlink need userId |
| AccountLifecycleController | AuthenticatedController | All endpoints need userId |
| TokenController | AuthenticatedController | Token management needs auth |
| AdminUnlockController | AdminController | Admin-only unlock operations |
| AdminSessionController | AdminController | Admin session management |
| RateLimitAdminController | AdminController | Admin rate limit management |
| DomainController (RBAC) | BaseController | Admin CRUD, uses Spring Security @PreAuthorize |
| RoleController (RBAC) | BaseController | Admin CRUD |
| GroupController (RBAC) | BaseController | Admin CRUD |
| ResourceController (RBAC) | BaseController | Admin CRUD |
| PermissionCheckController (RBAC) | BaseController | Permission verification |
| PolicyController (PBAC) | BaseController | Policy management |

## 5. MDC Keys (Consolidated)

| Key | Source | Previous Location |
|-----|--------|-------------------|
| `correlationId` | X-Correlation-ID / X-Request-ID / UUID | Controller (inline) |
| `userId` | SecurityContext.authentication.principal | Not in MDC before |
| `clientIp` | X-Forwarded-For / remoteAddr | Not in MDC before |
| `appVersion` | X-App-Version | ClientMetadataFilter |
| `clientPlatform` | X-Client-Platform | ClientMetadataFilter |

## 6. FR Traceability

| FR | Component | Method/Field |
|----|-----------|-------------|
| FR-001 | RequestContext.kt | All fields + @RequestScope |
| FR-002 | RequestContextFilter.kt | doFilterInternal() |
| FR-003 | RequestContextFilter.kt | setMdc() / clearMdc() |
| FR-004 | BaseController.kt | ok/created/okMessage/pagedResponse/message |
| FR-005 | AuthenticatedController.kt | currentUserId/currentJti/currentRoles/requireRole |
| FR-006 | AdminController.kt | requireAdmin() |
| FR-007 | DtoMappers.kt | Extension functions toCommand() |
| FR-008 | RequestContextFilter.kt | correlationId extraction |
| FR-009 | RequestContextFilter.kt | clientIp extraction |
| FR-010 | All controllers | API contract unchanged |
| FR-011 | All controllers | Old/new coexist during migration |
| FR-012 | test/ | TestRequestContextFactory (future) |
