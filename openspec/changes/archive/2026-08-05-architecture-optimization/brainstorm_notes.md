---
type: brainstorm_notes
change: architecture-optimization
date: 2026-08-05
selected_direction: "Approach 1 — Pragmatic Hybrid (Bridge Pattern)"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Architecture Optimization

## Date
2026-08-05

## Context

Sau khi hoàn thành research phase (8 documents) và `/wf_pre_openspec` (25 FRs), cần deep thinking trước khi apply. Research cho thấy auth-service chỉ dùng 15% base-core capabilities, có 7+ architectural violations. Tuy nhiên, **3 design decisions quan trọng** cần phân tích sâu hơn trước khi implement.

---

## Critical Design Decisions Analyzed

### Decision 1: AuthException → BusinessException Migration Strategy

#### Phát hiện quan trọng khi đọc source code

```
                ┌─────────────────────────────┐
                │  auth-service (CURRENT)      │
                │                              │
                │  AuthException               │
                │    ├── errorCode: String      │
                │    ├── message: String        │
                │    ├── httpStatus: HttpStatus  │
                │    └── extends RuntimeException│
                │                              │
                │  GlobalExceptionHandler      │
                │    └── returns ProblemDetail  │ ← RFC 7807
                │        (standard HTTP)       │
                └──────────┬──────────────────┘
                           │ ❓ Bridge how?
                ┌──────────▼──────────────────┐
                │  base-core                   │
                │                              │
                │  BusinessException           │
                │    ├── err: ErrorCodeBase     │
                │    │   ├── code: String       │
                │    │   ├── msgCode: String    │
                │    │   └── description: String│
                │    └── extends RuntimeException│
                │                              │
                │  BaseControllerAdvice        │
                │    └── returns ApiResponse   │ ← Custom wrapper
                │        (status: 422 always)  │
                └──────────────────────────────┘
```

**Vấn đề phát hiện**: `BaseControllerAdvice.handleBusinessException()` LUÔN trả về `HttpStatus.UNPROCESSABLE_ENTITY` (422) cho tất cả `BusinessException`. Auth-service hiện map exception → HTTP status linh hoạt (401, 403, 404, 409). Nếu migrate thẳng → sai HTTP status!

**Response format cũng khác**:
- auth-service: `ProblemDetail` (RFC 7807 standard) ← **tốt hơn**
- base-core: `ApiResponse<Unit>` (custom wrapper) ← **cũ hơn**

#### 3 Approaches Analyzed

**Approach A — Full base-core adoption** (REJECTED ❌)
```
AuthException → BusinessException(ErrorCodeBase)
GlobalExceptionHandler → extend BaseControllerAdvice
Response format: ApiResponse<Unit> (base-core default)
```
- ❌ Mất HTTP status granularity (all → 422)
- ❌ Downgrade từ ProblemDetail → ApiResponse
- ❌ Breaking change cho tất cả API consumers

**Approach B — Bridge pattern** (SELECTED ✅)
```
                    ┌─────────────────────┐
                    │  RuntimeException    │
                    └────────┬────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
    ┌─────────▼──────┐  ┌───▼──────────┐  ┌▼────────────────┐
    │BusinessException│  │AuthException │  │NotFoundException │
    │  (base-core)    │  │(auth-service)│  │  (base-core)     │
    └────────────────┘  │ extends      │  └─────────────────┘
                        │ BusinessEx.  │
                        └──────────────┘
```

Bridge implementation:
```kotlin
// Step 1: Create auth ErrorCode enum implementing ErrorCodeBase
enum class AuthErrorCode(
    private val code: String,
    private val msgCode: String,
    private val desc: String,
    val httpStatus: HttpStatus  // ← KEEP HTTP status
) : ErrorCodeBase(code, msgCode, desc) {
    INVALID_CREDENTIALS("AUTH_001", "auth.invalid_credentials", "Invalid credentials", UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH_002", "auth.account_locked", "Account locked", FORBIDDEN),
    TOKEN_EXPIRED("AUTH_003", "auth.token_expired", "Token expired", UNAUTHORIZED),
    // ...
}

// Step 2: AuthException extends BusinessException (bridge)
open class AuthException(
    val authError: AuthErrorCode,
    override val message: String = authError.getDesc()!!
) : BusinessException(authError) {
    // Keep backward compatibility
    val errorCode: String get() = authError.getCode()
    val httpStatus: HttpStatus get() = authError.httpStatus
}

// Step 3: AuthControllerAdvice extends BaseControllerAdvice + override
@ControllerAdvice
class AuthControllerAdvice(validator: LocalValidatorFactoryBean) 
    : BaseControllerAdvice(validator) {
    
    // Override to use ProblemDetail + correct HTTP status
    @ExceptionHandler(AuthException::class)
    fun handleAuthException(ex: AuthException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            ex.httpStatus, ex.message
        ).apply {
            setProperty("errorCode", ex.errorCode)
        }
    }
}
```
- ✅ base-core compatibility (`BusinessException` hierarchy)
- ✅ Keep ProblemDetail (RFC 7807)
- ✅ Keep HTTP status granularity
- ✅ Backward compatible API response format
- ⚠️ Slightly more complex (bridge layer)

**Approach C — Enhance base-core** (DEFERRED ⏸️)
```
Modify BaseControllerAdvice to support HTTP status from exception
Add ProblemDetail support to ApiResponse
```
- ✅ Clean long-term solution
- ❌ Changes base-core → affects ALL services
- ❌ Scope creep — separate change request
- ⏸️ Can do AFTER auth-service migration is stable

**Decision: Approach B — Bridge Pattern**

### Decision 2: CQRS Handler Granularity

#### AuthService Method Analysis (307 lines actual, 11 deps)

```
AuthService Methods → Handler Mapping:

┌─────────────────────────────────────────────────────────────┐
│  COMMANDS (write operations)                                │
│                                                             │
│  register()          → RegisterUserHandler          ~40L    │
│  login()             → LoginHandler                 ~55L ⚠️ │
│  refreshToken()      → RefreshTokenHandler          ~25L    │
│  switchDomain()      → SwitchDomainHandler          ~15L    │
│  revokeAllSessions() → RevokeSessionsHandler        ~10L    │
│                                                             │
│  QUERIES (read operations)                                  │
│                                                             │
│  buildAuthResponseForUser() → BuildAuthResponseHandler ~10L │
│                                                             │
│  PRIVATE (shared logic)                                     │
│                                                             │
│  generateAuthResponse()  → AuthResponseBuilder (domain svc) │
│  handleFailedLogin()     → LoginHandler (internal)          │
│  getPrimaryDomain()      → DomainLookupService (domain svc) │
│  hashToken()             → TokenHasher (domain svc/utility) │
└─────────────────────────────────────────────────────────────┘
```

#### Concern: LoginHandler quá lớn (~55 lines)

`login()` hiện có quá nhiều concerns:
1. Validate credentials
2. Check account lock status
3. CAPTCHA verification
4. Password verification
5. Handle failed login
6. MFA checkpoint
7. Domain resolution
8. Generate auth response

**2 Sub-approaches:**

**Sub-approach 2A — Single LoginHandler (simplicity)**
```
LoginHandler handles ALL login logic (~55 lines)
  inject: UserPort, CaptchaPort, MfaPort, TokenPort
  dependencies: 4 ← OK (threshold ≤ 4)
```
- ✅ Simple, 1 command → 1 handler
- ✅ Under 4 deps
- ⚠️ Slightly over 50-line target

**Sub-approach 2B — Decomposed LoginPipeline**
```
LoginHandler orchestrates steps:
  1. CredentialValidator.validate(username, password) → User
  2. LockChecker.check(user)
  3. CaptchaChecker.check(request, user)
  4. MfaChecker.check(user, request) → LoginResult
  5. AuthResponseBuilder.build(user, domain)
```
- ✅ Each step ≤ 20 lines
- ❌ Over-engineering for current complexity
- ❌ More classes to maintain

**Decision: Sub-approach 2A — Single LoginHandler**
Vì: LoginHandler inject 4 ports (dưới threshold), tổng ~55 lines gần sát 50 line target. Nếu tương lai login logic phức tạp hơn → tách lúc đó.

#### RbacEngine Method Analysis (90 lines)

```
RbacEngine Methods → Handler Mapping:

┌─────────────────────────────────────────────────────────────┐
│  QUERIES                                                    │
│                                                             │
│  hasPermission()           → CheckPermissionHandler   ~15L  │
│  getUserRoles()            → GetUserRolesHandler       ~10L │
│  getEffectivePermissions() → GetPermissionsHandler     ~20L │
│                                                             │
│  SHARED LOGIC                                               │
│                                                             │
│  resolveUserRoleIds()      → RbacResolver (domain svc) ~10L│
│  resolveUserPermissions()  → RbacResolver (shared)     ~10L│
└─────────────────────────────────────────────────────────────┘
```

**Decision**: 3 QueryHandlers + 1 shared `RbacResolver` domain service.

`RbacResolver` giữ `resolveUserRoleIds()` + `resolveUserPermissions()` — shared giữa handlers.

#### Complete Handler Inventory

| # | Handler | Type | Source | Lines (est) | Deps |
|---|---------|------|--------|:-----------:|:----:|
| 1 | `RegisterUserHandler` | Command | AuthService.register() | ~40 | 4 |
| 2 | `LoginHandler` | Command | AuthService.login() | ~55 | 4 |
| 3 | `RefreshTokenHandler` | Command | AuthService.refreshToken() | ~25 | 3 |
| 4 | `SwitchDomainHandler` | Command | AuthService.switchDomain() | ~15 | 3 |
| 5 | `RevokeSessionsHandler` | Command | AuthService.revokeAllSessions() | ~10 | 2 |
| 6 | `CheckPermissionHandler` | Query | RbacEngine.hasPermission() | ~15 | 2 |
| 7 | `GetUserRolesHandler` | Query | RbacEngine.getUserRoles() | ~10 | 2 |
| 8 | `GetPermissionsHandler` | Query | RbacEngine.getEffectivePermissions() | ~20 | 2 |
| 9 | `BuildAuthResponseHandler` | Query | AuthService.buildAuthResponseForUser() | ~15 | 3 |
| **Total** | **5 Commands + 4 Queries** | | | | |

Supporting domain services:
- `AuthResponseBuilder` — builds AuthResponse from domain models (extracted from private method)
- `RbacResolver` — resolves user → groups → roles → permissions chain
- `TokenHasher` — SHA-256 hashing utility

### Decision 3: Migration Order & Safety Strategy

#### Phase Dependency Graph

```
Phase 1 ─────────┐
(Domain Layer)    │
  │               │ MUST complete first
  ├─ domain/      │ (all other phases depend on domain models)
  ├─ ports/       │
  └─ mappers/     │
                  │
Phase 2 ──────────┤
(CQRS Handlers)   │
  │               │ DEPENDS on Phase 1 (uses domain models + ports)
  ├─ handlers/    │
  ├─ commands/    │
  └─ controller   │
     rewire       │
                  │
Phase 3 ──────────┤──────── Can partially parallel with Phase 2
(Communication)   │
  ├─ grpc-proto   │ (independent)
  ├─ gRPC server  │ DEPENDS on Phase 2 (dispatches via QueryBus)
  └─ @HttpExchange│ (independent of Phase 2)
                  │
Phase 4 ──────────┤──────── DEPENDS on Phase 2 (handlers exist)
(Performance)     │
  ├─ N+1 fix      │ (independent — can do in Phase 2)
  ├─ Caffeine L1  │ (new adapter)
  └─ Redis L2     │ (new adapter)
                  │
Phase 5 ──────────┘
(Testing)
  └─ All phases must be stable
```

#### Safety Strategy: Feature Flag for CQRS

```
                    ┌──────────────────┐
                    │  Controller      │
                    └────┬─────────────┘
                         │
              ┌──────────▼──────────┐
              │  Feature Flag       │
              │  cqrs.enabled=true  │
              └──┬──────────┬───────┘
                 │          │
         ┌───────▼───┐ ┌───▼────────┐
         │ CommandBus │ │ AuthService│
         │ (NEW)     │ │ (OLD)      │
         └───────────┘ └────────────┘
```

**Decision**: Use `@ConditionalOnProperty("app.cqrs.enabled")` for gradual rollout:
1. Phase 2a: Create handlers but keep old services
2. Phase 2b: Wire controllers to CommandBus via feature flag
3. Phase 2c: Test with flag ON in staging
4. Phase 2d: Remove old services + flag (cleanup)

#### Blast Radius per Phase

| Phase | Files Changed | Files Added | Risk | Rollback Complexity |
|:-----:|:------------:|:----------:|:----:|:-------------------:|
| 1 | 0 (no existing code changed!) | ~20 | 🟢 Low | Delete new files |
| 2 | 5 controllers + build.gradle | ~15 | 🟡 Medium | Feature flag OFF |
| 3 | build.gradle + application.yml | ~8 | 🟢 Low | Remove deps |
| 4 | 0 (new adapters only) | ~5 | 🟢 Low | Delete new files |
| 5 | 0 (test files only) | ~8 | 🟢 None | N/A |

**Critical insight**: Phase 1 has ZERO blast radius — we only ADD new files, never modify existing code. This is the safest starting point.

---

## Questions Asked & Answers

- Q1: base-core `BaseControllerAdvice` dùng `ApiResponse`, auth-service dùng `ProblemDetail` — nên chọn cái nào?
  → A: Keep `ProblemDetail` (RFC 7807) — it's the standard. Bridge via `AuthControllerAdvice extends BaseControllerAdvice` + override `handleAuthException()`.

- Q2: LoginHandler 55 lines — nên decompose không?
  → A: Không. 55 lines gần sát 50-line target, 4 deps dưới threshold. Decompose khi complexity tăng.

- Q3: Phase 1 có risk breaking gì không?
  → A: Không. Phase 1 chỉ ADD files (domain models, ports, mappers). Không modify existing code. Zero blast radius.

- Q4: `BusinessException(err: ErrorCodeBase)` vs `AuthException(errorCode: String, httpStatus)` — bridge thế nào?
  → A: Tạo `AuthErrorCode` enum implements `ErrorCodeBase` + giữ `httpStatus` property. `AuthException` extends `BusinessException` + expose `httpStatus`.

- Q5: N+1 trong `RbacEngine.getEffectivePermissions()` — fix ở phase nào?
  → A: Fix ngay trong Phase 2 khi tạo `GetPermissionsHandler` — thay N loop queries bằng 1 batch JOIN query trong outbound port implementation.

- Q6: `@Version` thêm ở đâu? `AuditableEntity` hay class riêng?
  → A: Class riêng `VersionedAuditableEntity` (user feedback xác nhận). Chỉ UserEntity, DomainEntity kế thừa.

---

## Approaches Considered

### Approach 1: Pragmatic Hybrid (Bridge Pattern) ✅ SELECTED

**Strategy**: Bridge auth-service exceptions vào base-core hierarchy MÀ giữ lại ProblemDetail + HTTP status granularity. Phase 1 zero-blast-radius. Feature flags cho CQRS rollout.

**Pros**:
- Backward compatible — API consumers không bị ảnh hưởng
- base-core compatible — `BusinessException` hierarchy intact
- Safe rollout — feature flag + zero-blast Phase 1
- Incremental — mỗi phase independent, có thể pause

**Cons**:
- Bridge layer adds thin complexity
- Two response formats co-exist temporarily (ProblemDetail in auth, ApiResponse in base-core)

### Approach 2: Full base-core Adoption (Pure)

**Strategy**: Migrate hoàn toàn sang base-core patterns — `ApiResponse`, `ErrorCodeBase`, `BaseControllerAdvice` without override.

**Pros**:
- 100% base-core consistency
- No bridge layer

**Cons**:
- ❌ Mất HTTP status granularity (all BusinessException → 422)
- ❌ Breaking API change (ProblemDetail → ApiResponse)
- ❌ Requires API version bump
- ❌ All consumers need update

### Approach 3: Enhance base-core First

**Strategy**: Upgrade base-core `BaseControllerAdvice` để support HTTP status từ exception + ProblemDetail support. Then adopt.

**Pros**:
- Clean long-term solution
- Benefits ALL services

**Cons**:
- ❌ Scope creep — separate task
- ❌ Delays auth-service migration
- ❌ Requires base-core release + all services update

---

## Selected Direction

**Approach 1 — Pragmatic Hybrid (Bridge Pattern)**

Reasoning:
1. **Zero breaking changes** — API consumers không cần update
2. **base-core compatible** — nếu sau này base-core upgrade ProblemDetail → chỉ cần remove bridge
3. **Safest rollout** — Phase 1 zero blast radius, Phase 2 feature-flagged
4. **Aligned with user preference** — "tận dụng base-core code đã có, tối ưu lên"

---

## Pre-classifications (preliminary)
- Feature type: EXTEND
- Flow type: Command (architecture refactoring)
- Affected modules:
  - `auth-service` (primary — all 4 bounded contexts)
  - `base-core/base-model` (minor — add `VersionedAuditableEntity`)
  - `services/shared/` (new — grpc-proto, domain-common, security-common)

---

## Codebase Investigation Findings

### Key Source Code Evidence

| File | Lines | Issue | Phase |
|------|:-----:|-------|:-----:|
| [AuthService.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt) | 307 | God-class, 11 deps, co-located DTOs, inline RestTemplate NOT present (SsoAdapter has it) | P2 |
| [RbacEngine.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/rbac/application/RbacEngine.kt) | 90 | N+1 queries (line 66-71), 7 repository deps | P2 |
| [SsoAdapter.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt) | — | Inline `RestTemplate()` (line 29) | P3 |
| [CaptchaVerifier.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt) | — | Injected `RestTemplate` (line 22) | P3 |
| [AuthExceptions.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt) | 78 | Extends `RuntimeException` NOT `BusinessException` | P1 |
| [BusinessException.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/exception/BusinessException.kt) | 14 | Takes `ErrorCodeBase`, extends `RuntimeException` | — |
| [BaseControllerAdvice.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt) | 119 | Returns `ApiResponse<Unit>` (not ProblemDetail), BusinessException → 422 always | — |

### CQRS Infrastructure (Ready to Use)

| File | What it provides |
|------|-----------------|
| [CommandHandler.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/command/CommandHandler.kt) | `interface CommandHandler<C : Command<R>, R>` — handle + commandType |
| [Command.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/command/Command.kt) | `interface Command<R>` — marker interface |
| [CommandBus.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/command/CommandBus.kt) | `interface CommandBus` — dispatch(command) |
| [SpringCommandBus.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/SpringCommandBus.kt) | Auto-discovers handlers via Spring List injection |
| [QueryHandler.kt](file:///Users/nguyenthanhthai/Desktop/workspace_research/boilerplate/components/eventsourcing-utils/src/main/kotlin/com/ntt/eventsourcingutils/lib/cqrs/query/QueryHandler.kt) | `interface QueryHandler<Q : Query<R>, R>` — handle + queryType |

---

## Architecture Diagrams

### Target Package Structure

```
auth-service/src/main/kotlin/com/ntt/authservice/
├── AuthServiceApplication.kt
│
├── auth/                              ← Authentication bounded context
│   ├── domain/                        ← 🆕 PURE KOTLIN (Phase 1)
│   │   ├── model/
│   │   │   ├── User.kt               (domain model)
│   │   │   ├── AuthToken.kt          (domain model)
│   │   │   ├── UserStatus.kt         (sealed interface)
│   │   │   └── vo/
│   │   │       ├── UserId.kt         (value object)
│   │   │       └── Email.kt          (value object)
│   │   └── service/
│   │       ├── AuthResponseBuilder.kt (domain service)
│   │       ├── TokenHasher.kt        (utility)
│   │       └── DomainLookupService.kt(domain service)
│   │
│   ├── application/                   ← 🔄 REFACTORED (Phase 2)
│   │   ├── port/
│   │   │   ├── in/                    ← Commands/Queries
│   │   │   │   ├── RegisterUserCommand.kt
│   │   │   │   ├── LoginCommand.kt
│   │   │   │   ├── RefreshTokenCommand.kt
│   │   │   │   ├── SwitchDomainCommand.kt
│   │   │   │   └── RevokeSessionsCommand.kt
│   │   │   └── out/                   ← Outbound ports
│   │   │       ├── UserRepository.kt  (interface)
│   │   │       ├── TokenStore.kt      (interface)
│   │   │       ├── SsoGateway.kt      (interface)
│   │   │       └── EventPublisher.kt  (interface)
│   │   └── handler/
│   │       ├── RegisterUserHandler.kt
│   │       ├── LoginHandler.kt
│   │       ├── RefreshTokenHandler.kt
│   │       ├── SwitchDomainHandler.kt
│   │       ├── RevokeSessionsHandler.kt
│   │       └── BuildAuthResponseHandler.kt
│   │
│   └── adapter/
│       ├── in/
│       │   ├── web/
│       │   │   ├── AuthController.kt  ← rewired to CommandBus
│       │   │   ├── MfaController.kt
│       │   │   ├── SsoController.kt
│       │   │   └── TokenController.kt
│       │   └── grpc/                  ← 🆕 (Phase 3)
│       │       └── PermissionGrpcService.kt
│       └── out/
│           ├── persistence/
│           │   ├── entity/            (existing — unchanged)
│           │   ├── repository/        (existing — unchanged)
│           │   ├── mapper/            ← 🆕 MapStruct (Phase 1)
│           │   │   └── UserEntityMapper.kt
│           │   └── adapter/           ← 🆕 Port implementations
│           │       └── JpaUserRepository.kt
│           ├── cache/                 ← 🆕 (Phase 4)
│           │   ├── CaffeinePermissionCache.kt
│           │   └── RedisPermissionCache.kt
│           └── http/                  ← 🆕 (Phase 3)
│               ├── SsoProviderClient.kt    (@HttpExchange)
│               └── CaptchaClient.kt        (@HttpExchange)
│
├── rbac/                              ← RBAC bounded context
│   ├── domain/                        ← 🆕
│   │   └── service/
│   │       └── RbacResolver.kt        (shared logic)
│   ├── application/
│   │   ├── port/in/
│   │   │   ├── CheckPermissionQuery.kt
│   │   │   ├── GetUserRolesQuery.kt
│   │   │   └── GetPermissionsQuery.kt
│   │   └── handler/
│   │       ├── CheckPermissionHandler.kt
│   │       ├── GetUserRolesHandler.kt
│   │       └── GetPermissionsHandler.kt
│   └── adapter/                       (existing — unchanged)
│
├── pbac/                              ← PBAC bounded context (Phase 2 later)
│
└── shared/
    ├── exception/
    │   ├── AuthErrorCode.kt           ← 🆕 implements ErrorCodeBase
    │   ├── AuthException.kt           ← 🔄 extends BusinessException
    │   ├── AuthCoreExceptions.kt      ← 🔄 use AuthErrorCode
    │   └── AuthControllerAdvice.kt    ← 🔄 extends BaseControllerAdvice
    └── ...
```

### Data Flow — Login Command (After Refactoring)

```
┌──────────┐    POST /auth/login    ┌──────────────┐
│  Client  │───────────────────────▶│AuthController │
└──────────┘                        └───────┬───────┘
                                            │
                              DTO → LoginCommand(username, password, ...)
                                            │
                                    ┌───────▼───────┐
                                    │  CommandBus   │
                                    │(SpringCmdBus) │
                                    └───────┬───────┘
                                            │ dispatch
                                    ┌───────▼───────┐
                                    │ LoginHandler  │ ← implements CommandHandler
                                    │               │
                                    │ inject:       │
                                    │  UserPort     │ (outbound port)
                                    │  CaptchaPort  │ (outbound port)
                                    │  MfaPort      │ (outbound port)
                                    │  TokenPort    │ (outbound port)
                                    └───────┬───────┘
                                            │
                      ┌─────────────────────┤
                      │                     │
              ┌───────▼───────┐     ┌───────▼───────┐
              │ User (domain) │     │  AuthToken    │
              │ Pure Kotlin   │     │  (domain)     │
              └───────────────┘     └───────────────┘
```

---

## Open Questions for Design Phase

- [RESOLVED] Exception bridge strategy → Approach B (Bridge Pattern)
- [RESOLVED] CQRS handler count → 5 Commands + 4 Queries = 9 handlers
- [RESOLVED] LoginHandler size → Keep single (~55 lines, 4 deps)
- [RESOLVED] Migration order → Phase 1 first (zero blast radius)
- [RESOLVED] N+1 fix timing → Phase 2 with GetPermissionsHandler
- [RESOLVED] @Version placement → VersionedAuditableEntity (opt-in)

## Open Questions for Implementation Phase

- [OPEN] `CqrsAutoConfiguration` trong `base-cqrs-starter` — cần verify auto-config scan path có đúng với auth-service package không?
- [OPEN] MapStruct Kotlin KSP vs KAPT — Kotlin 2.4.10 đã support KSP cho MapStruct chưa? (KAPT deprecated)
- [OPEN] `spring-boot-starter-grpc` — version nào compatible với Spring Boot 4.1.0? Có cần thêm vào platform BOM không?
- [OPEN] `@ImportHttpServices` — available từ Spring Boot 4.1 hay 4.0? Cần verify.

---

## Risk Registry (Updated After Brainstorm)

| Risk | Severity | Likelihood | Mitigation |
|------|:--------:|:----------:|------------|
| `BaseControllerAdvice` returns `ApiResponse` while auth uses `ProblemDetail` | 🔴 HIGH | Certain | Bridge pattern: `AuthControllerAdvice extends BaseControllerAdvice` + override |
| `BusinessException` always returns 422 | 🔴 HIGH | Certain | `AuthException` carries `httpStatus`, override handler uses it |
| LoginHandler exceeds 50-line target | 🟡 LOW | Likely | Accept ~55 lines (within tolerance), decompose if grows |
| MapStruct KAPT deprecated in Kotlin 2.4 | 🟡 MEDIUM | Possible | Check KSP support, fallback to manual mapping if needed |
| `spring-boot-starter-grpc` not in platform BOM | 🟡 LOW | Certain | Add to `platform/build.gradle.kts` dependencies |
| Phase 2 CQRS rollout breaks existing tests | 🟡 MEDIUM | Low | Feature flag `app.cqrs.enabled` for gradual rollout |
