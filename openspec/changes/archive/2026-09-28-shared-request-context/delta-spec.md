# Delta Specification: Shared RequestContext in Base-Core

## Overview
Tài liệu này ghi nhận sự thay đổi (delta) giữa kiến trúc quản lý RequestContext cũ tại từng microservice (`auth-service`) và kiến trúc dùng chung tại `base-core`.

---

## 1. Context Model & Holder

| Thuộc tính / Khía cạnh | Kiến trúc cũ (auth-service) | Kiến trúc mới (base-core) |
| :--- | :--- | :--- |
| **Vị trí** | `com.ntt.authservice.shared.web.RequestContext` (request scope bean) | `com.ntt.basecore.context.RequestContext` (`base-model`) |
| **Lưu trữ luồng** | Spring RequestScope bean (lỗi khi chạy ngoài Servlet Web) | `ThreadLocal<RequestContext>` + fallback non-web an toàn |
| **Virtual Threads (JDK 21)** | Nguy cơ leak nếu dùng scope không giải phóng | Strict `ThreadLocal` + dọn dẹp trong `finally` của filter + `VirtualThreadContextLeakTest` |
| **UserContext** | Tách rời, chỉ có trong `auth-service` | Chuẩn hóa `UserContext(userId, username, roles, jti, authenticated)` |
| **Tương thích ngược** | N/A | `auth-service/RequestContext.kt` là adapter ủy quyền sang `RequestContextHolder.get()` |

---

## 2. Web Filter & MDC Orchestration

| Khía cạnh | Cũ | Mới |
| :--- | :--- | :--- |
| **Filter** | `RequestContextFilter` trong từng service | `BaseRequestContextFilter` (`base-web-starter`), `@Order(HIGHEST_PRECEDENCE + 10)` |
| **Correlation ID** | Tự sinh hoặc đọc `X-Request-Id` | Chuẩn hóa `X-Correlation-ID` -> fallback `X-Request-ID` -> sinh UUID v7; tự động echo vào response header |
| **MDC Cleanup** | Dọn dẹp cục bộ | Tự động dọn sạch 100% trong khối `finally` của `BaseRequestContextFilter` |
| **Async Task Propagation** | Không có decorator dùng chung | Cung cấp `ContextTaskDecorator` để truyền Context và MDC sang background thread |

---

## 3. Security Bridge & Client Interceptor (Service-to-Service)

| Khía cạnh | Cũ | Mới |
| :--- | :--- | :--- |
| **Security Bridge** | Tự làm trong `RequestContextFilter` | `SecurityContextBridgeFilter` trong `base-security-starter` chạy sau Spring Security |
| **Inter-service HTTP Calls** | Không tự động forward Correlation ID | `RequestContextClientInterceptor` tự động truyền `X-Correlation-ID` và `X-Tenant-Id` qua `RestClient` / `RestTemplate` |
| **Token Relay (OQ-01)** | Không hỗ trợ | Mặc định không relay token; hỗ trợ cờ cấu hình `app.http-client.forward-auth-token: true` để forward `Authorization` khi cần |

---

## 4. Downstream Impact

- **`auth-service`**:
  - `RequestContext.kt`: Adapter giữ nguyên signature cũ, tương thích hoàn toàn với các controller hiện hành.
  - `RequestContextFilter.kt`: Đồng bộ `UserContext` vào `RequestContextHolder`, loại bỏ xử lý trùng lặp IP/MDC.
  - `AuditLogService.kt`: Loại bỏ mã tự bóc tách header `X-Forwarded-For`, dùng trực tiếp `RequestContextHolder.get()`.
- **`system-admin-service`**:
  - `AuditAspect.kt`: Đọc trực tiếp từ `RequestContextHolder.get()`, loại bỏ cảnh báo khi thực thi bất đồng bộ.
