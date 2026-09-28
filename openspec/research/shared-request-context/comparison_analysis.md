---
type: template
name: comparison_analysis
version: "1.0"
language: vi
description: Tổng hợp so sánh — comparison matrix, feature matrix, gap synthesis, recommendation
---

# Phân tích so sánh: shared-request-context

> Tổng hợp kết quả từ Open Source Discovery + Internet Research + Current System Analysis → Lựa chọn phương án kiến trúc tối ưu cho `base-core`.

---

## 1. Tóm tắt (Executive Summary)

| Mục | Nội dung |
|-----|----------|
| **Tính năng** | `shared-request-context` (Unified Request Context & Distributed Observability) |
| **Ngày phân tích** | 2026-09-28 |
| **Recommendation** | **Hybrid Approach (In-house Decoupled Starter in `base-core` leveraging Spring Boot 3 standards)** |
| **Rationale** | Xây dựng bộ starter phân tầng trong `base-core` (`base-web-starter` cho HTTP/MDC metadata, `base-security-starter` cho User identity, `base-http-client-starter` cho Outbound tracing), tận dụng các utility đã có sẵn trong `common-log` (`IpInfoUtil`, `VirtualThreadMdcFilter`) và tuân thủ các chuẩn mực Java 21 Virtual Threads. |
| **Confidence** | **HIGH** — Loại bỏ 100% trùng lặp code, bảo vệ chống rò rỉ bộ nhớ trên Virtual Threads, tương thích 100% với Clean Architecture. |

---

## 2. Ma trận so sánh (Comparison Matrix)

| # | Giải pháp | Loại | Cách giải quyết bài toán | Ưu điểm | Nhược điểm | Phù hợp project? | Score |
|---|----------|------|--------------------------|---------|------------|:---:|:---:|
| 1 | **Micrometer Tracing** | Open Source | Dùng W3C traceparent / B3 context propagation qua OTel bridge | Chuẩn Spring Boot 3, tracing đa service rất mạnh | Không tự động bóc tách Client IP, Device Fingerprint, User Identity domain | ⚠️ Bán phần | 8.5/10 |
| 2 | **Zalando Logbook** | Open Source | Filter logging toàn diện HTTP request/response và correlation | Tự động log body, header, mã hóa dữ liệu nhạy cảm | Quá cồng kềnh, I/O nặng, không cung cấp domain `RequestContext` bean | ⚠️ Bán phần | 7.5/10 |
| 3 | **Lift & Shift từ `auth-service`** | Internal | Copy nguyên xi `RequestContext` & `RequestContextFilter` sang `base-web-starter` | Triển khai nhanh nhất, ít phải viết lại | Dính chặt domain auth (`anonymousTokenJti`), lỗi `ScopeNotActiveException` ngoài web request | ❌ Không | 5.0/10 |
| 4 | **Hybrid Decoupled Starter (Đề xuất)** | In-house Core | Tách tầng Web Transport Context (`base-web`) và Security Identity Context (`base-security`) với Loom-safe Holder | Gọn nhẹ, an toàn trên Virtual Threads, tái sử dụng `common-log`, hỗ trợ cả Inbound và Outbound | Cần điều phối cập nhật qua 3-4 module con trong `base-core` | ✅ **Rất phù hợp** | **9.8/10** |

---

## 3. So sánh theo tính năng (Feature Matrix)

| Feature | Micrometer Tracing | Zalando Logbook | Lift & Shift | Hybrid Starter (base-core) | Cần cho project? |
|---------|:---:|:---:|:---:|:---:|:---:|
| Correlation ID Inbound & Generation | ✅ | ✅ | ✅ | ✅ | ⭐ Must |
| Client IP extraction (X-Forwarded-For, multi-proxy) | ❌ | ❌ | ⚠️ | ✅ (qua `IpInfoUtil`) | ⭐ Must |
| Device Fingerprint & Client Platform metadata | ❌ | ❌ | ✅ | ✅ | ⭐ Must |
| SLF4J MDC Binding & Deterministic Cleanup | ⚠️ (chỉ trace/span) | ⚠️ | ✅ | ✅ (5 MDC keys) | ⭐ Must |
| User Identity Bridge (JWT Claims / SecurityContext) | ❌ | ❌ | ⚠️ (cứng) | ✅ (Decoupled UserContext) | ⭐ Must |
| Virtual Threads (Java 21 Loom) Safety | ✅ | ⚠️ | ❌ (Scope proxy) | ✅ (ThreadLocal chuẩn + dọn rác) | ⭐ Must |
| Non-Web Safe (Kafka listener / Scheduled tasks) | ⚠️ | ❌ | ❌ (Crash) | ✅ (Safe fallback) | ⭐ Must |
| Outbound HTTP / Kafka Context Propagation | ✅ | ⚠️ | ❌ | ✅ | ⭐ Must |
| Zero External Heavy Dependencies | ❌ (kéo OTel) | ❌ | ✅ | ✅ | ⭐ Must |
| **Coverage** | **4/9** | **2/9** | **5/9** | **9/9 (100%)** | |

---

## 4. Gap Synthesis (Tổng Hợp Khoảng Trống)

1. **Khoảng trống của giải pháp hiện tại (`auth-service`)**:
   - Thiếu Outbound Propagation khi gọi sang service khác.
   - Bị lệ thuộc cứng vào `@RequestScope` bean, dẫn đến việc các tác vụ nền hoặc AOP trong môi trường non-web bị crash.
2. **Khoảng trống của thư viện bên ngoài**:
   - Thư viện quốc tế tập trung vào distributed tracing trừu tượng (`traceId`, `spanId`) hoặc HTTP body logging thuần túy.
   - Hoàn toàn thiếu context nghiệp vụ cụ thể của hệ thống ngân hàng / tài chính / thương mại điện tử: `clientPlatform` (IOS/ANDROID/WEB), `deviceFingerprint` (chống gian lận), `appVersion` (quản lý phiên bản bắt buộc nâng cấp), và `tenantId`.

---

## 5. Đề Xuất Chiến Lược (Strategic Recommendation)

Chọn **Phương án 4: Hybrid Decoupled Starter trong `base-core`**.

### Chiến lược triển khai 3 trụ cột:
1. **Trụ cột 1: Domain-Driven Context Model**:
   - Đặt `RequestContext`, `UserContext`, `RequestContextHolder` tại `base-core` (hoặc `base-model`).
   - Cung cấp POJO gọn nhẹ, không lệ thuộc Spring Web hay Spring Security ở tầng model.
2. **Trụ cột 2: Tách biệt Web Filter và Security Bridge**:
   - `base-web-starter`: Chịu trách nhiệm bóc tách transport metadata, gán `RequestContextHolder`, nạp MDC. Chạy trước Security.
   - `base-security-starter`: Chịu trách nhiệm trích xuất `Authentication` sau khi JWT filter chạy xong, gán vào `RequestContext.user`.
3. **Trụ cột 3: Hoàn thiện vòng lặp Observability**:
   - Tích hợp `ContextTaskDecorator` cho `@Async` / Virtual Thread executors.
   - Tích hợp `RequestContextClientInterceptor` cho `base-http-client-starter`.
   - Kết nối với `KafkaTracingProducerInterceptor` trong `base-messaging-starter`.
