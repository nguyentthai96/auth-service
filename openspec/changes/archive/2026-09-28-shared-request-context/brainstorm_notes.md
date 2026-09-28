---
type: brainstorm_notes
change: shared-request-context
date: 2026-09-28
selected_direction: "Decoupled Layered Architecture (Base Web Context + Security Bridge + Inter-Service Propagation)"
pre_flow: "Non-Financial (Infrastructure / Cross-Cutting Observability)"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Chuẩn Hóa và Đưa RequestContext / RequestContextFilter vào base-core

## Date
2026-09-28

## Context
User nhận thấy logic trong `RequestContextFilter` của `auth-service` (lấy correlation/request id, auth JWT, session của user, thông tin device, client ip, nạp MDC) là nhu cầu chung của mọi microservice trong hệ sinh thái (`auth-service`, `account-service`, `notification-service`, `system-admin-service`).
User yêu cầu phân tích, deep-thinking, debate đa chiều để đưa ra phương án tối ưu nhất cho việc đưa logic này vào `base-core`.

---

## Questions Asked & Answers
- **Q1: Có nên đưa RequestContext và Filter vào base-core không?**
  - **A**: **CÓ**. Đây là cross-cutting concern mang tính nền tảng cho distributed tracing, audit logging, và structured logging (MDC) trong hệ thống microservices. Nếu không đưa vào `base-core`, mỗi service sẽ tiếp tục code ad-hoc (như `AuditAspect` trong `system-admin-service` đang tự tách `X-Forwarded-For` và `userPrincipal`), dẫn đến trùng lặp code và đứt gãy trace.
- **Q2: Có nên bê nguyên xi `RequestContextFilter` của `auth-service` sang `base-core` không?**
  - **A**: **KHÔNG**. `auth-service` là Identity Provider (Issuer), chứa các khái niệm riêng biệt như `anonymousSessionId`, `anonymousTokenJti`, và `InvalidCredentialsException`. Bê nguyên xi sẽ gây ra *Leaky Abstraction* và ép các service không dùng Spring Security vẫn phải kéo theo dependencies bảo mật. Cần tách thành 2 tầng: *Web Transport Context* (không phụ thuộc security) và *Security Identity Context*.
- **Q3: Cơ chế lưu trữ `@RequestScope` bean có an toàn cho mọi môi trường không?**
  - **A**: **KHÔNG AN TOÀN NẾU DÙNG ĐƠN ĐỘC**. `@RequestScope` chỉ hoạt động trong luồng HTTP Servlet request. Khi service xử lý Kafka Message (`@KafkaListener`), Scheduled Job (`@Scheduled`), hoặc Async task (`@Async`), `@RequestScope` sẽ ném ngoại lệ `ScopeNotActiveException`. Cần kết hợp `RequestContextHolder` (ThreadLocal an toàn với Virtual Threads) + `TaskDecorator` để hỗ trợ cả Web lẫn Non-Web / Async.
- **Q4: Inbound Filter đã đủ chưa hay cần thêm Outbound Propagation?**
  - **A**: **CHƯA ĐỦ**. Khi Service A gọi Service B (qua Feign/RestClient) hoặc publish event sang Kafka, nếu không có Outbound Interceptor forward `X-Correlation-ID` thì toàn bộ chuỗi trace trên hệ thống giám sát tập trung (ELK/Grafana) sẽ bị đứt đoạn. Do đó, cần hỗ trợ cả Inbound Filter lẫn Outbound Propagation.

---

## Approaches Considered

### Approach 1: Bê nguyên si (Lift & Shift) từ `auth-service` sang `base-web-starter`
Chuyển trực tiếp `RequestContext` (@RequestScope) và `RequestContextFilter` từ `auth-service` sang `base-web-starter`.
- **Pros**:
  - Tốc độ triển khai nhanh nhất, ít phải viết lại logic.
- **Cons**:
  - Vi phạm nguyên lý Single Responsibility & Separation of Concerns.
  - Các khái niệm đặc thù của `auth-service` (`anonymousSessionId`, `anonymousTokenJti`, `InvalidCredentialsException`) làm ô nhiễm `base-core`.
  - Ép `base-web-starter` phải phụ thuộc chặt vào Spring Security (`SecurityContextHolder`, `GrantedAuthority`).
  - Gây lỗi `ScopeNotActiveException` trên các luồng Kafka / Scheduled tasks.

### Approach 2: Tạo riêng một Starter mới `starters/base-context-starter`
Tạo một module starter hoàn toàn mới trong `base-core` chứa toàn bộ Context, Tracing, và MDC.
- **Pros**:
  - Đóng gói tập trung vào một nơi duy nhất.
- **Cons**:
  - Làm tăng số lượng module con trong `base-core` không cần thiết.
  - Tất cả các downstream service phải khai báo thêm một dependency mới trong `build.gradle.kts`.
  - Vẫn phải giải quyết bài toán phụ thuộc vòng (circular dependency) nếu muốn tích hợp cả Web lẫn Security.

### Approach 3: Kiến trúc Phân tầng Bóc tách (Decoupled Layered Architecture) — [RECOMMENDED]
Tận dụng hệ thống module hiện có của `base-core` theo đúng trách nhiệm kiến trúc:
1. **`base-core` (root/base-model)**: Định nghĩa `RequestContext` data class, `UserContext` data class, và `RequestContextHolder` (ThreadLocal chuẩn, Loom-safe).
2. **`starters/base-web-starter`**: Cung cấp `BaseRequestContextFilter` (trích xuất `X-Correlation-ID`, `X-Forwarded-For`, device info, nạp MDC ban đầu). Độc lập 100% với Spring Security.
3. **`starters/base-security-starter`**: Cung cấp `SecurityContextBridgeFilter` (chạy sau Spring Security filter chain để chuyển đổi `Authentication` thành `UserContext` và gắn vào `RequestContext`).
4. **`starters/base-http-client-starter`**: Cung cấp `RequestContextClientInterceptor` để tự động forward `X-Correlation-ID` và metadata sang các service hạ nguồn.
- **Pros**:
  - Tuân thủ triệt để Clean Architecture và Single Responsibility.
  - Microservice không dùng Spring Security vẫn sử dụng được đầy đủ HTTP metadata, IP, và MDC logging.
  - Tương thích hoàn hảo với Virtual Threads (Java 21) và không làm crash các background jobs / Kafka listeners.
  - Khép kín vòng đời tracing phân tán (cả Inbound lẫn Outbound).
- **Cons**:
  - Cần cập nhật 3 starter trong `base-core` thay vì gom vào 1 file duy nhất.

---

## Selected Direction
**Chọn Approach 3: Kiến trúc Phân tầng Bóc tách (Decoupled Layered Architecture)**.
Lý do:
- Đảm bảo tính mở rộng dài hạn (Extensibility) cho toàn bộ hệ sinh thái dịch vụ.
- Tránh được bẫy `ScopeNotActiveException` và rò rỉ bộ nhớ trên Virtual Threads.
- Không gây ô nhiễm `base-core` bởi các domain logic riêng của `auth-service`.

---

## Pre-classifications (preliminary)
- **Feature type**: EXTEND
- **Flow type**: Non-Financial (Cross-Cutting Infrastructure / Observability)
- **Affected modules**:
  - `components/base-core` (`base-model`, `base-web-starter`, `base-security-starter`, `base-http-client-starter`)
  - `services/auth-service` (Migrate to use base-core context)
  - `services/system-admin-service` (Replace ad-hoc IP/User extraction in `AuditAspect`)
  - `services/account-service` (Adopt standardized RequestContext & MDC)
  - `services/notification-service` (Adopt standardized RequestContext & MDC)

---

## GitNexus Findings & Codebase Analysis
- **`auth-service`**:
  - [`RequestContext`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt): Đang được inject trong 22 controllers và `AuditLogService`.
  - [`RequestContextFilter`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt): Thứ tự filter `LOWEST_PRECEDENCE - 20`.
- **`system-admin-service`**:
  - [`AuditAspect.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/application/AuditAspect.kt): Chứa hàm `getClientIp()` và `extractUserId()` thủ công. Cần thay bằng `RequestContextHolder`.
- **`account-service`**:
  - [`ResourceJwtAuthFilter.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/account-service/src/main/kotlin/com/ntt/accountservice/shared/security/ResourceJwtAuthFilter.kt): Đã parse JWT RS256 và set `SecurityContextHolder`, nhưng thiếu lớp cầu nối sang `RequestContext`.
- **`base-core`**:
  - Đã có [`VirtualThreadMdcFilter.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/common-log/src/main/kotlin/com/ntt/commonlog/filter/VirtualThreadMdcFilter.kt) (`HIGHEST_PRECEDENCE`) để dọn sạch MDC dư thừa trên Virtual Threads.
  - Đã có [`IpInfoUtil.kt`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/common-log/src/main/kotlin/com/ntt/commonlog/utils/IpInfoUtil.kt) xử lý đa proxy `X-Forwarded-For`, `Proxy-Client-IP`, `WL-Proxy-Client-IP`.

---

## Open Questions for Design Phase (`/wf_openspec`)
- [RESOLVED] **Token Relay vs Header Trust**: Đối với các cuộc gọi nội bộ (Service-to-Service):
  - **Quyết định (Approved)**: Mặc định chỉ forward `X-Correlation-ID` (và `X-Tenant-Id` nếu có) để giữ overhead nhẹ và an toàn (Zero-trust / Least Privilege).
  - Cung cấp cấu hình `app.http-client.forward-auth-token: false` (mặc định) cho phép bật Token Relay (`Authorization: Bearer <token>`) khi một service downstream cụ thể thực sự cần.
- [RESOLVED] **Custom Claims Extensibility**: Cung cấp extension point `UserContextCustomizer` (functional interface `@FunctionalInterface fun customize(builder, claims)`) để downstream service tự động bổ sung claims riêng (`activeDomain`, `tenantId`) mà không làm bẩn model `base-core`.
- [RESOLVED] **Virtual Thread Safety**: Sử dụng `ThreadLocal` chuẩn thay vì `InheritableThreadLocal` để tránh rò rỉ carrier thread theo tài liệu kiến trúc Virtual Threads của dự án.
- [RESOLVED] **MDC Keys**: Thống nhất danh sách MDC chuẩn hóa gồm: `correlationId`, `userId`, `clientIp`, `appVersion`, `clientPlatform`.

---

## Visual Architecture Blueprint

```
[Inbound Request]
       │
       ▼
[VirtualThreadMdcFilter] (HIGHEST_PRECEDENCE) ──────────▶ Xóa MDC rác còn sót lại
       │
       ▼
[BaseRequestContextFilter] (HIGHEST_PRECEDENCE + 10) ───▶ Đọc IP (IpInfoUtil), Device, UUID
       │                                                  Đặt vào RequestContextHolder
       ▼                                                  Nạp correlationId, clientIp vào MDC
[Spring Security / JWT Filter] ─────────────────────────▶ Xác thực token (RS256/OAuth2)
       │                                                  Gán Authentication vào SecurityContext
       ▼
[SecurityContextBridgeFilter] (LOWEST_PRECEDENCE - 20) ─▶ Lấy Authentication -> UserContext
       │                                                  Gán vào RequestContext.user
       ▼                                                  Nạp userId vào MDC
[Controller / Service / AuditAspect] ───────────────────▶ Đọc thông tin từ RequestContextHolder
       │
       ▼ (Outbound Service-to-Service)
[RequestContextClientInterceptor] ──────────────────────▶ Đính kèm X-Correlation-ID vào RestClient
```
