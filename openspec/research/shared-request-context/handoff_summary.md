---
type: template
name: handoff_summary
version: "1.0"
language: vi
description: Tóm tắt handoff — bridge document từ feature research sang downstream pipeline
---

# Research Handoff: shared-request-context

> Bridge document — tóm tắt toàn bộ kết quả nghiên cứu tính năng `shared-request-context` để chuyển giao trực tiếp sang pipeline triển khai (`/wf_openspec` hoặc `/wf_openspec_apply`).

---

## Metadata

| Mục | Nội dung |
|-----|----------|
| **Feature** | `shared-request-context` (Unified Request Context & Distributed Observability) |
| **Ngày hoàn thành** | 2026-09-28 |
| **Recommendation** | **Build In-house Decoupled Starter trong `base-core`** |
| **Research directory** | `openspec/research/shared-request-context/` |
| **Status** | complete |

---

## 1. Recommendation

Phát triển bộ Starter phân tầng gọn nhẹ trong `base-core` (`base-web-starter` quản lý Web transport & MDC metadata, `base-security-starter` làm cầu nối SecurityContext sang UserContext, `base-http-client-starter` forward correlation header). Giải pháp này loại bỏ 100% code trùng lặp, bảo đảm an toàn bộ nhớ trên Java 21 Virtual Threads, và không làm ô nhiễm `base-core` bởi các domain logic riêng của `auth-service`.

---

## 2. Key Findings

| Category | Finding | Source |
|----------|---------|-------|
| **Open Source** | Micrometer Tracing (9.55/10) và Zalando Logbook (8.55/10) rất mạnh về tracing/logging cơ bản, nhưng không đáp ứng được domain metadata nghiệp vụ (ClientPlatform, DeviceFingerprint, AppVersion, UserContext). | [opensource_findings.md](./opensource_findings.md) |
| **Web Research** | Tuyệt đối cấm sử dụng `InheritableThreadLocal` trên Virtual Threads vì gây ô nhiễm carrier thread; Bắt buộc phải có `TaskDecorator` cho `@Async` và Filter dọn rác ở `HIGHEST_PRECEDENCE`. | [web_research.md](./web_research.md) |
| **Gap Coverage** | Độ bao phủ tính năng đạt 100% (9/9 tiêu chí), khắc phục hoàn toàn nhược điểm crash `ScopeNotActiveException` ngoài web request của `@RequestScope`. | [comparison_analysis.md](./comparison_analysis.md) |
| **Current System** | Tận dụng sẵn `IpInfoUtil` và `VirtualThreadMdcFilter` trong `common-log` để không phải viết lại logic bóc tách IP đa proxy. | [research_brief.md](./research_brief.md) |

---

## 3. Use Cases Identified

| UC ID | Tên | Mô tả ngắn | Priority |
|-------|-----|------------|----------|
| **UC-001** | Trích xuất & Khởi tạo Request Context từ Inbound HTTP | Đọc headers `X-Correlation-ID`, IP client, Device info và khởi tạo `RequestContext` | ⭐ Must |
| **UC-002** | Nạp và Dọn dẹp Logging MDC | Đưa 5 khóa correlationId, clientIp, appVersion, clientPlatform, userId vào MDC | ⭐ Must |
| **UC-003** | Ánh xạ Danh Tính Người Dùng (SecurityContextBridge) | Tự động chuyển `Authentication` của Spring Security sang domain `UserContext` | ⭐ Must |
| **UC-004** | Truy xuất Ngữ Cảnh trong Tầng Nghiệp Vụ & Audit | Cho phép Controller và AuditAspect truy xuất nhanh qua `RequestContextHolder` | ⭐ Must |
| **UC-005** | Lan truyền Ngữ Cảnh sang Async & Virtual Threads | Tự động copy RequestContext và MDC khi chạy `@Async` qua `ContextTaskDecorator` | ⭐ Must |
| **UC-006** | Lan truyền Ngữ Cảnh Đa Dịch Vụ (Outbound) | Tự động gắn `X-Correlation-ID` khi gọi HTTP client hoặc bắn message Kafka | ⭐ Must |
| **UC-007** | Khởi tạo Ngữ Cảnh An Toàn ngoài Web Request | Cung cấp fallback an toàn cho Kafka Consumers và Scheduled tasks | ⭐ Must |

---

## 4. Technical Highlights

| Aspect | Decision / Finding |
|--------|--------------------|
| **Architecture** | Phân tầng bóc tách: Web Transport (`base-web`) ⟂ Security Identity (`base-security`) ⟂ Outbound (`base-http-client`). |
| **Data Model** | `RequestContext` (Transport metadata) + `UserContext` (Identity) + `RequestContextHolder` (ThreadLocal chuẩn). |
| **Filter Pipeline** | `VirtualThreadMdcFilter` (HIGHEST) → `BaseRequestContextFilter` (HIGHEST + 10) → `JWT Filter` → `SecurityContextBridgeFilter` (LOWEST - 20). |
| **MDC Keys** | `correlationId`, `clientIp`, `appVersion`, `clientPlatform`, `userId`. |
| **Loom Safety** | Dùng `ThreadLocal` chuẩn + dọn rác nghiêm ngặt trong `finally` block + cấm `InheritableThreadLocal`. |

---

## 5. Artifact Inventory (Danh Mục Tài Liệu)

| Artifact | File Path | Mô tả |
|----------|-----------|-------|
| 1. Research Brief | [`research_brief.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/shared-request-context/research_brief.md) | Mục tiêu, phạm vi và khảo sát hiện trạng hệ thống |
| 2. Open Source Findings | [`opensource_findings.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/shared-request-context/opensource_findings.md) | Đánh giá 4 giải pháp mã nguồn mở & scoring matrix |
| 3. Web Research | [`web_research.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/shared-request-context/web_research.md) | Kinh nghiệm thực chiến, pitfalls và best practices quốc tế |
| 4. Comparison Analysis | [`comparison_analysis.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/shared-request-context/comparison_analysis.md) | Ma trận so sánh tính năng và luận giải khuyến nghị |
| 5. Business Analysis | [`business_analysis.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/shared-request-context/business_analysis.md) | 7 Use Cases, 8 Business Rules, Ma trận truy vết |
| 6. Technical Spec | [`technical_spec.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/shared-request-context/technical_spec.md) | Sơ đồ Mermaid, Kotlin models, Filter code, Testing plan |
| 7. Validation Report | [`validation_report.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/shared-request-context/validation_report.md) | Báo cáo kiểm định chất lượng (100% PASS) |
| 8. Handoff Summary | [`handoff_summary.md`](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/shared-request-context/handoff_summary.md) | Bản tóm tắt chuyển giao sang OpenSpec pipeline |

---

## 6. Khuyến Nghị Cho Pipeline Tiếp Theo

- **Phương án 1 (Khuyến nghị)**: Tiến hành chạy `/wf_openspec shared-request-context` để sinh trực tiếp bộ artifact đặc tả triển khai (`proposal.md`, `specs/`, `design.md`, `tasks.md`) dựa trên nền tảng kỹ thuật đã được nghiên cứu toàn diện.
- **Phương án 2**: Chạy `/wf_pre_openspec` nếu muốn chuẩn hóa lại danh sách Functional Requirements (FR) từ tài liệu `business_analysis.md`.
