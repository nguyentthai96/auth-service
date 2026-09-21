# Research Brief — Controller Request Context Standardization

## 1. Feature Overview

**Tên tính năng**: Controller Request Context Standardization  
**Mô tả**: Thiết kế lại cấu trúc Controller layer để loại bỏ code lặp, quản lý request context tập trung, và truyền data giữa các tầng Clean Architecture dễ dàng hơn.

**Input mode**: Idea (mô tả tự do từ user)

---

## 2. Vấn đề hiện tại

### 2.1 Code Duplication Matrix

| Duplicate Pattern | Số lần lặp | Các file bị ảnh hưởng |
|-------------------|-----------|----------------------|
| `getCurrentUserId()` | **7 lần** (7 private fun copy-paste) | CqrsAuthController, MfaController, SessionController, DeviceController, SsoController, AccountLifecycleController, AdminUnlockController |
| `extractClientIp()` | **3 lần** | CqrsAuthController, AnonymousAuthController, LoginRateLimitFilter |
| `extractBearerToken()` | **1 lần** (nhưng có thể cần ở nhiều nơi khác) | AnonymousAuthController |
| `extractAnonymousTokenJti()` | **1 lần** | CqrsAuthController |
| `LocaleContextHolder.getLocale()` + `messageSource.getMessage(...)` | **~15+ lần** | Hầu hết tất cả controllers |
| `SecurityContextHolder.getContext().authentication` | **8 lần** | Tất cả authenticated controllers |
| Header extraction (`X-Correlation-ID`, `User-Agent`, `X-Device-Fingerprint`) | **5+ lần** | CqrsAuthController, AnonymousAuthController |
| DTO → Command mapping (inline) | **Mỗi endpoint** | CqrsAuthController, AnonymousAuthController |

### 2.2 Architectural Violations

1. **Controller biết quá nhiều về infrastructure**: Controllers trực tiếp access `SecurityContextHolder`, `HttpServletRequest`, `Cookie`, header parsing.
2. **Không có Request Context trung tâm**: Mỗi controller tự extract header riêng → inconsistent (ví dụ: login dùng `X-Correlation-ID ?: X-Request-ID`, register chỉ dùng `X-Correlation-ID`).
3. **DTO → Command mapping nằm trong Controller**: Controller phải biết cách map từng field DTO sang Command → fat controller.
4. **MessageSource/i18n logic lặp**: `val locale = LocaleContextHolder.getLocale()` + `messageSource.getMessage(...)` xuất hiện ở mọi endpoint cần trả message.
5. **Pagination không chuẩn hóa**: `AdminSessionController` tự build pagination response bằng tay.

---

## 3. Keywords & Search Queries

1. `Spring Boot request context ThreadLocal`
2. `Clean Architecture controller base class pattern`
3. `Request-scoped bean Spring Boot`
4. `Controller boilerplate reduction Spring Kotlin`
5. `CQRS command mapping from DTO`
6. `Spring HandlerMethodArgumentResolver custom`
7. `MDC request context propagation`
8. `Spring WebMVC RequestContextHolder`

---

## 4. Current System Analysis

### 4.1 Related Features (Existing Patterns)

- **CQRS Pattern** (`Command` / `Handler`): Đã implement — `LoginCommand → LoginHandler`
- **Event Sourcing**: Đã có `EventStorePort`, `EventService`
- **Audit Trail Filter** (`ClientMetadataFilter`): Đã extract `X-App-Version`, `X-Client-Platform` vào MDC
- **Content Language Filter** (`ContentLanguageFilter`): Đã set `Content-Language` header
- **Service Auth Filter** (`ServiceAuthFilter`): Đã extract Bearer token cho internal APIs

### 4.2 Existing Architecture Patterns

```
Clean Architecture layers:
┌─────────────────────────────────────────────┐
│ adapter.in.web  (Controllers + DTOs)        │ ← WEB LAYER
├─────────────────────────────────────────────┤
│ application     (Handlers + Services)       │ ← USE CASE LAYER
├─────────────────────────────────────────────┤
│ domain          (Entities + Domain Logic)   │ ← DOMAIN LAYER
├─────────────────────────────────────────────┤
│ adapter.out     (Repos + Gateways)          │ ← INFRA LAYER
└─────────────────────────────────────────────┘
```

**CQRS flow hiện tại**:
```
Controller → (manual DTO→Command mapping) → Handler.handle(command) → Service → Repository
```

### 4.3 Tech Stack Constraints

| Component | Technology |
|-----------|-----------|
| Language | Kotlin (JVM 21) |
| Framework | Spring Boot 3.x |
| Web | Spring WebMVC |
| Auth | Spring Security (JWT) |
| DI | Spring IoC (Constructor injection) |
| Build | Gradle (Kotlin DSL) |
| CQRS lib | `com.ntt.eventsourcingutils` |

### 4.4 Modules

| Module | Controllers | Cần refactor? |
|--------|------------|--------------|
| `auth` | 16 controllers | ✅ Cao — duplicates nhiều nhất |
| `rbac` | 5 controllers (DomainController, RoleController, GroupController, ResourceController, PermissionCheckController) | ⚠️ Trung bình — ít duplicate nhưng cần chuẩn hóa pagination |
| `pbac` | 1 controller (PolicyController) | ⚠️ Nhẹ |

---

## 5. Scope

### In Scope
- ✅ Request Context infrastructure (request-scoped bean)
- ✅ Base controller classes
- ✅ Helper utilities cho header extraction, userId, pagination
- ✅ DTO → Command mapping convention
- ✅ i18n response helper
- ✅ Clean Architecture compliance

### Out of Scope
- ❌ Thay đổi business logic
- ❌ Thay đổi database schema
- ❌ Thay đổi API contract (request/response format)
- ❌ Migration sang reactive (WebFlux)
