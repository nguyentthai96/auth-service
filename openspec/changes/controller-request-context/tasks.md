<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "EXTEND", transaction_flow: "N/A" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Tasks: controller-request-context

_Type: EXTEND | Flow: Command | Date: 2026-09-21_
_Total: 26 tasks | 4 phases_

---

## Phase 1: Infrastructure (shared/web/) — 5 tasks

- [ ] **Task 1: Tạo RequestContext bean**
  - File: `src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt` | Action: [NEW]
  - FR: FR-001 — @RequestScope bean chứa userId, jti, roles, clientIp, userAgent, deviceFingerprint, correlationId, requestId, locale, anonymousSessionId, anonymousTokenJti, appVersion, clientPlatform
  - Pattern: Mutable `var` fields, default values, helper methods: `requireUserId()`, `requireJti()`, `hasRole()`, `isAdmin()`
  - Dependencies: `InvalidCredentialsException` from `shared.exception`

- [ ] **Task 2: Tạo RequestContextFilter**
  - File: `src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt` | Action: [NEW]
  - FR: FR-002 (populate), FR-003 (MDC), FR-008 (correlationId), FR-009 (clientIp)
  - Base: `OncePerRequestFilter` from Spring
  - Pattern: `@Order(LOWEST_PRECEDENCE - 20)`, `ObjectProvider<RequestContext>`, `shouldNotFilter` = skip non `/api/` paths
  - MDC keys: correlationId, userId, clientIp, appVersion, clientPlatform
  - Dependencies: `RequestContext` (Task 1), `SecurityContextHolder`, `MDC`
  - Source: Absorb logic from `ClientMetadataFilter` (Task 5 DELETE)

- [ ] **Task 3: Tạo BaseController**
  - File: `src/main/kotlin/com/ntt/authservice/shared/web/BaseController.kt` | Action: [NEW]
  - FR: FR-004 — Abstract controller with response helpers
  - Pattern: Constructor injection `(requestContext: RequestContext, messageSource: MessageSource)`, `protected` visibility
  - Methods: `ok(body)`, `ok(body, messageKey)`, `okMessage(messageKey, vararg args)`, `created(body)`, `noContent()`, `message(key, vararg args)`, `pagedResponse(page: Page<T>)`
  - Dependencies: `RequestContext` (Task 1), `MessageSource`, `ResponseEntity`, `Page` (Spring Data)

- [ ] **Task 4: Tạo AuthenticatedController + AdminController**
  - File: `src/main/kotlin/com/ntt/authservice/shared/web/AuthenticatedController.kt` | Action: [NEW]
  - File: `src/main/kotlin/com/ntt/authservice/shared/web/AdminController.kt` | Action: [NEW]
  - FR: FR-005 (AuthenticatedController), FR-006 (AdminController)
  - Base: `BaseController` (Task 3)
  - AuthenticatedController methods: `currentUserId(): Long`, `currentJti(): String`, `currentRoles(): Set<String>`, `requireRole(role: String)`
  - AdminController methods: `requireAdmin()` — check ROLE_ADMIN or ROLE_SUPER_ADMIN
  - Dependencies: `BaseController` (Task 3), `AccessDeniedException`

- [ ] **Task 5: Xóa ClientMetadataFilter**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ClientMetadataFilter.kt` | Action: [DELETE]
  - FR: FR-002, FR-003 — Logic absorbed into RequestContextFilter (Task 2)
  - Source: `ClientMetadataFilter.kt` — MDC keys `appVersion`, `clientPlatform` moved to RequestContextFilter

---

## Phase 2: Auth Controllers Migration — 11 tasks

- [ ] **Task 6: Migrate SessionController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SessionController.kt` | Action: [MODIFY]
  - Base: `AuthenticatedController` from `shared.web`
  - FR: FR-005, FR-010 — Replace `getCurrentUserId()` → `currentUserId()`, replace `messageSource.getMessage(...)` → `okMessage(key)`
  - Pattern: Remove private `getCurrentUserId()`, add constructor params `(requestContext, messageSource, ...)`
  - Source: Remove duplicate `getCurrentUserId()` (line 63-66)

- [ ] **Task 7: Migrate DeviceController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/DeviceController.kt` | Action: [MODIFY]
  - Base: `AuthenticatedController`
  - FR: FR-005, FR-010 — Replace `getCurrentUserId()` → `currentUserId()`
  - Source: Remove duplicate `getCurrentUserId()` (line 68)

- [ ] **Task 8: Migrate MfaController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt` | Action: [MODIFY]
  - Base: `AuthenticatedController`
  - FR: FR-005, FR-010 — Replace `getCurrentUserId()`, replace 2× `messageSource.getMessage()` → `message(key)` or `okMessage(key)`
  - Source: Remove duplicate `getCurrentUserId()` (line 131-134), remove `LocaleContextHolder.getLocale()` calls

- [ ] **Task 9: Migrate SsoController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/SsoController.kt` | Action: [MODIFY]
  - Base: `AuthenticatedController`
  - FR: FR-005, FR-010 — Replace `getCurrentUserId()`, replace 2× `messageSource.getMessage()`
  - Source: Remove duplicate `getCurrentUserId()` (line 64)

- [ ] **Task 10: Migrate AccountLifecycleController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AccountLifecycleController.kt` | Action: [MODIFY]
  - Base: `AuthenticatedController`
  - FR: FR-005, FR-010 — Replace `getCurrentUserId()`, replace 3× `messageSource.getMessage()`
  - Source: Remove duplicate `getCurrentUserId()` (line 113)

- [ ] **Task 11: Migrate TokenController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt` | Action: [MODIFY]
  - Base: `AuthenticatedController`
  - FR: FR-005, FR-010 — Replace i18n pattern
  - Source: Replace `messageSource.getMessage()` (line 111)

- [ ] **Task 12: Migrate AdminUnlockController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminUnlockController.kt` | Action: [MODIFY]
  - Base: `AdminController`
  - FR: FR-006, FR-010 — Replace `getCurrentUserId()` (nullable variant → use `requestContext.userId`)

- [ ] **Task 13: Migrate AdminSessionController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AdminSessionController.kt` | Action: [MODIFY]
  - Base: `AdminController`
  - FR: FR-006, FR-010 — Replace `messageSource.getMessage()`

- [ ] **Task 14: Migrate RateLimitAdminController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt` | Action: [MODIFY]
  - Base: `AdminController`
  - FR: FR-006, FR-010 — Replace `messageSource.getMessage()`

- [ ] **Task 15: Migrate CqrsAuthController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - Base: `BaseController` (has both public + auth endpoints)
  - FR: FR-004, FR-005, FR-007, FR-008, FR-009, FR-010
  - Pattern: Largest controller — replace `getCurrentUserId()`, `extractClientIp()`, 5× `messageSource.getMessage()`, replace inline `httpRequest.getHeader(...)` with `requestContext.*`
  - Dependencies: Keep `setRefreshTokenCookie()`, `clearRefreshTokenCookie()`, `extractAnonymousTokenJti()` — feature-specific
  - Source: Remove `getCurrentUserId()` (line 263), `extractClientIp()` (line 269)

- [ ] **Task 16: Migrate AnonymousAuthController**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AnonymousAuthController.kt` | Action: [MODIFY]
  - Base: `BaseController`
  - FR: FR-009, FR-010 — Replace `extractClientIp()` → `requestContext.clientIp`
  - Source: Remove `extractClientIp()` (line 169)

---

## Phase 3: RBAC + PBAC + Service Layer — 4 tasks

- [ ] **Task 17: Migrate CaptchaController + InternalApiController + KeyExchangeController + EventStoreController**
  - Files: 4 controllers in `auth/adapter/in/web/` | Action: [MODIFY]
  - Base: `BaseController` (minimal change — add constructor, extend base)
  - FR: FR-004, FR-011

- [ ] **Task 18: Migrate RbacControllers.kt**
  - File: `src/main/kotlin/com/ntt/authservice/rbac/adapter/in/web/RbacControllers.kt` | Action: [MODIFY]
  - Base: `BaseController` for all 5 controllers (DomainController, RoleController, GroupController, ResourceController, PermissionCheckController)
  - FR: FR-004, FR-011
  - Pattern: Add `(requestContext, messageSource)` constructor params, extend BaseController

- [ ] **Task 19: Migrate PolicyController**
  - File: `src/main/kotlin/com/ntt/authservice/pbac/adapter/in/web/PolicyController.kt` | Action: [MODIFY]
  - Base: `BaseController`
  - FR: FR-004, FR-011

- [ ] **Task 20: Migrate AuditLogService**
  - File: `src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt` | Action: [MODIFY]
  - FR: FR-009 — Inject `ObjectProvider<RequestContext>`, replace `getCurrentRequest()` + `getClientIp()`
  - Pattern: `ObjectProvider<RequestContext>` (not direct inject) — graceful fallback for non-web contexts (Kafka, scheduler)
  - Dependencies: `RequestContext` (Task 1)
  - Source: Delete `getCurrentRequest()` (line 149-155), delete `getClientIp()` (line 158-165)

---

## Phase 4: DTO Mappers + Cleanup — 6 tasks

- [ ] **Task 21: Tạo DtoMappers.kt**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/DtoMappers.kt` | Action: [NEW]
  - FR: FR-007 — Extension functions: `LoginRequestDto.toCommand(ctx)`, `RegisterRequestDto.toCommand(ctx)`, `SwitchDomainRequestDto.toCommand(ctx, userId)`
  - Pattern: Kotlin extension functions on DTO classes, enriching with `RequestContext` data (ip, userAgent, correlationId)

- [ ] **Task 22: Refactor CqrsAuthController dùng DtoMappers**
  - File: `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt` | Action: [MODIFY]
  - FR: FR-007 — Replace inline DTO→Command mapping with `request.toCommand(requestContext)`
  - Dependencies: DtoMappers.kt (Task 21)

- [ ] **Task 23: Verify tất cả tests pass**
  - Action: Run `./gradlew test`
  - FR: FR-010, FR-011 — Backward compatibility verification

- [ ] **Task 24: Cleanup unused imports**
  - Files: All modified controllers | Action: [MODIFY]
  - Pattern: Remove `SecurityContextHolder`, `LocaleContextHolder`, `HttpServletRequest` imports where no longer used

- [ ] **Task 25: Tạo SecurityFilterChain config update**
  - File: `src/main/kotlin/com/ntt/authservice/shared/security/SecurityConfig.kt` | Action: [MODIFY]
  - Pattern: Verify `RequestContextFilter` auto-registered via `@Component` + `@Order`, no manual registration needed
  - Dependencies: Verify `ClientMetadataFilter` removal doesn't break SecurityFilterChain config

- [ ] **Task 26: Tạo TestRequestContextFactory (test support)**
  - File: `src/test/kotlin/com/ntt/authservice/shared/web/TestRequestContextFactory.kt` | Action: [NEW]
  - FR: FR-012 — Factory methods to create mock RequestContext for unit tests
  - Pattern: `fun authenticated(userId: Long, roles: Set<String>): RequestContext`, `fun anonymous(): RequestContext`, `fun admin(userId: Long): RequestContext`
