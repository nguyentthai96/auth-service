---
type: template
name: research_brief
version: "1.0"
language: vi
---

# Research Brief: shared-request-context (Unified Request Context & Distributed Observability)

> Tài liệu khởi đầu cho quá trình research tính năng — chuẩn hóa Request Context, Correlation ID, User Identity và MDC logging đưa vào base-core cho toàn bộ hệ sinh thái Microservices.

## 1. Thông tin chung

| Mục | Nội dung |
|-----|----------|
| **Tên tính năng** | `shared-request-context` (Unified Request Context & Observability) |
| **Ngày tạo** | 2026-09-28 |
| **Input source** | file |
| **Input content** | `brainstorm_request_context_base_core.md` (phân tích hiện trạng `auth-service.RequestContextFilter` và đề xuất đưa vào `base-core`) |
| **Người yêu cầu** | Tech Lead / System Architect |

---

## 2. Mô tả tính năng

### 2.1 Bối cảnh (Context)
Hiện tại, các microservices trong hệ sinh thái (`auth-service`, `account-service`, `system-admin-service`, `notification-service`) đang xử lý request context và metadata một cách phân mảnh:
- `auth-service` tự xây dựng `RequestContextFilter` & `RequestContext` nhưng bị gắn chặt với domain nội bộ của Auth (anonymous session, exception riêng).
- `system-admin-service` trong `AuditAspect` phải tự viết logic ad-hoc bóc tách `X-Forwarded-For` và `userPrincipal`.
- `account-service` chỉ có `ResourceJwtAuthFilter`, không có Correlation ID tracking hay MDC logging.
- `notification-service` không có bất kỳ cơ chế tracking nào, gây đứt đoạn chuỗi vết (distributed trace) khi nhận request hoặc consume Kafka event.

Do đó, cần chuẩn hóa và đưa thành phần Request Context & Observability Filter vào `base-core` để tái sử dụng xuyên suốt toàn bộ các microservices.

### 2.2 Mục tiêu (Objectives)
- [x] **Zero Duplication**: Loại bỏ toàn bộ code bóc tách header, IP client, device fingerprint, và user session rải rác ở từng service.
- [x] **Decoupled Architecture**: Phân tách rõ ràng giữa *Web Transport Context* (không phụ thuộc Security) và *Security Identity Context* (tích hợp Spring Security / JWT).
- [x] **Concurrency & Loom Safety**: Đảm bảo an toàn tuyệt đối khi chạy trên Java 21 Virtual Threads (không leak carrier thread memory) và hỗ trợ truyền context qua các luồng bất đồng bộ (`@Async`, background executors).
- [x] **Non-Web Fallback**: Cung cấp fallback an toàn cho Kafka Listeners và Scheduled Tasks mà không bị lỗi `ScopeNotActiveException`.
- [x] **Bi-directional Observability**: Hỗ trợ cả Inbound Filter (đọc headers -> set MDC) lẫn Outbound Propagation (truyền `X-Correlation-ID` qua HTTP client và Kafka producer).

### 2.3 Phạm vi ban đầu (Initial Scope)

| In Scope | Out of Scope |
|----------|-------------|
| Model `RequestContext`, `UserContext`, `RequestContextHolder` trong `base-core` / `base-model` | Thay thế hoàn toàn Gateway (Gateway vẫn giữ vai trò cổng biên) |
| Inbound `BaseRequestContextFilter` và nạp MDC trong `starters/base-web-starter` | Thay đổi thuật toán ký/mã hóa token JWT |
| Cầu nối `SecurityContextBridgeFilter` trong `starters/base-security-starter` | Thay đổi format log của ELK / Loki bên ngoài |
| Outbound Client Interceptor trong `starters/base-http-client-starter` | |
| TaskDecorator cho Virtual Threads / ThreadPoolTaskExecutor | |

---

## 3. Keywords & Search Terms

### 3.1 Primary Keywords
- `Spring Boot 3 RequestContext`
- `Correlation ID filter MDC`
- `Micrometer Tracing context propagation`
- `Virtual Threads ThreadLocal MDC leak`
- `Spring Security SecurityContext to RequestContext`

### 3.2 Secondary Keywords
- `Zalando Logbook Spring Boot 3`
- `TaskDecorator MDC propagation Spring`
- `X-Correlation-ID HTTP client interceptor`
- `KafkaTracingProducerInterceptor record headers`

### 3.3 Domain-Specific Terms
- `Correlation ID`: Chuỗi định danh duy nhất xuyên suốt một chuỗi giao dịch phân tán qua nhiều microservices.
- `MDC (Mapped Diagnostic Context)`: Bản đồ key-value của SLF4J gắn với luồng hiện tại để in kèm trong mọi dòng log cấu trúc.
- `Virtual Threads (Loom)`: Luồng nhẹ của JDK 21+, cần chú ý dọn dẹp `ThreadLocal` để tránh ô nhiễm luồng vận chuyển (carrier thread).
- `SecurityContextBridge`: Bộ chuyển đổi đưa thông tin danh tính từ Spring Security vào Domain Context mà không làm lộ framework sang tầng dưới.

### 3.4 Search Queries

| # | Query | Target | Priority |
|---|-------|--------|----------|
| 1 | `"Spring Boot 3" correlation id filter MDC virtual threads best practices` | Best Practices | High |
| 2 | `github spring boot starter correlation id MDC request context` | Open Source | High |
| 3 | `github zalando logbook spring boot 3 features` | Tool Evaluation | High |
| 4 | `Spring Boot TaskDecorator MDC context propagation async` | Concurrency | Medium |

---

## 4. Current System Analysis

### 4.1 Related Features in Project

| Feature | Module/Package | Relevance | Notes |
|---------|---------------|-----------|-------|
| `RequestContextFilter` & `RequestContext` | `services/auth-service/shared/web` | High | Điểm xuất phát của ý tưởng; cần trích xuất và tinh gọn. |
| `AuditAspect` | `services/system-admin-service/audit/application` | High | Nơi đang duplicate bóc tách IP & userPrincipal. |
| `ResourceJwtAuthFilter` | `services/account-service/shared/security` | Medium | Đang validate JWT nhưng chưa bridge sang RequestContext. |
| `VirtualThreadMdcFilter` | `components/base-core/common-log/filter` | High | Bộ lọc HIGHEST_PRECEDENCE dọn dẹp MDC trên Virtual Threads. |
| `IpInfoUtil` | `components/base-core/common-log/utils` | High | Utility bóc tách IP đa proxy (`X-Forwarded-For`, etc.). |
| `KafkaTracingProducerInterceptor` | `components/base-core/starters/base-messaging-starter` | Medium | Đã có sẵn logic gắn trace ID vào Kafka Header. |
| `ExternalServiceContext` | `components/base-core/starters/base-http-client-starter` | Medium | Context khi gọi HTTP client ra ngoài. |

### 4.2 Existing Code Patterns
- Clean Architecture / Hexagonal Architecture: domain tách biệt infrastructure.
- Multi-module Spring Boot Starter pattern (`ntt.starter-conventions`).
- `OncePerRequestFilter` kết hợp `@Order` rõ ràng.
- `RequestContextHolder` pattern (hiện có `RequestAttributeContext.kt` thô sơ trong `base-core`).

### 4.3 Tech Stack Constraints
- **Language**: Kotlin 2.x, Java 21
- **Framework**: Spring Boot 3.x
- **Concurrency**: Virtual Threads (`spring.threads.virtual.enabled=true`)
- **Logging**: SLF4J + Logback + MDC structured JSON
- **Build tool**: Gradle 8.x Multi-module

### 4.4 Integration Points

| Integration Point | Type | Module/File | Notes |
|-------------------|------|-------------|-------|
| `WebAutoConfiguration` | Auto-configuration | `starters/base-web-starter` | Tự động đăng ký `BaseRequestContextFilter`. |
| `SecurityAutoConfiguration` | Auto-configuration | `starters/base-security-starter` | Tự động đăng ký `SecurityContextBridgeFilter`. |
| `HttpClientAutoConfiguration` | Auto-configuration | `starters/base-http-client-starter` | Đăng ký interceptor truyền correlation header. |
| `AuditLogService` / `AuditAspect` | Consumer | `auth-service` / `system-admin-service` | Sử dụng `RequestContextHolder` thay vì tự bóc tách. |

---

## 5. Research Questions

### 5.1 Câu hỏi cần trả lời
- [x] **Q1**: Tại sao không nên dùng `@RequestScope` đơn độc? (Đã làm rõ: gây `ScopeNotActiveException` ngoài web request).
- [x] **Q2**: Làm thế nào để lan truyền MDC và Context sang Virtual Threads và Async Threads an toàn? (Dùng `TaskDecorator` và `ContextSnapshot`).
- [x] **Q3**: Chuẩn hóa danh sách Header và MDC Keys là gì? (`X-Correlation-ID`, `X-Request-ID`, `X-Forwarded-For`, `User-Agent`, `X-Device-Fingerprint`, `X-App-Version`, `X-Client-Platform`).
- [x] **Q4**: Có nên dùng thư viện bên ngoài (Zalando Logbook, Micrometer Tracing) hay tự build starter gọn nhẹ trong `base-core`?

### 5.2 Assumptions cần verify
- [x] **A1**: `IpInfoUtil` trong `common-log` xử lý chính xác IPv4, IPv6, localhost và proxy chains.
- [x] **A2**: Virtual Threads trong Spring Boot 3 chia sẻ `ThreadLocal` an toàn trong suốt 1 request nếu dọn sạch ở `finally`.

---

## 6. Success Criteria

| Tiêu chí | Định nghĩa | Measurement |
|----------|-----------|-------------|
| Research coverage | Khảo sát đầy đủ cả giải pháp nội bộ, mã nguồn mở và best practices quốc tế | ≥ 5 nguồn uy tín |
| Open source options | Đánh giá so sánh các framework phổ biến (Logbook, Micrometer Tracing, Sleuth patterns) | ≥ 3 dự án được score |
| Gap analysis | Xác định rõ ưu/nhược điểm từng giải pháp đối với kiến trúc hiện tại | Hoàn thành bảng phân tích |
| Business analysis | Bóc tách đầy đủ các Use Case từ Inbound, MDC, Security đến Outbound | ≥ 5 Use Cases chi tiết |
| Technical spec | Cung cấp sơ đồ kiến trúc, sequence diagram, data model và filter ordering hoàn chỉnh | Đạt chuẩn triển khai |

---

> **Next step**: Phase 2 (Open Source Discovery) + Phase 3 (Internet Research)
