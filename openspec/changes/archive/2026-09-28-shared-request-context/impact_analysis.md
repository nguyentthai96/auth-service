# Impact Analysis: shared-request-context

_Generated: 2026-09-28_

---

## 1. Core Files — NƠI SỬA

> Liệt kê các files CẦN MODIFY code và TẠO MỚI. BẮT BUỘC `file:///` link + line range.

| # | File | Line Range | Chức năng | Action |
|---|------|------------|-----------|--------|
| 1 | [RequestContext.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt) | L23-L78 | Chuyển đổi thành typealias hoặc subclass kế thừa BaseCore RequestContext để bảo đảm 100% backward-compatibility | MODIFY / EXTRACT |
| 2 | [RequestContextFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt) | L34-L175 | Loại bỏ code trùng lặp (MDC, IP, Headers, Metadata), kế thừa hoặc delegate sang BaseRequestContextFilter từ base-core | MODIFY / DELEGATE |
| 3 | [BaseController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/BaseController.kt) | L28-L46 | Cập nhật setter injection / type reference RequestContext trỏ tới BaseCore hoặc compatibility adapter | MODIFY |
| 4 | [AuditLogService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt) | L149-L165 | Thay thế logic tự parse HttpServletRequest & getClientIp trùng lặp bằng `RequestContextHolder.get()` | MODIFY / REUSE |
| 5 | [RequestContext.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/base-model/src/main/kotlin/com/ntt/basecore/context/RequestContext.kt) | L1-L120 | Data class chuẩn trung tâm lưu trữ identity, tenant, client metadata, correlation ID, tracing | NEW |
| 6 | [RequestContextHolder.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/base-model/src/main/kotlin/com/ntt/basecore/context/RequestContextHolder.kt) | L1-L90 | Quản lý ThreadLocal ngữ cảnh an toàn cho Virtual Threads (JDK 21) kèm cơ chế Non-Web fallback | NEW |
| 7 | [BaseRequestContextFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/web/filter/BaseRequestContextFilter.kt) | L1-L150 | Filter HTTP tiêu chuẩn xử lý Correlation ID, IP parsing (trusted proxy), MDC logging và RequestContextHolder lifecycle | NEW |
| 8 | [ContextTaskDecorator.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/context/decorator/ContextTaskDecorator.kt) | L1-L60 | Lan truyền RequestContext & MDC an toàn sang Async ThreadPool / Virtual Thread Executors | NEW |
| 9 | [SecurityContextBridgeFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/security/filter/SecurityContextBridgeFilter.kt) | L1-L80 | Bridge Authentication từ Spring SecurityContextHolder sang UserContext trong RequestContext | NEW |
| 10 | [RequestContextClientInterceptor.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-http-client-starter/src/main/kotlin/com/ntt/basecore/client/interceptor/RequestContextClientInterceptor.kt) | L1-L70 | RestClient/RestTemplate ClientHttpRequestInterceptor tự động lan truyền correlationId, tenantId và tùy chọn token relay | NEW |

---

## 2. Call Tree — LOGIC CẦN SỬA

> BẮT BUỘC ASCII tree. Ghi annotation `// ←` ở điểm quan trọng.

#### `Old RequestContextFilter.doFilterInternal()` (L40-L61 trong auth-service)

⟶ doFilterInternal(request, response, filterChain)
├── requestContextProvider.ifAvailable == null? → filterChain.doFilter()  // ← Bị phụ thuộc vào Spring RequestScope proxy
├── populateFromSecurityContext(ctx)                                     // ← Tight coupling với Spring Security
├── populateFromHeaders(ctx, request)                                    // ← Tự parse header X-Correlation-ID, X-Forwarded-For
├── populateClientMetadata(ctx, request)                                 // ← Hardcoded parser appVersion, clientPlatform
├── populateLocale(ctx)                                                  // ← LocaleContextHolder
├── setMdc(ctx)                                                          // ← Tự quản lý MDC
├── filterChain.doFilter()
└── finally: clearMdc()                                                  // ← MDC clear nhưng ThreadLocal RequestContext không có cleanup rõ ràng

#### `Refactored Decoupled Flow (Base-Core & Auth-Service)`

⟶ VirtualThreadMdcFilter.doFilterInternal() [HIGHEST_PRECEDENCE]          // ← com.ntt.commonlog: Outer guard phát hiện MDC contamination
    └── BaseRequestContextFilter.doFilterInternal() [HIGHEST_PRECEDENCE + 10]
        ├── extractOrCreateCorrelationId(request)                         // ← Priority: X-Correlation-ID -> X-Request-ID -> UUID v7
        ├── parseClientIp(request)                                        // ← RFC 7239 / X-Forwarded-For với trusted proxies filter
        ├── extractClientMetadata(request)                                // ← User-Agent, Device-Fingerprint, App-Version
        ├── RequestContextHolder.set(requestContext)                      // ← ThreadLocal.set() snapshot an toàn
        ├── setStandardMdc(requestContext)                                // ← correlationId, clientIp, appVersion, traceId
        ├── filterChain.doFilter()                                        // ──> Đi tiếp vào chuỗi Filter
        │   └── SecurityFilterChain
        │       └── SecurityContextBridgeFilter [AFTER SecurityContextHolderFilter]
        │           ├── read Authentication from SecurityContextHolder
        │           ├── populate UserContext(userId, username, roles, jti)
        │           ├── RequestContextHolder.setUserContext(userContext)  // ← Enrich identity vào RequestContext hiện tại
        │           └── updateMdcIdentity(userContext)                    // ← Enrich userId, username vào MDC
        │               └── Controller / Service Layer
        │                   ├── BaseController.injectBaseDependencies()   // ← Backward compatible!
        │                   ├── RequestContextHolder.get()                // ← Tĩnh, không cần inject proxy, không lo Non-Web
        │                   └── RestClient / WebClient Calls
        │                       └── RequestContextClientInterceptor.intercept() // ← Auto propagate X-Correlation-ID
        └── finally:
            ├── MDC.clear()                                               // ← Dọn dẹp MDC
            └── RequestContextHolder.clear()                              // ← ThreadLocal.remove() ngăn chặn carrier thread leak!

---

## 3. Blast Radius

### 🔴 Direct Impact — auth-service (4 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `RequestContext.kt` | [RequestContext.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt) | Chuyển đổi thành Adapter hoặc Typealias kế thừa base-core RequestContext |
| 2 | `RequestContextFilter.kt` | [RequestContextFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt) | Chuyển thành delegator hoặc deprecated wrapper trỏ tới base-web-starter BaseRequestContextFilter |
| 3 | `BaseController.kt` | [BaseController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/BaseController.kt) | Giữ nguyên public API của BaseController, delegate requestContext ngầm sang `RequestContextHolder.get()` |
| 4 | `AuditLogService.kt` | [AuditLogService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt) | L149-L165: Thay thế `RequestContextHolder.getRequestAttributes()` và duplicate `getClientIp()` bằng `RequestContextHolder.get()` |

### 🟡 Indirect Impact — auth-service Controllers & Tests (3 files)

| # | File | Link | Cách sử dụng |
|---|------|------|-------------|
| 1 | `AuthenticatedController.kt` | [AuthenticatedController.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/AuthenticatedController.kt) | Giữ nguyên các hàm `currentUserId()`, `currentJti()`, `requireRole()` nhờ RequestContext adapter |
| 2 | `DeviceControllerTest.kt` | [DeviceControllerTest.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/test/kotlin/com/ntt/authservice/auth/adapter/in/web/DeviceControllerTest.kt) | L29: `RequestContext().apply { ... }` vẫn compile & run bình thường |
| 3 | `SecurityConfig.kt` | [SecurityConfig.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt) | Đảm bảo vị trí FilterChain không xung đột với `BaseRequestContextFilter` và `SecurityContextBridgeFilter` |

### 🟠 Cross-service Impact (3 services / 4 files)

| # | File | Link | Protocol | Cách sử dụng |
|---|------|------|----------|-------------|
| 1 | `system-admin-service` / `AuditAspect.kt` | [AuditAspect.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/application/AuditAspect.kt) | Local Aspect | Thay thế Spring `ServletRequestAttributes` bằng `RequestContextHolder.get()` |
| 2 | `account-service` | [account-service](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/account-service) | HTTP Inbound / Inter-service | Tự động nhận `base-web-starter` và `base-http-client-starter` để có correlationId, user identity |
| 3 | `notification-service` | [notification-service](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/notification-service) | Kafka / Async | Sử dụng Non-Web `RequestContext.fallback("kafka-consumer")` khi xử lý background events |

### 🟢 Shared Utilities (2 files)

| # | File | Link | Methods dùng |
|---|------|------|-------------|
| 1 | `VirtualThreadMdcFilter.kt` | [VirtualThreadMdcFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/common-log/src/main/kotlin/com/ntt/commonlog/filter/VirtualThreadMdcFilter.kt) | `HIGHEST_PRECEDENCE` dọn rác MDC trước request, kết hợp chặt chẽ với `BaseRequestContextFilter` |
| 2 | `ApiResponse.kt` & `PageResponse.kt` | [ApiResponse.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/base-model/src/main/kotlin/com/ntt/basecore/domain/web/payload/ApiResponse.kt) | Được sử dụng trong các controllers cùng với `RequestContext` |

---

## 4. Reuse Map

| Logic Block | Existing Location | Match % | Decision | Impact | Action |
|---|---|---|---|---|---|
| RequestContext Data Model | [auth-service/RequestContext.kt:L25](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt#L25) | 90% | **EXTRACT** | 🟡 Medium (4 callers) | Trích xuất thành `com.ntt.basecore.context.RequestContext` trong `base-model` |
| Correlation ID & IP Parser | [auth-service/RequestContextFilter.kt:L89](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt#L89) | 85% | **EXTRACT** | 🟢 Low (2 callers) | Đưa vào `BaseRequestContextFilter` trong `base-web-starter` |
| MDC Keys Setup & Clear | [auth-service/RequestContextFilter.kt:L131](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt#L131) | 95% | **EXTRACT** | 🟢 Low (1 caller) | Tích hợp vào `BaseRequestContextFilter` và chuẩn hóa key trong `common-log` |
| Security Context Extractor | [auth-service/RequestContextFilter.kt:L66](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt#L66) | 80% | **EXTRACT** | 🟡 Medium (2 callers) | Đưa vào `SecurityContextBridgeFilter` trong `base-security-starter` |
| Client IP Extractor in Audit | [auth-service/AuditLogService.kt:L158](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt#L158) | 100% | **REUSE** | 🟢 Low (1 caller) | Loại bỏ hàm duplicate, gọi trực tiếp `RequestContextHolder.get().clientIp` |

### EXTRACT Details

#### E1: RequestContext Model & Holder
- **Source**: [RequestContext.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt#L25-L78)
- **Target**: `com.ntt.basecore.context.RequestContext` & `RequestContextHolder` — module `base-model`
- **Callers found**: 4 files (`RequestContextFilter.kt`, `BaseController.kt`, `AuthenticatedController.kt`, `DeviceControllerTest.kt`)
- **Impact level**: 🟡 Medium
- **Breaking changes**: Không (dùng subclass/adapter tại `auth-service` giữ nguyên package cũ).
- **Migration plan**:
  1. Tạo `RequestContext` & `RequestContextHolder` tại `base-model`.
  2. Tại `auth-service`, cập nhật `com.ntt.authservice.shared.web.RequestContext` kế thừa class mới, giữ nguyên các custom helper như `requireUserId()`, `requireJti()`.
  3. Cung cấp default singleton / thread-safe fallback cho non-web context.

#### E2: Base Web RequestContextFilter & MDC Management
- **Source**: [RequestContextFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt#L34-L175)
- **Target**: `com.ntt.basecore.web.filter.BaseRequestContextFilter` — module `base-web-starter`
- **Callers found**: 2 files
- **Impact level**: 🟢 Low
- **Breaking changes**: Không.
- **Migration plan**:
  1. Viết `BaseRequestContextFilter` trong `base-web-starter` với `@Order(Ordered.HIGHEST_PRECEDENCE + 10)`.
  2. Bổ sung `RequestContextAutoConfiguration` với `@ConditionalOnWebApplication`.
  3. `auth-service` chỉ cần khai báo starter, `appRequestContextFilter` cũ được chuyển thành subclass hoặc tắt đi khi starter kích hoạt.

---

## 5. Context Snapshot — ĐỦ ĐỂ CODE

### Dependencies

| Dependency | Type | Key Methods | Ghi chú |
|-----------|------|-------------|---------|
| `com.ntt.basecore.context.RequestContextHolder` | Static Context Holder | `get(): RequestContext`, `set(ctx)`, `clear()`, `runWith(ctx, block)` | Quản lý ThreadLocal ngữ cảnh |
| `com.ntt.basecore.context.decorator.ContextTaskDecorator` | Spring TaskDecorator | `decorate(Runnable): Runnable` | Lan truyền Context & MDC sang async tasks |
| `VirtualThreadMdcFilter` | Servlet Filter (Outer) | `doFilterInternal()` | HIGHEST_PRECEDENCE dọn rác MDC an toàn |
| `SecurityContextBridgeFilter` | Servlet Filter (Security) | `doFilterInternal()` | Nằm sau Spring Security Filter Chain để trích xuất UserContext |
| `RequestContextClientInterceptor` | ClientHttpRequestInterceptor | `intercept(HttpRequest, byte[], ClientHttpRequestExecution)` | Tự động lan truyền correlation ID và tenant ID sang downstream calls |

### Config Keys

| Key | Type | Default Value | Nơi dùng | Mô tả |
|-----|------|---------------|----------|-------|
| `app.context.correlation-id.header` | String | `X-Correlation-ID` | `BaseRequestContextFilter` | Header name cho correlation ID |
| `app.context.tenant-id.header` | String | `X-Tenant-Id` | `BaseRequestContextFilter` | Header name cho tenant ID |
| `app.http-client.forward-auth-token` | Boolean | `false` | `RequestContextClientInterceptor` | Cờ cấu hình Token Relay (giải quyết OQ-01: mặc định không forward Auth Token) |
| `app.context.trusted-proxies` | List<String> | `["127.0.0.1", "::1"]` | `BaseRequestContextFilter` | Danh sách IP proxy tin cậy để parse `X-Forwarded-For` |

### Error Codes Thrown

| Error Code | Condition | Nơi throw |
|-----------|-----------|-----------|
| `INVALID_CREDENTIALS` | Khi gọi `requireUserId()` hoặc `requireJti()` mà chưa đăng nhập | `RequestContext.kt:L59` |
| `ACCESS_DENIED` | Khi gọi `requireRole()` mà người dùng không có role cần thiết | `AuthenticatedController.kt:L38` |
| `SCOPE_NOT_ACTIVE` | (Được loại bỏ hoàn toàn nhờ cơ chế `RequestContext.fallback()`) | N/A |

### DTO & Model Reuse Check

| Model cần | Existing Model | Match % | Decision |
|---|---|---|---|
| RequestContext Data Model | `auth-service/shared/web/RequestContext` | 90% | EXTRACT sang `base-model` |
| UserContext Data Model | N/A (Inline fields in auth-service) | 100% new | NEW trong `base-model` |
| Context Snapshot Model | N/A | 100% new | NEW trong `base-model` để phục vụ Async/TaskDecorator |

### Base API Verification

| API Call | Verified Method | Source | Status |
|---|---|---|---|
| `RequestContextHolder.get()` | `fun get(): RequestContext` | `base-model` | ✅ Verified |
| `RequestContextHolder.clear()` | `fun clear()` | `base-model` | ✅ Verified |
| `TaskDecorator.decorate()` | `override fun decorate(runnable: Runnable): Runnable` | Spring Framework `core.task` | ✅ Verified |
| `ClientHttpRequestInterceptor.intercept()` | `override fun intercept(...)` | Spring Framework `http.client` | ✅ Verified |
| `MDC.put()` / `MDC.remove()` | `SLF4J MDC static methods` | `org.slf4j.MDC` | ✅ Verified |
