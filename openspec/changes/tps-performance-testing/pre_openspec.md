<!-- STRUCTURED_MARKER -->
> **Type**: EXTEND
> **Source**: User Idea (no URD)
> **Archive**: N/A
> **Status**: PRE-OPENSPEC

## 1. THÔNG TIN CHUNG
- **Mã tính năng**: `tps-performance-testing`
- **Mô tả ngắn**: Cung cấp phương pháp và công cụ để viết integration tests nhằm đánh giá performance (TPS), I/O Database/Cache, và API End-to-End.
- **Actor (Người dùng/Hệ thống)**: Developers, CI/CD Pipeline
- **Luồng giao dịch (Transaction Flow)**: Command (Công cụ test, không chạy luồng tài chính)

## 2. DANH SÁCH YÊU CẦU (FUNCTIONAL REQUIREMENTS)

| ID | Tiêu đề | Mô tả chi tiết | Tag |
|---|---|---|---|
| FR-001 | Hỗ trợ Assert số lượng SQL | Hệ thống phải cung cấp Annotation hoặc Helper function (VD: `assertQueryCount`) để đếm số lượng truy vấn SQL thực thi trong một block code thông qua `datasource-proxy`. | `[IDEA]` |
| FR-002 | Test luồng HTTP Client | Hệ thống phải hỗ trợ mô phỏng độ trễ của API bên ngoài bằng WireMock để đánh giá khả năng chịu tải và I/O bottleneck của các HTTP Clients. | `[IDEA]` |
| FR-003 | E2E Load Test bằng K6 | Hệ thống phải cung cấp script K6 (`auth_flow.js`) giả lập 500 Virtual Users thực hiện luồng Auth, và tự động thu thập metrics (TPS, p95). | `[IDEA]` |
| FR-004 | Tích hợp K6 vào Gradle | Hệ thống phải hỗ trợ một Gradle task (VD: `k6Run`) để tự động khởi chạy K6 container, trỏ vào ứng dụng Spring Boot đang chạy độc lập. | `[IDEA]` |
| FR-005 | Đánh giá JMH Microbenchmark | Hệ thống phải tích hợp `jmh-gradle-plugin` để benchmark các thuật toán cốt lõi (Mã hóa, Serialization) trong `base-testing-starter` hoặc module tương tự. | `[IDEA]` |
| FR-006 | Fail Fast trên CI/CD | Hệ thống phải cấu hình K6 thresholds (VD: p95 < 200ms, Error rate < 1%) để đánh rớt (fail) quá trình CI/CD nếu hiệu năng API bị giảm sút. | `[ENRICHED]` |

## 3. DANH SÁCH YÊU CẦU PHI CHỨC NĂNG (NON-FUNCTIONAL)
- **Hiệu năng**: Các bài test integration I/O phải chạy nhanh, không làm chậm quá trình build cục bộ của developer. K6 tests chỉ nên chạy độc lập hoặc trên CI pipeline.
- **Tính khả dụng**: Base testing starter phải dễ dàng import và sử dụng trong tất cả các module khác (VD: `auth-service`).

## 4. QUY TẮC NGHIỆP VỤ (BUSINESS RULES)
- Bất kỳ PR nào ảnh hưởng đến Database JPA Entity phải chạy qua Integration Test có `assertQueryCount`.
- Tuyệt đối không chạy Load test K6 vào Database Production.

## 5. CÁC ĐIỂM TÍCH HỢP (INTEGRATION POINTS)
- PostgreSQL & Redis (thông qua Testcontainers)
- K6 Engine (qua Docker)
- WireMock (Spring Cloud Contract)

## 6. PHÂN TÍCH CHẤT LƯỢNG YÊU CẦU
- **Điểm chất lượng**: 95 / 100
- **Chi tiết trừ điểm**:
  - [-5 điểm Đầy đủ (Completeness)]: Yêu cầu FR-002 đề cập đến WireMock nhưng chưa định nghĩa cụ thể API bên ngoài nào cần giả lập độ trễ trong thực tế.
  
## 7. CÁC VẤN ĐỀ CẦN LÀM RÕ (OPEN QUESTIONS)
- Với FR-002, cụ thể service bên ngoài nào của `auth-service` sẽ được giả lập đầu tiên bằng WireMock? (Có phải Kafka broker hay hệ thống Notification không?)

## 8. CÁC RỦI RO & VẤN ĐỀ ĐÃ PHÁT HIỆN (ISSUES)
- 🟡 [Ambiguity] Việc cấu hình JVM args (`-XX:+UseZGC -Xms1G -Xmx1G`) cho load test cần đảm bảo đồng nhất với cấu hình production thực tế để kết quả TPS có ý nghĩa.

## 9. ĐIỀU KIỆN TIỀN QUYẾT (PRE-CONDITIONS)
- Module `base-testing-starter` phải có sẵn thư viện `datasource-proxy`.

## 10. DETECTED SCOPE (CANDIDATE SERVICES)
<!-- STRUCTURED_MARKER -->
- `base-testing-starter` (Chứa các cấu hình chung)
- `auth-service` (Dự án thử nghiệm load test E2E)

## 11. TRANSACTION FLOW DETAIL
- **Type**: Command
- Các script chạy độc lập một chiều, trả về kết quả Report (Pass/Fail).

## 12. TRACEABILITY MATRIX
- `FR-001` → Cải tiến DB I/O (Layer 2) → `base-testing-starter/.../AssertQueryCount.kt`
- `FR-003` → Cải tiến Load Testing bằng K6 → `auth-service/tests/load/auth_flow.js`

## 13. AGENT NOTES
- **Approach**: Chia làm 2 phase thực hiện. Phase 1: Thêm `assertQueryCount` vào `base-testing-starter`. Phase 2: Viết script K6 và Gradle task trong `auth-service`.
- Việc test Database I/O sẽ mang lại giá trị tức thời lớn nhất để phát hiện lỗi N+1 queries.
