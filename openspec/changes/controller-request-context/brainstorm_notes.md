---
type: brainstorm_notes
change: controller-request-context
date: 2026-09-21
selected_direction: "Approach B — Composite RequestContextFilter absorbs ClientMetadataFilter"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Controller Request Context Standardization

## Date
2026-09-21

## Context
User yêu cầu chuẩn hóa controller layer để loại bỏ code lặp (7× `getCurrentUserId()`, 3× `extractClientIp()`, 15× locale/i18n), tạo base controller hierarchy, và quản lý request context tập trung. Đã có pre_openspec.md với 12 FRs (quality 88/100).

## Questions Asked & Answers

### Q1: ClientMetadataFilter vs RequestContextFilter — merge hay coordinate?
**User answer**: Đồng ý merge. Muốn đề xuất phương án tối ưu nhất.

**Analysis — 3 phương án:**

```
═══ HIỆN TRẠNG ═══

    ┌──────────────────────────────────┐
    │ ClientMetadataFilter             │  @Order(HIGHEST + 10)
    │ → MDC: appVersion, clientPlatf.  │  (runs FIRST)
    └──────────────┬───────────────────┘
                   ↓
    ┌──────────────────────────────────┐
    │ JwtAuthFilter (Security Chain)   │
    │ → SecurityContext: userId, jti   │
    └──────────────┬───────────────────┘
                   ↓
    ┌──────────────────────────────────┐
    │ LoginRateLimitFilter             │  (needs clientIp)
    │ → extractClientIp() ← DUPLICATE │
    └──────────────┬───────────────────┘
                   ↓
    ┌──────────────────────────────────┐
    │ ContentLanguageFilter            │  @Order(LOWEST - 10)
    │ → Response: Content-Language     │  (runs LAST)
    └──────────────┬───────────────────┘
                   ↓
    Controller → getCurrentUserId() ← DUPLICATE
```

### Approach A: Coordinate — giữ 2 filter riêng, chỉ thêm RequestContextFilter

```
ClientMetadataFilter  [HIGHEST+10]  → MDC: appVersion, platform
JwtAuthFilter         [Security]    → SecurityContext
RequestContextFilter  [LOWEST-20]   → RequestContext bean + MDC: correlationId, userId, ip
ContentLanguageFilter [LOWEST-10]   → Response header
```

**Pros**: Zero migration risk, không sửa existing code
**Cons**: 2 filter cùng quản lý MDC → potential confusion, MDC keys scattered

### Approach B: Absorb — merge ClientMetadataFilter vào RequestContextFilter ⭐ SELECTED

```
JwtAuthFilter           [Security]    → SecurityContext
RequestContextFilter    [LOWEST-20]   → RequestContext bean + ALL MDC (appVersion, platform, correlation, userId, ip)
ContentLanguageFilter   [LOWEST-10]   → Response header (giữ nguyên — concern khác)
```

**Pros**:
- Single source of truth cho ALL request metadata (MDC + bean)
- Dễ trace — "muốn biết MDC nào → xem RequestContextFilter"
- ClientMetadataFilter code rất nhỏ (10 lines logic) → absorb dễ dàng
- Xóa 1 filter class → less complexity

**Cons**:
- ClientMetadataFilter run ở `HIGHEST+10`, RequestContextFilter run ở `LOWEST-20`
  → appVersion/platform MDC available muộn hơn (sau Security filter)
- ⚠️ Nhưng quan trọng: LoginRateLimitFilter chạy TRƯỚC SecurityFilter
  → cần clientIp TRƯỚC khi SecurityFilter chạy

**Mitigation**: RequestContextFilter cần run SAU Security (cần userId) nhưng LoginRateLimitFilter
cần clientIp TRƯỚC Security → giải quyết bằng cách LoginRateLimitFilter tự extract IP
(hoặc chấp nhận nó đã có `extractClientIp()` riêng — đây là filter, không phải controller).

### Approach C: MDC-only Filter + RequestScope bean riêng

```
RequestMetadataFilter   [HIGHEST+5]   → MDC only (appVersion, platform, correlationId, ip)
JwtAuthFilter           [Security]    → SecurityContext
RequestContextFilter    [LOWEST-20]   → @RequestScope bean (read MDC + SecurityContext)
```

**Pros**: MDC available sớm nhất, bean available muộn (sau auth)
**Cons**: 2 filter + 1 bean → over-engineering, MDC read from 2 nơi

### Decision: Approach B — Absorb ClientMetadataFilter

**Lý do chọn B:**
1. ClientMetadataFilter chỉ có 10 lines logic — đủ nhỏ để absorb
2. `appVersion`/`platform` MDC chỉ dùng cho logging → available SAU security filter vẫn OK
   (log entries quan trọng đều ở controller/service layer — chạy sau security)
3. Single filter quản lý ALL request metadata = dễ maintain nhất
4. LoginRateLimitFilter đặc thù (chạy trước auth, cần IP riêng) → chấp nhận nó giữ `extractClientIp()` riêng
   → đây là filter-level concern, không phải controller boilerplate

**Result:**
```
JwtAuthFilter            [Security]    → SecurityContext
RequestContextFilter     [LOWEST-20]   → RequestContext bean + ALL MDC
  ├── MDC: correlationId, userId, clientIp
  ├── MDC: appVersion, clientPlatform (absorbed from ClientMetadataFilter)
  ├── Bean: userId, jti, roles, authenticated
  ├── Bean: clientIp, userAgent, deviceFingerprint
  ├── Bean: correlationId, requestId, locale
  └── Bean: anonymousSessionId, anonymousTokenJti
ContentLanguageFilter    [LOWEST-10]   → Response: Content-Language
LoginRateLimitFilter     [its own]     → keeps own extractClientIp() (pre-auth filter)
```

---

### Q2: SelfServiceUnlockController (Thymeleaf SSR) — migrate hay exclude?
**User answer**: Exclude. Kế hoạch tách frontend ra khỏi backend.

**Decision**: `SelfServiceUnlockController` → **EXCLUDE** từ migration scope.
Lý do: User đang hướng tới tách frontend/backend. SSR controller sẽ bị remove trong tương lai.

---

## Approaches Considered

### Approach 1: Pure RequestScope Bean (no base class)

Chỉ tạo `RequestContext` bean + `RequestContextFilter`.
Controller inject `RequestContext` trực tiếp, KHÔNG có base controller.

```kotlin
@RestController
class SessionController(
    private val requestContext: RequestContext,
    private val loginSessionService: LoginSessionService,
    private val messageSource: MessageSource
) {
    @GetMapping
    fun getActiveSessions(): ResponseEntity<*> {
        val userId = requestContext.requireUserId()
        // ...
        val msg = messageSource.getMessage("key", null, requestContext.locale)
        return ResponseEntity.ok(mapOf("message" to msg))
    }
}
```

**Pros**: Đơn giản, ít abstraction, composition over inheritance
**Cons**: Vẫn lặp `messageSource.getMessage()`, `ResponseEntity.ok(mapOf(...))`,
không có response helpers → chỉ giải quyết 50% vấn đề

### Approach 2: Base Controller Hierarchy ⭐ SELECTED

`RequestContext` + `BaseController` hierarchy (đã specify trong technical spec).

```kotlin
@RestController
class SessionController(
    requestContext: RequestContext,
    messageSource: MessageSource,
    private val loginSessionService: LoginSessionService
) : AuthenticatedController(requestContext, messageSource) {

    @GetMapping
    fun getActiveSessions() = ok(loginSessionService.getActiveSessions(currentUserId()))

    @DeleteMapping("/{id}")
    fun revoke(@PathVariable id: Long): ResponseEntity<*> {
        val revoked = loginSessionService.revokeSession(id, currentUserId(), "MANUAL")
        return if (revoked) okMessage("auth.session_revoked") else ResponseEntity.notFound().build()
    }
}
```

**Pros**: Giải quyết triệt để — response helpers, userId, i18n, pagination
**Cons**: Inheritance-based, constructor params propagate

### Approach 3: Interface-based (Kotlin interface with default methods)

```kotlin
interface AuthenticatedEndpoint {
    val requestContext: RequestContext
    fun currentUserId(): Long = requestContext.requireUserId()
}
interface ResponseHelper {
    val messageSource: MessageSource
    val requestContext: RequestContext
    fun okMessage(key: String) = ResponseEntity.ok(mapOf("message" to msg(key)))
    fun msg(key: String) = messageSource.getMessage(key, null, requestContext.locale)
}
```

**Pros**: Multiple inheritance via interfaces, no class hierarchy constraint
**Cons**: Kotlin interface properties = abstract → vẫn cần declare in class.
Verbose hơn abstract class. Pattern ít quen thuộc hơn cho Spring devs.

---

## Selected Direction

**Approach 2: Base Controller Hierarchy** + **Approach B: Absorb ClientMetadataFilter**

Lý do tổng hợp:
1. Abstract class tự nhiên hơn interface cho controller base (Spring convention)
2. Constructor injection propagation không phải vấn đề với Kotlin (compact syntax)
3. Absorb ClientMetadataFilter → single filter quản lý ALL metadata
4. Response helpers (`ok()`, `okMessage()`, `pagedResponse()`) giảm boilerplate mạnh nhất
5. Convention DTO→Command via extension functions = Kotlin-native

**Final Architecture:**

```
   HTTP Request
       │
       ▼
┌──────────────────┐
│ SecurityFilter   │ → userId, jti, roles vào SecurityContext
└──────┬───────────┘
       ▼
┌──────────────────┐
│RequestContextFilt│ → Populate RequestContext bean
│                  │   + ALL MDC keys (correlationId,
│                  │     userId, ip, appVersion, platform)
└──────┬───────────┘
       ▼
┌──────────────────┐
│ DispatcherServlet│
└──────┬───────────┘
       ▼
┌──────────────────┐
│  Controller      │ extends AuthenticatedController / BaseController
│  ├ currentUserId │ → from RequestContext
│  ├ ok(body)      │ → from BaseController
│  ├ okMessage(key)│ → i18n response helper
│  └ pagedResponse │ → pagination helper
└──────┬───────────┘
       ▼
┌──────────────────┐
│request.toCommand │ → Extension function (DTO → Command)
│  (requestContext)│   enriches with ip, userAgent, correlation
└──────┬───────────┘
       ▼
┌──────────────────┐
│    Handler       │ → Pure business logic (no HTTP dependency)
└──────────────────┘
```

## Pre-classifications (preliminary)
- Feature type: EXTEND (infrastructure mới cho controllers đã tồn tại)
- Flow type: Command (refactoring, không có user-facing transaction flow)
- Affected modules:
  - `shared/web/` [NEW] — RequestContext, RequestContextFilter, BaseController, AuthenticatedController, AdminController
  - `auth/adapter/in/web/` [MODIFY] — 15 controllers (exclude SelfServiceUnlockController)
  - `auth/adapter/in/web/filter/ClientMetadataFilter.kt` [DELETE] — absorbed into RequestContextFilter
  - `auth/adapter/in/web/dto/DtoMappers.kt` [NEW] — extension functions
  - `rbac/adapter/in/web/RbacControllers.kt` [MODIFY] — 5 controllers
  - `pbac/adapter/in/web/PolicyController.kt` [MODIFY]

## Codebase Investigation Findings

### AuditLogService — IN SCOPE (user yêu cầu)
`shared/audit/AuditLogService.kt` (line 149-165):
- Có `getCurrentRequest()` dùng `RequestContextHolder` thủ công
- Có `getClientIp()` — bản thứ **4** của extractClientIp logic
- **User decision**: Đưa vào scope refactoring — cần push IP vào context cho DDoS detection + hacking trace
- **Impact**: AuditLogService được gọi từ **8 services** (~25 lần gọi):
  - `AccountLifecycleService` (3 calls)
  - `MfaService` (9 calls)
  - `MfaRateLimitService` (2 calls)
  - `AccountLockoutService` (4 calls)
  - `SsoAdapter` (4 calls)
  - `PasswordPolicyService` (1 call)
  - `RevokeSessionsHandler` (1 call)
  - `PasswordUpgradeService` (1 call)
- **Refactor plan**: Inject `RequestContext` vào AuditLogService → xóa `getCurrentRequest()` + `getClientIp()`
- **Risk**: LOW — chỉ đổi source IP/UserAgent, KHÔNG đổi behavior
- **Bonus**: Callers không cần thay đổi — AuditLogService tự lấy IP từ RequestContext

### LoginRateLimitFilter — giữ nguyên
`auth/adapter/in/web/filter/LoginRateLimitFilter.kt`:
- Cần `extractClientIp()` riêng vì chạy TRƯỚC SecurityFilter
- RequestContext chưa populated tại thời điểm filter này chạy
- **Decision**: Chấp nhận duplicate — đây là filter-level concern, không phải controller boilerplate

### ContentLanguageFilter — giữ nguyên
- Concern khác biệt (response header, không phải request metadata)
- Chạy SAU controller (response phase)
- Không overlap với RequestContextFilter

### Cookie management methods — giữ trong CqrsAuthController
`setRefreshTokenCookie()`, `clearRefreshTokenCookie()`, `extractRefreshTokenFromCookie()`:
- Chỉ dùng trong CqrsAuthController (login/refresh/logout)
- Không phải common pattern → KHÔNG cần di chuyển vào base

## Open Questions for Design Phase

- [RESOLVED] Q1: ClientMetadataFilter → absorb vào RequestContextFilter
- [RESOLVED] Q2: SelfServiceUnlockController → exclude, user sẽ tách frontend
- [RESOLVED] Q3: `AuditLogService` → **IN SCOPE** — user cần IP trong context cho DDoS/hacking trace. Inject RequestContext vào AuditLogService, xóa `getCurrentRequest()` + `getClientIp()`. 8 callers KHÔNG bị ảnh hưởng.
- [RESOLVED] Q4: `@ConditionalOnProperty` trên `CqrsAuthController` → KHÔNG ảnh hưởng. `BaseController` là abstract, không registered as bean.

## Open Questions for URD Analysis
- Không có (source = idea, không cần URD)
