---
type: template
name: opensource_findings
version: "1.0"
language: vi
description: Kết quả tìm kiếm và đánh giá open source — scoring matrix + gap analysis chi tiết
---

# Kết quả tìm kiếm Open Source: shared-request-context

> Đánh giá các dự án open source và thư viện tiêu chuẩn giải quyết bài toán Request Context, Correlation ID, Tracing và MDC Propagation trong Spring Boot 3 & Microservices.

---

## 1. Tổng quan tìm kiếm

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | `shared-request-context` (Request Context, Tracing, MDC Propagation) |
| **Ngày tìm kiếm** | 2026-09-28 |
| **Số dự án tìm thấy** | 6 |
| **Số dự án đánh giá chi tiết** | 4 |
| **Tech stack mục tiêu** | Spring Boot 3.x, Kotlin 2.x, Java 21, Virtual Threads (Loom), Gradle multi-module |

### Chiến lược tìm kiếm

| # | Query | Kết quả | Ghi chú |
|---|-------|---------|---------|
| 1 | `"Spring Boot 3" correlation id filter MDC virtual threads best practices` | Chuẩn hóa Spring Boot 3, Micrometer Tracing, TaskDecorator | Khuyến nghị dùng Context Propagation |
| 2 | `github spring boot starter correlation id MDC request context` | Nhiều custom starter, spring-webflux-mdc | Đa số các custom starter thiếu Loom safety |
| 3 | `github zalando logbook spring boot 3 features` | Logbook 3.x cho Spring Boot 3, audit log HTTP | Rất mạnh về HTTP payload logging |
| 4 | `github alibaba transmittable-thread-local virtual threads` | TransmittableThreadLocal (TTL) | Rất mạnh với thread pool truyền thống, cần lưu ý với Virtual Threads |

---

## 2. Danh sách dự án tìm thấy

| # | Tên dự án | URL | Stars | Last Commit | License | Đánh giá? |
|---|----------|-----|-------|-------------|---------|-----------|
| 1 | **Micrometer Tracing & Context Propagation** | [micrometer-metrics/tracing](https://github.com/micrometer-metrics/tracing) | ~1.5k | Active (2026) | Apache 2.0 | ✅ Có (Tiêu chuẩn Spring Boot 3) |
| 2 | **Zalando Logbook** | [zalando/logbook](https://github.com/zalando/logbook) | ~2.5k | Active (2026) | Apache 2.0 | ✅ Có (Logging & Correlation) |
| 3 | **Alibaba TransmittableThreadLocal (TTL)** | [alibaba/transmittable-thread-local](https://github.com/alibaba/transmittable-thread-local) | ~14k | Active (2026) | Apache 2.0 | ✅ Có (Async ThreadLocal) |
| 4 | **spring-webflux-mdc** | [vincenzoracca/spring-webflux-mdc](https://github.com/vincenzoracca/spring-webflux-mdc) | ~200 | Active (2026) | MIT | ✅ Có (Reactive MDC) |

---

## 3. Bảng đánh giá (Scoring Matrix)

### Tiêu chí đánh giá

| Tiêu chí | Trọng số | 1-3 (Low) | 4-6 (Med) | 7-10 (High) |
|----------|----------|-----------|-----------|-------------|
| **Feature completeness** | 20% | Thiếu core feature | Đủ cơ bản | Đầy đủ metadata, tracing, context, MDC |
| **Applicability** | 15% | Khác stack | Cần chỉnh sửa nhiều | Tương thích hoàn hảo Spring Boot 3, Kotlin, Loom |
| **Activity** | 15% | Không update > 6 tháng | Update hàng tháng | Update hàng tuần/thường xuyên |
| **Documentation** | 15% | Không có docs | Chỉ có README | Tài liệu đầy đủ, use case rõ ràng |
| **Code quality** | 15% | Ít test, rác code | Test cơ bản | Test coverage cao, clean architecture |
| **Community** | 10% | < 100 stars | 100-1000 stars | > 1000 stars |
| **Popularity** | 10% | Ít người dùng | Đang tăng trưởng | Tiêu chuẩn ngành, hàng triệu downloads |

### Kết quả đánh giá

| Dự án | Feature (20%) | Applicability (15%) | Activity (15%) | Docs (15%) | Code (15%) | Community (10%) | Popularity (10%) | **Tổng điểm** |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Micrometer Tracing** | 9 | 10 | 10 | 9 | 10 | 9 | 10 | **9.55 / 10** |
| **Zalando Logbook** | 8 | 8 | 8 | 9 | 9 | 9 | 9 | **8.55 / 10** |
| **Alibaba TTL** | 8 | 7 | 8 | 8 | 9 | 10 | 10 | **8.40 / 10** |
| **spring-webflux-mdc** | 6 | 7 | 7 | 7 | 8 | 5 | 5 | **6.65 / 10** |

---

## 4. Gap Analysis chi tiết từng dự án

### 4.1. Micrometer Tracing & Context Propagation — Gap Analysis
**Overall Score**: 9.55 / 10

| Khía cạnh | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|---|:---:|:---:|---|---|
| Distributed Tracing | ✅ | | Tự động sinh traceId, spanId, tích hợp OTel/Brave | Hơi nặng nếu chỉ cần đơn giản `correlationId` |
| Context Propagation | ✅ | | Có `ContextSnapshot`, wrap được Executor cho Virtual Threads | API hơi phức tạp cho developer mới |
| Spring Boot 3 Native | ✅ | | Là bộ phận lõi chính thức của Spring Boot 3 | Không tự động bóc tách Client IP, Device Fingerprint |
| User Identity Bridge | | ❌ | - | Không tự động đọc JWT claims thành UserContext |
| Custom Headers Tracking | ⚠️ | | Cấu hình được qua observation predicates | Cần cấu hình thủ công |

**Verdict**: Rất tốt cho distributed tracing, nhưng **vẫn cần một lớp nhẹ trên cùng (`base-core`)** để quản lý các domain metadata (User, Device, IP).  
**Recommendation**: Tích hợp nguyên lý `ContextSnapshot` của Micrometer vào `ContextTaskDecorator` của `base-core`.

---

### 4.2. Zalando Logbook — Gap Analysis
**Overall Score**: 8.55 / 10

| Khía cạnh | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|---|:---:|:---:|---|---|
| HTTP Request/Response Logging | ✅ | | Format JSON đẹp, tự động log header, body, status | Sinh thêm nhiều log lines, tốn dung lượng I/O |
| Correlation ID Generation | ✅ | | Có sẵn correlation ID cho từng request/response | Chỉ phục vụ log, không có `RequestContext` bean cho business logic |
| Sensitive Data Masking | ✅ | | Hỗ trợ regex và header masking mạnh mẽ | Trùng lặp với `SensitiveDataMasker` đã có trong `common-log` |
| Virtual Threads | ✅ | | Tương thích tốt với Servlet MVC | Cần cấu hình khéo léo để tránh buffer body quá lớn |

**Verdict**: Phù hợp cho Audit HTTP logs chi tiết, nhưng quá cồng kềnh nếu chỉ cần Context cho Business Logic.  
**Recommendation**: Tham khảo pattern Correlation Header extraction và Response Header injection của Logbook; không cần kéo thêm thư viện bên ngoài vì `common-log` đã có `HttpLoggingFilter`.

---

### 4.3. Alibaba TransmittableThreadLocal (TTL) — Gap Analysis
**Overall Score**: 8.40 / 10

| Khía cạnh | Có (✅) | Thiếu (❌) | Điểm mạnh | Điểm yếu |
|---|:---:|:---:|---|---|
| Async Thread Transfer | ✅ | | Copy context qua ThreadPoolExecutor và ForkJoinPool cực mượt | Dựa trên `InheritableThreadLocal` sửa đổi |
| Java Agent Support | ✅ | | Trong suốt với code ứng dụng (không cần sửa code runnables) | **Nguy cơ rò rỉ bộ nhớ trên Virtual Threads** do carrier thread reuse |
| Maturity | ✅ | | Được Alibaba kiểm chứng với quy mô siêu lớn | Không phù hợp với mô hình luồng nhẹ của Loom |

**Verdict**: Rất mạnh cho Java 8/11/17 với thread pool truyền thống, nhưng **không khuyến nghị** cho Java 21 Virtual Threads.  
**Recommendation**: Sử dụng `ThreadLocal` chuẩn + `TaskDecorator` tường minh thay vì TTL để đảm bảo Virtual Thread safety.

---

## 5. Kết Luận & Định Hướng Triển Khai cho `base-core`

Từ việc đánh giá 4 giải pháp open source hàng đầu:
1. **Không có thư viện nào có sẵn 100% khớp với nhu cầu của dự án**:
   - Các thư viện tracing (Micrometer, Sleuth) chỉ lo `traceId`/`spanId`, không lo `deviceFingerprint`, `appVersion`, `locale`, hay `UserContext` phân quyền.
   - Các thư viện logging (Logbook) chỉ lo log HTTP in/out, không cung cấp bean Request Context cho Business layer hay AOP audit.
2. **Giải pháp tối ưu nhất**:
   - **Tự phát triển starter gọn nhẹ (Lightweight Internal Starter) trong `base-core`**:
     - Lấy cảm hứng từ cơ chế `ContextSnapshot` của Micrometer và `OncePerRequestFilter` chuẩn Spring.
     - Tái sử dụng `IpInfoUtil` và `VirtualThreadMdcFilter` đã có trong `common-log`.
     - Phân tầng sạch: Web Metadata (`base-web-starter`) -> Security Identity (`base-security-starter`) -> Outbound Propagation (`base-http-client-starter`).
