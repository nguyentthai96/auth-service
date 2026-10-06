---
type: brainstorm_notes
change: filter-architecture-consolidation
date: 2026-10-05
selected_direction: "Migrate-first: base-core consolidation → service migration → auth-service refactor"
pre_flow: "Non-Financial"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: Filter Architecture Consolidation

## Date
2026-10-05

## Context
Review filter architecture cross-service phát hiện:
- ContentLanguageFilter chỉ tồn tại ở auth-service, account/sysadmin thiếu 100% happy path Content-Language
- ResourceJwtAuthFilter duplicate giữa account/sysadmin (86 LOC identical), trong khi base-core ĐÃ CÓ DefaultSessionValidationFilter
- JwtAuthFilter auth-service là "God object" (236 LOC, 7 deps)
- Error response format inconsistent across filters (ProblemDetail vs sendError vs empty body)

## Questions Asked & Answers

- Q1: Scope brainstorm? → A: Toàn bộ filter architecture review (4 threads)
- Q2: DefaultSessionValidationFilter đã deploy chưa? → A: Mới tạo nhưng chưa deploy sang services
- Q3: Sequencing strategy? → A: Approach A — Migrate-first (base-core → services → auth refactor)
- Q4: Content-Language merge vào đâu? → A: Phương án A — Thêm vào BaseRequestContextFilter (zero filter mới)
- Q5: ResourceJwtAuthFilter migration? → A: Xóa hoàn toàn, dùng DefaultSessionValidationFilter 100%
- Q6: JwtAuthFilter refactor scope? → A: Refactor lớn — pipeline approach + kế thừa AbstractSessionValidationFilter

## Approaches Considered

### Approach A: Migrate-first (SELECTED ✅)
- Base-core first → service migration → auth refactor
- Pros: Bottom-up, mỗi step tự test được, backward compatible
- Cons: Nhiều changes sequential, cần deploy base-core trước

### Approach B: Bottom-up (fix perf first)
- Fix JwtAuthFilter performance → standardize errors → migrate
- Pros: Immediate performance win, auth-service isolated
- Cons: Không giải quyết root cause (code duplicate)

### Approach C: Scope tách nhỏ
- 4 changes song song, mỗi cái là OpenSpec riêng
- Pros: Parallelizable, risk isolation
- Cons: Dependency management phức tạp, khó test integration

## Selected Direction

**Migrate-first** — 4 phases tuần tự, mỗi phase là 1 PR deployable:

```
Phase 1: base-core enhancement
├── BaseRequestContextFilter + Content-Language header
├── OrderConstants (không cần thêm constant mới)
└── RequestContextProperties + contentLanguageEnabled flag

Phase 2: account-service + system-admin-service migration
├── Enable DefaultSessionValidationFilter (app.security.session-validation.enabled=true)
├── Xóa ResourceJwtAuthFilter.kt hoàn toàn
├── Sửa SecurityConfig.kt — bỏ addFilterBefore(jwtAuthFilter)
├── Bỏ inline Content-Language trong ControllerAdvice
└── Configure publicPaths trong application.yml

Phase 3: auth-service JwtAuthFilter → pipeline refactor
├── Kế thừa AbstractSessionValidationFilter
├── Tách thành components:
│   ├── TokenAuthenticator (signature validation)
│   ├── BlacklistChecker (cache-based, already exists: TokenBlacklistCacheService)
│   ├── ClaimValidatorChain (already exists: ClaimValidatorChain)
│   ├── FingerprintValidator (already exists: FingerprintService)
│   └── MetricsRecorder (cache Timer instances, domain events)
├── Xóa ContentLanguageFilter.kt (base-core đã handle)
└── Sửa SecurityConfig.kt

Phase 4: Error response standardization
├── AbstractSessionValidationFilter.sendUnauthorized() → RFC 7807 ProblemDetail
├── ServiceAuthFilter → dùng cùng format
├── LoginRateLimitFilter → verify đã dùng ProblemDetail
└── GlobalExceptionHandler → verify consistency
```

## Architecture Vision

### Before (hiện tại)

```
                    base-core                          services
                    ─────────                          ────────
Filters:  BaseRequestCtxF ────────────────▶ ALL services (auto)
          ContentCachingF ────────────────▶ ALL services (auto)
          SecurityCtxBridgeF ──────────────▶ ALL services (auto)
          DefaultSessionValidF ────────────▶ ❌ CHƯA AI DÙNG
          AbstractSessionValidF ───────────▶ ❌ CHƯA AI DÙNG

          ❌ ContentLanguageFilter          ▶ ❌ THIẾU ở base-core

i18n:     I18nAutoConfig ─────────────────▶ ALL services (auto)
          BaseControllerAdvice ────────────▶ Content-Language chỉ error path

          auth-service:    JwtAuthFilter (236 LOC, God object)
                          ContentLanguageFilter (local)
                          LoginRateLimitFilter (local)
                          ServiceAuthFilter (local)

          account-svc:     ResourceJwtAuthFilter (duplicate!)
          sysadmin-svc:    ResourceJwtAuthFilter (duplicate!)
```

### After (target)

```
                    base-core                          services
                    ─────────                          ────────
Filters:  BaseRequestCtxF + Content-Language ▶ ALL services (auto) ✅
          ContentCachingF ────────────────▶ ALL services (auto)
          SecurityCtxBridgeF ──────────────▶ ALL services (auto)
          DefaultSessionValidF ────────────▶ account + sysadmin (auto) ✅

i18n:     I18nAutoConfig ─────────────────▶ ALL services (auto)
          BaseControllerAdvice ────────────▶ Content-Language → belt-and-suspenders

          auth-service:    AuthSessionValidationFilter
                           (extends AbstractSessionValidF)
                           ├── TokenBlacklistCacheService
                           ├── ClaimValidatorChain
                           ├── FingerprintService
                           └── TokenEventRecorder
                          LoginRateLimitFilter (local, đã tốt)
                          ServiceAuthFilter (local, đã tốt)

          account-svc:     ❌ Xóa ResourceJwtAuthFilter ✅
          sysadmin-svc:    ❌ Xóa ResourceJwtAuthFilter ✅
```

### Lines of Code Impact

| Change | LOC removed | LOC added | Net |
|--------|------------|-----------|-----|
| Content-Language → BaseRequestCtxF | 48 (auth CLF) + 2 (advice inline) | ~5 | **-45** |
| ResourceJwtAuthFilter migration | 86x2 = 172 | ~15 (yml config) | **-157** |
| JwtAuthFilter → pipeline | 236 (monolith) | ~200 (split classes) | **-36** |
| Error standardization | ~10 (sendError) | ~15 (ProblemDetail) | **+5** |
| **Total** | **~466** | **~235** | **-231** |

## Pre-classifications (preliminary)
- Feature type: MAINTENANCE (code quality + consolidation)
- Flow type: Non-Financial (infrastructure change)
- Affected modules:
  - `base-core/base-web-starter` (BaseRequestContextFilter, RequestContextProperties)
  - `base-core/base-security-starter` (AbstractSessionValidationFilter error format)
  - `auth-service` (JwtAuthFilter refactor, ContentLanguageFilter removal, SecurityConfig)
  - `account-service` (ResourceJwtAuthFilter removal, SecurityConfig, application.yml)
  - `system-admin-service` (ResourceJwtAuthFilter removal, SecurityConfig, application.yml)

## GitNexus Findings (explored via grep/codebase scan)

### Key Symbols
| Symbol | Module | Role |
|--------|--------|------|
| `BaseRequestContextFilter` | base-web-starter | MDC + RequestContext owner, HIGHEST_PRECEDENCE |
| `DefaultSessionValidationFilter` | base-security-starter | Zero-config JWT auth (chưa deploy) |
| `AbstractSessionValidationFilter` | base-security-starter | Template method for JWT validation |
| `ResourceJwtAuthFilter` | account/sysadmin | Duplicate JWT filter (legacy) |
| `JwtAuthFilter` | auth-service | God object (7 deps, 236 LOC) |
| `OrderConstants` | base-core | Centralized filter order (-2000 to 300) |
| `FilterChainProperties` | base-web-starter | Shared exclude paths + config |
| `I18nAutoConfiguration` | base-core | LocaleResolver + MessageSource |

### Architecture Insights
- `DefaultSessionValidationFilter` đã implement đúng pattern mà ResourceJwtAuthFilter cần
- auth-service `JwtAuthFilter` cần custom implementation vì có: blacklist, fingerprint, claim chain, anonymous token, metrics + events
- `AbstractSessionValidationFilter` là base class phù hợp cho cả auth-service (custom) lẫn resource services (default)

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| DefaultSessionValidF thay đổi filter type (servlet vs security chain) | SecurityConfig phải refactor | Test kỹ authorization rules sau migration |
| BaseRequestContextFilter chạy trước DispatcherServlet | Locale có thể fallback default (en) | I18nAutoConfig đã set AcceptHeaderLocaleResolver trước filter chain |
| JwtAuthFilter refactor pipeline có thể thay đổi behavior | Auth flow regression | Unit test + integration test trước và sau |
| Multiple services affected cùng lúc | Coordinated deployment | Phase-by-phase: base-core deploy trước, services deploy theo |

## Open Questions for Design Phase

### Q1: BaseControllerAdvice.setContentLanguageHeader() — giữ hay xóa?
- **Status**: [RESOLVED] → **GIỮ LẠI** (belt-and-suspenders)
- **Trade-off analysis**:
  - `response.setHeader()` là **idempotent**, cost ~0 nanoseconds
  - BaseRequestContextFilter set Content-Language TRƯỚC → ControllerAdvice set lại SAU = cùng giá trị, zero harm
  - Nếu filter bị disable (config) hoặc path excluded → advice là backup an toàn
  - Dev mới có thể confused → **thêm comment giải thích "belt-and-suspenders"** trong advice
  - Safety value >> DX concern
- **Decision**: Giữ `setContentLanguageHeader()` trong BaseControllerAdvice, thêm comment giải thích

### Q2: AuthSessionValidationFilter.isTokenRevoked() — direct call vs constructor inject?
- **Status**: [RESOLVED] → **Constructor injection** (delegate pattern)
- **Reasoning**:
  - Constructor injection tuân thủ DI best practice, testable, extensible
  - Từng service mở rộng bằng cách tạo custom filter extends AbstractSessionValidationFilter
  - auth-service: inject TokenBlacklistCacheService, override isTokenRevoked() → 1 line delegate
  - account/sysadmin: dùng DefaultSessionValidationFilter (isTokenRevoked = false)
  - Future: nếu account/sysadmin cần blacklist → tạo custom filter kế thừa Abstract
- **Pattern**:
  ```kotlin
  class AuthSessionValidationFilter(
      private val tokenBlacklistCacheService: TokenBlacklistCacheService,
      // ...other deps via constructor
  ) : AbstractSessionValidationFilter(publicPaths) {
      override fun isTokenRevoked(tokenId: String) = tokenBlacklistCacheService.isBlacklisted(tokenId)
  }
  ```

### Q3: MetricsRecorder — class riêng hay dùng ObservableFilterWrapper?
- **Status**: [RESOLVED] → **Dùng lại ObservableFilterWrapper** (existing base-core)
- **Reasoning**:
  - ObservableFilterWrapper đã có sẵn: Observation.createNotStarted() + timer + tracing
  - Wrap AuthSessionValidationFilter qua ObservableFilterWrapper.wrap() → tự có duration metric
  - Business metrics riêng (counter auth.token.validation, event recording) → giữ trong filter body
  - KHÔNG tạo class MetricsRecorder riêng — avoid over-engineering

### Q4: SecurityConfig auth-service — servlet filter vs security chain?
- **Status**: [RESOLVED] → **Servlet Filter Registration** (FilterRegistrationBean + OrderConstants)
- **Trade-off analysis**:
  - DefaultSessionValidationFilter (base-core) đã chọn servlet registration (order: -1700)
  - auth-service nên dùng cùng model → **consistency across ecosystem**
  - OrderConstants quản lý tập trung → dễ reason about ordering
  - @ConditionalOnMissingBean: auth-service tạo custom bean → DefaultSessionValidF auto-disable
  - LoginRateLimitFilter + ServiceAuthFilter: **GIỮ trong security chain** (cần authorize context)
- **Final architecture** (auth-service):
  ```
  Servlet Container
  ├── BaseRequestCtxF           (order: -2000) ← Content-Language
  ├── ContentCachingF           (order: -1950)
  ├── AuthSessionValidationF    (order: -1700) ← validate JWT, set SecurityContext
  ├── SecurityCtxBridgeF        (order: -50)
  │
  └── DelegatingFilterProxy → SecurityFilterChain
       ├── LoginRateLimitFilter  (before UsernamePasswordAF)
       ├── ServiceAuthFilter     (before UsernamePasswordAF)
       ├── AuthorizationFilter   → DynamicAuthorizationManager
       └── ...
  ```

### Q5: notification-service — scan và apply?
- **Status**: [RESOLVED] → **Bỏ qua** (hiện tại chưa cần)
- Notification-service sẽ tự động nhận Content-Language từ BaseRequestContextFilter khi upgrade base-core
- JWT filter migration cho notification-service để change riêng nếu cần

## Open Questions for URD Analysis
- Không có URD formal — brainstorm từ code review analysis
- Nếu cần formal URD → tạo từ filter_architecture_review.md và filter_crossservice_map.md
- Tất cả 5 open questions đã RESOLVED → ready cho /wf_openspec
