# SRS: controller-request-context

_Type: EXTEND | Flow: Command | Date: 2026-09-21_

## 1. Scope

Chuẩn hóa Controller layer trong auth-service: tạo `RequestContext` (@RequestScope), `BaseController` hierarchy, DTO→Command convention, và consolidate filter MDC logic. Loại bỏ code lặp trên 17 controllers và 1 service class.

**In scope**: auth (16 controllers), rbac (5 controllers), pbac (1 controller), shared/audit (AuditLogService)
**Out of scope**: SelfServiceUnlockController (Thymeleaf SSR), LoginRateLimitFilter (pre-auth filter)

## 2. Functional Requirements

### FR-001: RequestContext Bean
- Hệ thống PHẢI cung cấp `@RequestScope` bean `RequestContext` trong package `shared.web`
- Fields: `userId: Long?`, `username: String?`, `jti: String?`, `roles: Set<String>`, `authenticated: Boolean`, `clientIp: String`, `userAgent: String?`, `deviceFingerprint: String?`, `correlationId: String`, `requestId: String`, `locale: Locale`, `anonymousSessionId: String?`, `anonymousTokenJti: String?`, `appVersion: String`, `clientPlatform: String`
- Helper methods: `requireUserId(): Long` (throw `InvalidCredentialsException` nếu null), `requireJti(): String`, `hasRole(role): Boolean`, `isAdmin(): Boolean`
- **Acceptance**: Bean thread-safe (1 instance/request), không null khi inject trong controller

### FR-002: RequestContextFilter
- Hệ thống PHẢI tự động populate `RequestContext` thông qua `RequestContextFilter` extends `OncePerRequestFilter`
- Filter order: `@Order(LOWEST_PRECEDENCE - 20)` — SAU SecurityFilterChain, TRƯỚC ContentLanguageFilter
- Data sources: `SecurityContextHolder.getContext().authentication` (userId, jti, roles) + `HttpServletRequest` (headers)
- Inject via `ObjectProvider<RequestContext>` — graceful handling khi context unavailable
- `shouldNotFilter`: Skip paths không bắt đầu bằng `/api/`
- **Acceptance**: RequestContext populated đầy đủ trước khi request đến controller

### FR-003: MDC Propagation
- `RequestContextFilter` PHẢI propagate vào MDC: `correlationId`, `userId`, `clientIp`, `appVersion`, `clientPlatform`
- MDC PHẢI cleanup trong `finally` block (prevent thread-pool leaks)
- MDC keys `appVersion`, `clientPlatform` absorbed từ `ClientMetadataFilter` (sẽ bị xóa)
- **Acceptance**: MDC keys available trong log output, cleanup after request

### FR-004: BaseController Response Helpers
- `BaseController` (abstract) PHẢI cung cấp: `ok(body)`, `ok(body, messageKey)`, `okMessage(messageKey, vararg args)`, `created(body)`, `noContent()`, `message(key, vararg args)`, `pagedResponse(page: Page<T>)`
- Constructor: `(requestContext: RequestContext, messageSource: MessageSource)` — protected visibility
- i18n PHẢI dùng `requestContext.locale` (KHÔNG `LocaleContextHolder.getLocale()`)
- **Acceptance**: Tất cả helpers return `ResponseEntity<*>`, i18n resolved đúng locale

### FR-005: AuthenticatedController
- `AuthenticatedController` extends `BaseController` PHẢI cung cấp: `currentUserId(): Long`, `currentJti(): String`, `currentRoles(): Set<String>`, `requireRole(role: String)`
- `currentUserId()` → `requestContext.requireUserId()` (throw `InvalidCredentialsException` nếu null)
- **Acceptance**: Behavior tương đương 7 bản `getCurrentUserId()` hiện tại

### FR-006: AdminController
- `AdminController` extends `AuthenticatedController` PHẢI cung cấp: `requireAdmin()`
- `requireAdmin()` check `ROLE_ADMIN` hoặc `ROLE_SUPER_ADMIN`, throw `AccessDeniedException` nếu không có
- **Acceptance**: Admin controllers dùng `AdminController` base

### FR-007: DTO→Command Convention
- Hệ thống PHẢI cung cấp Kotlin extension functions: `LoginRequestDto.toCommand(ctx, anonymousTokenJti?)`, `RegisterRequestDto.toCommand(ctx, anonymousTokenJti?)`, `SwitchDomainRequestDto.toCommand(ctx, userId)`
- Extension functions enrichment: `ctx.clientIp`, `ctx.userAgent`, `ctx.correlationId`, `ctx.deviceFingerprint`
- File: `DtoMappers.kt` trong `auth/adapter/in/web/dto/`
- **Acceptance**: Inline DTO→Command mapping trong CqrsAuthController replaced với `request.toCommand(requestContext)`

### FR-008: Correlation ID Extraction
- `RequestContextFilter` PHẢI extract correlation ID theo thứ tự: `X-Correlation-ID` → `X-Request-ID` → auto-generated UUID
- Nhất quán cho TẤT CẢ endpoints
- **Acceptance**: Không còn controller nào tự extract correlation ID

### FR-009: Client IP Extraction
- `RequestContextFilter` PHẢI extract client IP theo thứ tự: `X-Forwarded-For` (first value, trimmed) → `request.remoteAddr`
- Nhất quán cho TẤT CẢ endpoints + AuditLogService
- AuditLogService PHẢI inject `ObjectProvider<RequestContext>` → xóa `getCurrentRequest()` + `getClientIp()`
- **Acceptance**: Không còn controller/service nào duplicate `extractClientIp()` / `getClientIp()`

### FR-010: Backward Compatibility
- KHÔNG thay đổi API contract (request/response format) của bất kỳ endpoint nào
- Tất cả existing tests PHẢI pass sau migration
- Exception behavior preserved: same `InvalidCredentialsException` khi userId null
- **Acceptance**: Zero API breaking changes

### FR-011: Incremental Migration Support
- Migration PHẢI có thể thực hiện controller-by-controller — cũ và mới cùng tồn tại song song
- Mỗi phase PHẢI compilable và test-pass
- **Acceptance**: Partial migration state = valid application state

### FR-012: Test Support
- Hệ thống PHẢI cung cấp `TestRequestContextFactory` với factory methods: `authenticated(userId, roles)`, `anonymous()`, `admin(userId)`
- **Acceptance**: Unit test controller mà không cần full Spring context

## 3. Non-functional Requirements

| NFR | Requirement | Metric |
|-----|------------|--------|
| Performance | RequestContextFilter latency | < 2ms per request |
| Thread Safety | RequestContext isolation | Guaranteed by @RequestScope |
| Code Quality | Duplicate code reduction | ≥ 50% fewer duplicate methods |
| Maintainability | Single source for request metadata | 1 class (RequestContext) thay vì scattered across 17 controllers |

## 4. FR Traceability Matrix

| FR | pre_openspec | design.md | tasks.md | Status |
|----|-------------|-----------|----------|--------|
| FR-001 | ✓ | §2.1 RequestContext | Task 1 | Mapped |
| FR-002 | ✓ | §2.2 RequestContextFilter | Task 2 | Mapped |
| FR-003 | ✓ | §2.2 RequestContextFilter (MDC) | Task 2 | Mapped |
| FR-004 | ✓ | §2.3 BaseController | Task 3 | Mapped |
| FR-005 | ✓ | §2.3 AuthenticatedController | Task 4 | Mapped |
| FR-006 | ✓ | §2.3 AdminController | Task 4 | Mapped |
| FR-007 | ✓ | §2.6 DtoMappers | Tasks 21-22 | Mapped |
| FR-008 | ✓ | §2.2 RequestContextFilter | Task 2 | Mapped |
| FR-009 | ✓ | §2.2 + §2.5 | Tasks 2, 20 | Mapped |
| FR-010 | ✓ | §2.4 Migration Pattern | Task 23 | Mapped |
| FR-011 | ✓ | §2.4 Migration Pattern | All phases | Mapped |
| FR-012 | ✓ | N/A | Task 26 | Mapped |
