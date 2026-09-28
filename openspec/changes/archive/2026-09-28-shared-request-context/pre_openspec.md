# Pre-OpenSpec: shared-request-context

> **Type**: EXTEND
> **Flow**: Non-Financial
> **Source**: URD (Feature Research: `openspec/research/shared-request-context/business_analysis.md`)
> **Classification Evidence**: `RequestContext` in `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt`, `WebAutoConfiguration.kt` in `components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/web/WebAutoConfiguration.kt`, `IpInfoUtil.kt` in `components/base-core/common-log/src/main/kotlin/com/ntt/commonlog/utils/IpInfoUtil.kt`
> **Archive**: N/A (Fresh Full Mode)
> **Quality Score**: 100/100

## 📋 Feature Summary

Chuẩn hóa và đưa thành phần `RequestContext`, `RequestContextFilter`, và cơ chế quản trị SLF4J MDC vào `base-core` để tái sử dụng xuyên suốt toàn bộ các microservices (`auth-service`, `account-service`, `system-admin-service`, `notification-service`). Kiến trúc phân tầng bóc tách độc lập giữa *Web Transport Context* (không phụ thuộc Security) và *Security Identity Context* (tích hợp Spring Security / JWT), bảo đảm an toàn tuyệt đối trên Java 21 Virtual Threads (Loom), hỗ trợ lan truyền ngữ cảnh hai chiều (Inbound & Outbound), và cung cấp fallback an toàn cho các tác vụ nền phi Web (Kafka / Scheduled).

| Metric | Giá trị |
|--------|---------|
| Số FR | 12 (URD: 10, Enriched: 2) |
| Issues | 1 (🔴: 0, 🟡: 1, 🟢: 0) |
| Open Questions | 0 (Đã chốt) |
| **Quality Score** | **100/100** |

---

## 1. Actors

- **Client App / API Gateway**: Gửi request kèm các headers định danh thiết bị, IP kết nối và Correlation ID (nếu có).
- **Microservice Developer**: Truy xuất ngữ cảnh người dùng, IP, thiết bị, locale qua `RequestContextHolder` trong Controller, Business Service, hoặc AOP Aspect.
- **Spring Security Engine**: Xác thực token JWT RS256 và cung cấp đối tượng `Authentication`.
- **Async & Virtual Thread Pool**: Tiếp nhận các tác vụ bất đồng bộ (`@Async`, Virtual Threads) và kế thừa ngữ cảnh qua `ContextTaskDecorator`.
- **Downstream Services / Kafka**: Tiếp nhận các cuộc gọi HTTP ra ngoài hoặc sự kiện Kafka có đính kèm Correlation ID.

---

## 2. Functional Requirements

### FR-001: Trích xuất Correlation ID và Request ID từ Inbound HTTP [URD]
- **Actor**: Client App / API Gateway
- **Action**: Hệ thống phải trích xuất mã tương quan từ header `X-Correlation-ID`. Nếu không có, tìm kiếm `X-Request-ID`. Nếu cả hai đều thiếu, hệ thống phải tự động sinh mới một chuỗi UUID ngẫu nhiên. Đồng thời sinh `requestId` độc nhất cho chặng gọi hiện tại.
- **Validation**: `correlationId` và `requestId` không bao giờ được phép rỗng hoặc null.

### FR-002: Bóc tách địa chỉ IP máy khách qua chuỗi Proxy [URD]
- **Actor**: Client App / API Gateway
- **Action**: Hệ thống phải bóc tách địa chỉ IP nguồn thực tế của người dùng từ header `X-Forwarded-For` (lấy giá trị đầu tiên trong chuỗi phân tách bởi dấu phẩy). Nếu không có, fallback sang `request.getRemoteAddr()`. Chuẩn hóa địa chỉ IPv6 loopback `0:0:0:0:0:0:0:1` thành `127.0.0.1`.
- **Validation**: Tận dụng utility `IpInfoUtil` đã có trong `common-log`.

### FR-003: Thu thập thông tin định danh thiết bị và nền tảng [URD]
- **Actor**: Client App
- **Action**: Hệ thống phải đọc các headers metadata gồm: `User-Agent`, `X-Device-Fingerprint`, `X-App-Version`, `X-Client-Platform`, `X-Tenant-Id`.
- **Validation**: Nếu thiếu `X-App-Version` hoặc `X-Client-Platform`, gán giá trị mặc định là `"unknown"`.

### FR-004: Khởi tạo và quản trị vòng đời RequestContextHolder [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải khởi tạo đối tượng bất biến `RequestContext` và lưu trữ vào `RequestContextHolder` sử dụng `ThreadLocal` chuẩn (không dùng `InheritableThreadLocal`).
- **Validation**: Bắt buộc giải phóng `RequestContextHolder.clear()` trong khối `finally` của filter để chống rò rỉ bộ nhớ trên Virtual Threads.

### FR-005: Tự động nạp và dọn dẹp SLF4J MDC Logging [URD]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải nạp 4 khóa ban đầu vào SLF4J MDC (`correlationId`, `clientIp`, `appVersion`, `clientPlatform`). Dọn dẹp sạch sẽ toàn bộ MDC trong khối `finally`.
- **Validation**: Không để sót bất kỳ khóa nào sang chu kỳ tiếp theo của Thread Pool / Carrier Thread.

### FR-006: Cầu nối danh tính người dùng SecurityContextBridge [URD]
- **Actor**: Spring Security Engine
- **Action**: Hệ thống phải cung cấp `SecurityContextBridgeFilter` chạy sau Spring Security Filter Chain (`Ordered.LOWEST_PRECEDENCE - 20`), trích xuất thông tin người dùng (`userId`, `username`, `roles`, `permissions`, `jti`) từ `SecurityContextHolder`, ánh xạ vào `RequestContext.user` và nạp thêm khóa `userId` vào MDC.
- **Validation**: Nếu request chưa xác thực hoặc là anonymous, giữ `RequestContext.user = null`.

### FR-007: Lan truyền ngữ cảnh sang luồng bất đồng bộ và Virtual Threads [URD]
- **Actor**: Async & Virtual Thread Pool
- **Action**: Hệ thống phải cung cấp `ContextTaskDecorator` để sao chép (snapshot) `RequestContext` và `MDC` sang các luồng thực thi mới khi gọi `@Async` hoặc submit tác vụ vào Executor.
- **Validation**: Khi thread con kết thúc, tự động dọn dẹp context của thread con và khôi phục context trước đó nếu có.

### FR-008: Lan truyền mã tương quan qua Outbound HTTP Client [URD]
- **Actor**: Microservice Developer
- **Action**: Hệ thống phải cung cấp `RequestContextClientInterceptor` cho `RestClient` / `RestTemplate` trong `base-http-client-starter`, tự động đọc `correlationId` và `tenantId` từ `RequestContextHolder` để gắn vào header cuộc gọi ra ngoài.
- **Validation**: Không ghi đè nếu header `X-Correlation-ID` đã được caller thiết lập thủ công từ trước.

### FR-009: Cung cấp Fallback ngữ cảnh cho tác vụ phi Web [URD]
- **Actor**: Background Worker
- **Action**: Khi phương thức `RequestContextHolder.require()` được gọi ngoài luồng Web (ví dụ trong Kafka Listener `@KafkaListener` hoặc Scheduled Task `@Scheduled`), hệ thống phải trả về một instance fallback `RequestContext.fallback(source)` thay vì ném lỗi `ScopeNotActiveException`.
- **Validation**: Đảm bảo các AOP Aspect (như `AuditAspect`) không làm sập tác vụ nền.

### FR-010: Trả về mã tương quan trong HTTP Response Header [URD]
- **Actor**: Client App / API Gateway
- **Action**: Hệ thống phải tự động gắn header `X-Correlation-ID: <correlationId>` vào mọi HTTP response (bao gồm cả response lỗi 4xx/5xx).
- **Validation**: Header trả về phải trùng khớp chính xác với `correlationId` của request.

### FR-011: Tự động đính kèm mã tương quan vào sự kiện Kafka [ENRICHED]
- **Actor**: Background Worker
- **Action**: Hệ thống phải đảm bảo `KafkaTracingProducerInterceptor` trong `base-messaging-starter` tự động lấy `correlationId` từ `RequestContextHolder` và gắn vào Kafka Record Header.
- **Validation**: Định dạng header Kafka là byte array UTF-8.

### FR-012: Tự động cấu hình AutoConfiguration cho Spring Boot Starter [ENRICHED]
- **Actor**: Hệ thống
- **Action**: Hệ thống phải tự động kích hoạt `BaseRequestContextFilter` và `ContextTaskDecorator` khi phát hiện ứng dụng là Servlet Web (`@ConditionalOnWebApplication`), cho phép tắt mở qua cấu hình `app.web.request-context.enabled=true/false`.
- **Validation**: Hỗ trợ `@ConditionalOnMissingBean` để downstream microservices có thể override filter nếu cần.

---

## 3. Non-functional Requirements

- **Hiệu năng (Performance)**: Thao tác trích xuất và nạp context của filter phải hoàn thành dưới `0.05ms`, không gây nghẽn I/O.
- **Bảo mật (Security)**: Không log các thông tin nhạy cảm (Authorization token, password) vào MDC hoặc Header.
- **Tương thích (Compatibility)**: Tương thích 100% với Java 21 Virtual Threads (`spring.threads.virtual.enabled=true`).
- **Khả năng mở rộng (Extensibility)**: Cho phép downstream microservice mở rộng thêm các claims tùy biến thông qua thuộc tính `UserContext.attributes`.

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp. Toàn bộ 12 FRs đều độc lập, phân định ranh giới rõ ràng giữa Transport Metadata (FR-001 đến FR-005), Security Identity (FR-006), Concurrency (FR-007, FR-009), Outbound (FR-008, FR-011) và Framework Integration (FR-010, FR-012).

---

## 5. Enriched Domain Requirements

Giới hạn bổ sung domain: `min(5, ceil(10 × 0.20)) = 2` FRs.

### Enriched FRs
- **FR-011**: Tự động đính kèm mã tương quan vào sự kiện Kafka (Cần thiết cho hệ sinh thái Event-Driven trong `base-messaging-starter`).
- **FR-012**: Tự động cấu hình AutoConfiguration cho Spring Boot Starter (Tiêu chuẩn Spring Boot Starter để downstream services tự động nhận tính năng).

### External Integrations
| Hệ thống | Mục đích | Ghi chú |
|----------|----------|---------|
| **SLF4J MDC** | Lưu trữ metadata phục vụ Structured Logging | 5 keys: correlationId, clientIp, appVersion, clientPlatform, userId |
| **Kafka Broker** | Nhận và chuyển tiếp correlationId qua Record Headers | Tích hợp qua `KafkaTracingProducerInterceptor` |
| **Downstream HTTP Services** | Tiếp nhận cuộc gọi REST với `X-Correlation-ID` | Tích hợp qua `RequestContextClientInterceptor` |

---

## 6. Assumptions

- `A-01`: API Gateway ở cổng biên đã sanitize cơ bản header `X-Forwarded-For` để hạn chế giả mạo IP (IP Spoofing).
- `A-02`: Virtual Threads trong JVM 21 quản lý `ThreadLocal` độc lập cho từng Virtual Thread, việc rò rỉ chỉ xảy ra nếu sử dụng `InheritableThreadLocal`.

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|:---:|-----------|
| Rõ ràng (Clarity) | 25/25 | Không có |
| Đầy đủ (Completeness) | 25/25 | Đã chốt OQ-01: bổ sung cấu hình forward-auth-token |
| Nhất quán (Consistency) | 25/25 | Không có |
| Kiểm thử được (Testability) | 25/25 | Không có |
| **Tổng** | **100/100** | |

### Chi tiết trừ điểm
Không phát hiện điểm trừ nào. Toàn bộ câu hỏi mở đã được giải quyết.

---

## 8. Issues & Risks

| # | Loại | Mức độ | Mô tả | FR | Đề xuất |
|---|------|:---:|-------|-----|---------|
| 1 | Risk | 🟡 | Tránh lỗi rò rỉ carrier thread trên môi trường Virtual Threads nếu filter exception xảy ra trước `finally` | FR-004 | Sử dụng `VirtualThreadMdcFilter` ở `HIGHEST_PRECEDENCE` làm chốt chặn bảo vệ kép |

---

## 9. Open Questions

- **[RESOLVED] OQ-01**: Đối với các cuộc gọi nội bộ (Service-to-Service), ngoài `X-Correlation-ID`, downstream service có cần nhận thêm Bearer token gốc (Token Relay) hay chỉ cần `X-User-Id`?
  - **Quyết định (Approved by User)**: Mặc định chỉ forward `X-Correlation-ID` (và `X-Tenant-Id` nếu có) để giữ overhead nhẹ và an toàn; cung cấp cấu hình `app.http-client.forward-auth-token: false` (mặc định) cho phép bật Token Relay khi một service downstream thực sự cần.

---

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
Cross-Cutting Observability & Security Context (`base-core`)

### 10.2 Flow Type
Non-Financial (Infrastructure / Observability)

### 10.3 Candidate Services
- `components/base-core`: Chứa core context model, `base-web-starter`, `base-security-starter`, `base-http-client-starter`.
- `services/auth-service`: Refactor loại bỏ duplicate `RequestContextFilter` cục bộ, kế thừa starter chuẩn.
- `services/system-admin-service`: Refactor loại bỏ logic parse IP/User thủ công trong `AuditAspect`.
- `services/account-service`: Kích hoạt starter để tự động sở hữu Correlation ID và MDC logging.
- `services/notification-service`: Kích hoạt starter để đồng bộ tracing đa dịch vụ.

### Detection Evidence
- Keyword: `RequestContext` → Module: `auth-service/shared/web` → File: `RequestContext.kt`
- Keyword: `WebAutoConfiguration` → Module: `base-core/starters/base-web-starter` → File: `WebAutoConfiguration.kt`
- Keyword: `AuditAspect` → Module: `system-admin-service/audit/application` → File: `AuditAspect.kt`
- Keyword: `ResourceJwtAuthFilter` → Module: `account-service/shared/security` → File: `ResourceJwtAuthFilter.kt`
- Keyword: `IpInfoUtil` → Module: `base-core/common-log` → File: `IpInfoUtil.kt`

### 10.4 External Integrations
- SLF4J MDC Logging
- Apache Kafka Tracing Interceptor
- Spring MVC RestClient / RestTemplate Interceptors

### 10.5 Required Modules
- `components/base-core`
- `components/base-core/starters/base-web-starter`
- `components/base-core/starters/base-security-starter`
- `components/base-core/starters/base-http-client-starter`
- `components/base-core/common-log`

---

## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|:---:|-------|--------|--------|
| 1 | Client | Gửi HTTP Request kèm headers định danh | Inbound Filter Chain |
| 2 | System | Xóa sạch MDC rác còn sót lại trên luồng | `VirtualThreadMdcFilter` |
| 3 | System | Đọc `X-Correlation-ID`, bóc tách IP qua `IpInfoUtil`, tạo `RequestContext` | `BaseRequestContextFilter` |
| 4 | System | Nạp `correlationId`, `clientIp`, `appVersion`, `clientPlatform` vào MDC | SLF4J MDC |
| 5 | Security | Xác thực token JWT RS256 và set SecurityContext | Spring Security Filter |
| 6 | System | Ánh xạ `Authentication` sang `UserContext`, nạp `userId` vào MDC | `SecurityContextBridgeFilter` |
| 7 | Developer | Đọc dữ liệu từ `RequestContextHolder` để xử lý nghiệp vụ / audit log | Controller / Business Service |
| 8 | System | Forward `X-Correlation-ID` khi gọi HTTP sang service khác | `RequestContextClientInterceptor` |
| 9 | System | Echo `X-Correlation-ID` trong response header, dọn dẹp MDC & Holder trong `finally` | `BaseRequestContextFilter` |

---

## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|:---:|-------------|--------------|----------------|:---:|
| FR-001 | UC-001 | Section 4.1 | `BaseRequestContextFilter.kt` | Ready |
| FR-002 | UC-001 | Section 4.1 | `IpInfoUtil.kt`, `BaseRequestContextFilter.kt` | Ready |
| FR-003 | UC-001 | Section 4.1 | `BaseRequestContextFilter.kt` | Ready |
| FR-004 | UC-001 | Section 2.3 | `RequestContext.kt`, `RequestContextHolder.kt` | Ready |
| FR-005 | UC-002 | Section 4.1 | `BaseRequestContextFilter.kt` | Ready |
| FR-006 | UC-003 | Section 4.2 | `SecurityContextBridgeFilter.kt` | Ready |
| FR-007 | UC-005 | Section 4.3 | `ContextTaskDecorator.kt` | Ready |
| FR-008 | UC-006 | Section 4.4 | `RequestContextClientInterceptor.kt` | Ready |
| FR-009 | UC-007 | Section 2.1 | `RequestContext.kt` (`fallback`) | Ready |
| FR-010 | UC-001 | Section 4.1 | `BaseRequestContextFilter.kt` | Ready |
| FR-011 | UC-006 | Section 4.5 | `KafkaTracingProducerInterceptor.kt` | Ready |
| FR-012 | Scope | Section 1.3 | `RequestContextAutoConfiguration.kt` | Ready |

---

## 13. Agent Notes (Tổng Hợp Bổ Sung)

### Observations
- Đây là một cải tiến mang tính nền tảng (Foundation Enhancement) ở mức độ kiến trúc doanh nghiệp. Khi triển khai thành công vào `base-core`, tất cả các microservices hiện tại và tương lai sẽ ngay lập tức thừa hưởng khả năng truy vết phân tán (Distributed Tracing), kiểm toán an ninh (Audit Trail) và structured logging chuẩn JSON mà không cần viết lại bất kỳ dòng code boilerplate nào.

### Related Features / Precedents
- `controller-request-context` (đã thực hiện thử nghiệm trong `auth-service`): Cung cấp kinh nghiệm thực tế về việc tương tác với `AuditLogService` và các Controller.
- `VirtualThreadMdcFilter` trong `common-log`: Đã chứng minh hiệu quả dọn dẹp carrier thread rò rỉ trên Java 21.

### Suggested Approach
- Giữ `RequestContext` là POJO thuần túy trong `base-core` để tránh phụ thuộc vào framework.
- Kế thừa lại các hàm đã có trong `common-log/IpInfoUtil.kt`.
- Áp dụng migration theo 2 giai đoạn: Giai đoạn 1 đóng gói và publish starter trong `base-core`; Giai đoạn 2 refactor nhẹ nhàng tại `auth-service`, `system-admin-service`, và `account-service`.
