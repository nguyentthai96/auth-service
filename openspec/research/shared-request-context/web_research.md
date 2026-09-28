---
type: template
name: web_research
version: "1.0"
language: vi
description: Kết quả tìm kiếm internet — Perplexity-style iterative search + product evaluation
---

# Kết quả nghiên cứu Internet: shared-request-context

> Kết quả tìm kiếm internet theo phương pháp Perplexity-style iterative search — phân tích kinh nghiệm thực chiến từ cộng đồng Spring Boot và Microservices quốc tế.

---

## 1. Chiến lược tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | `shared-request-context` (Request Context, Tracing, MDC Propagation) |
| **Ngày nghiên cứu** | 2026-09-28 |
| **Số iterations** | 3 iterations (Broad → Deep Dive → Targeted) |
| **Tổng sources** | 6 unique sources (Spring.io, Baeldung, Medium, Dev.to, GitHub, StackOverflow) |
| **Keywords ban đầu** | `Spring Boot 3 correlation id filter`, `MDC virtual threads`, `RequestContext` |
| **Keywords phát triển** | `Micrometer ContextPropagation`, `TaskDecorator`, `Carrier Thread Contamination`, `Token Relay Header` |

---

## 2. Kết quả theo Iteration

### Iteration 1 — BROAD (Tổng quan Hệ sinh thái Spring Boot 3)

**Mục tiêu**: Nắm bắt các thay đổi cốt lõi giữa Spring Boot 2 (Sleuth) và Spring Boot 3 (Micrometer Tracing) liên quan đến Context và Logging.

| # | Query | Kết quả quan trọng | Keywords mới |
|---|-------|-------------------|-------------|
| 1 | `"Spring Boot 3" correlation id filter MDC virtual threads best practices` | Spring Boot 3 thay thế Spring Cloud Sleuth bằng Micrometer Tracing; Virtual Threads vẫn hỗ trợ ThreadLocal nhưng gặp lỗi khi bàn giao sang async thread. | `Micrometer Tracing`, `ContextSnapshot`, `VirtualThreadPerTaskExecutor` |
| 2 | `github spring boot starter correlation id MDC request context` | Các custom starter phổ biến dùng `OncePerRequestFilter` kết hợp `MDC.put()` và bắt buộc phải `MDC.clear()` trong `finally`. | `OncePerRequestFilter`, `TaskDecorator`, `ScopeNotActiveException` |
| 3 | `Spring Boot TaskDecorator MDC context propagation async` | `@Async` và ThreadPool không tự copy MDC; cần đăng ký `TaskDecorator` trong cấu hình `ThreadPoolTaskExecutor`. | `ThreadPoolTaskExecutor.setTaskDecorator`, `MdcTaskDecorator` |

**Takeaways Iteration 1:**
- Trên Spring Boot 3 + Java 21, `ThreadLocal` hoạt động bình thường trên từng Virtual Thread riêng lẻ cho 1 request HTTP.
- Cạm bẫy lớn nhất là rò rỉ hoặc mất mát context khi chuyển giao giữa Web Servlet Thread và Worker Thread (`@Async`, Kafka, Coroutines).

---

### Iteration 2 — DEEP DIVE (Khảo sát Chuyên sâu về Virtual Threads & Context Loss)

**Mục tiêu**: Đi sâu vào các tài liệu kỹ thuật về Virtual Threads và Context Propagation trong Spring Boot 3.

| # | URL | Title | Key Insights | Relevance (1-10) |
|---|-----|-------|-------------|:---:|
| 1 | [spring.io/blog/observability-with-spring-boot-3](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3) | Observability with Spring Boot 3 | Micrometer Tracing tự động inject `traceId`/`spanId` vào MDC và HTTP Client Headers qua builder. | 9/10 |
| 2 | [fati.dev/spring-boot-correlation-id-logging](https://fati.dev/posts/spring-boot-correlation-id-logging/) | Distributed Tracing with Correlation IDs in Spring Boot | Hướng dẫn triển khai Correlation Filter đọc `X-Correlation-ID` hoặc fallback sang `UUID.randomUUID()`. | 8/10 |
| 3 | [github.com/zalando/logbook](https://github.com/zalando/logbook) | Zalando Logbook: HTTP logging made easy | Kiến trúc bóc tách filter đọc payload, masking dữ liệu nhạy cảm và gán ID tương quan. | 8/10 |
| 4 | [dev.to/spring-virtual-threads-mdc-leak](https://dev.to/spring-virtual-threads-mdc-leak) | Virtual Threads and MDC Leaks in Spring | Giải thích nguy cơ ô nhiễm Carrier Thread nếu dùng `InheritableThreadLocal` hoặc thiếu `MDC.clear()` ở filter ngoài cùng. | 10/10 |

**Takeaways Iteration 2:**
- **Không bao giờ dùng `InheritableThreadLocal` với Virtual Threads**: Carrier threads (luồng nền tảng do JVM quản lý) được tái sử dụng liên tục. Nếu dùng `InheritableThreadLocal`, giá trị của virtual thread cũ sẽ bị "nhiễm" sang virtual thread mới chạy trên cùng carrier thread.
- **Quy tắc Vàng cho Filter ngoài cùng**: Phải có một Filter nằm ở `HIGHEST_PRECEDENCE` làm nhiệm vụ dọn sạch MDC trước khi filter chain bắt đầu (`VirtualThreadMdcFilter` trong `common-log` của dự án đã làm đúng điều này).

---

### Iteration 3 — TARGETED (Lan truyền Context Đa Dịch Vụ & Non-Web Fallback)

**Mục tiêu**: Giải quyết bài toán lan truyền ra ngoài (Outbound Propagation) và xử lý an toàn khi không có HTTP request.

| # | Chủ đề | Nghiên cứu & Giải pháp | Đánh giá |
|---|--------|------------------------|----------|
| 1 | **Outbound RestClient / WebClient** | Sử dụng `ClientHttpRequestInterceptor` của Spring 6. Thêm interceptor đọc từ `RequestContextHolder` và gắn `X-Correlation-ID: <id>` vào header ra ngoài. | Cực kỳ gọn nhẹ, không phụ thuộc framework nặng. |
| 2 | **Outbound Kafka Events** | Sử dụng `ProducerInterceptor<K, V>` của Apache Kafka. Lấy `correlationId` từ `RequestContextHolder` gắn vào `record.headers().add("X-Correlation-ID", ...)`. | Đã có sẵn nền tảng trong `base-messaging-starter`. |
| 3 | **Non-Web Context (Kafka Consumer / Batch / Cron)** | Nếu code dùng `@RequestScope`, nó sẽ crash khi chạy trong `@KafkaListener` hoặc `@Scheduled`. Giải pháp: Dùng `RequestContextHolder` với cơ chế `RequestContext.systemFallback(jobName)`. | Tránh triệt để `ScopeNotActiveException`. |

---

## 3. Tổng Hợp Best Practices & Anti-Patterns Từ Cộng Đồng

### 3.1. Best Practices (Khuyến nghị áp dụng)
1. **Single Source of Truth**: Cung cấp 1 bean/holder duy nhất (`RequestContext`) chứa cả Transport Metadata và User Identity.
2. **Deterministic Cleanup**: Đặt `MDC.clear()` và `RequestContextHolder.clear()` trong khối `finally` của filter Servlet.
3. **TaskDecorator for Async**: Luôn cấu hình `TaskDecorator` cho Spring `ThreadPoolTaskExecutor` để truyền MDC và context sang các tác vụ bất đồng bộ.
4. **Header Normalization**: Thống nhất header tiêu chuẩn `X-Correlation-ID` (ưu tiên hơn `X-Request-ID`), chấp nhận cả hai nếu client gửi lên.

### 3.2. Anti-Patterns (Cần tránh tuyệt đối)
- ❌ **Anti-pattern 1: Dùng `@RequestScope` cho các service được chia sẻ với background/queue jobs**.
- ❌ **Anti-pattern 2: Dùng `InheritableThreadLocal` trên môi trường Virtual Threads (JDK 21)**.
- ❌ **Anti-pattern 3: Bỏ quên Outbound Propagation** (khiến trace log bị đứt đoạn giữa các microservice).
- ❌ **Anti-pattern 4: Bê domain logic của Auth Provider vào Web Core**.
