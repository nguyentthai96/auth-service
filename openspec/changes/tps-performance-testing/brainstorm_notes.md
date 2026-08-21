---
type: brainstorm_notes
change: tps-performance-testing
date: 2026-08-21
selected_direction: "Hybrid Approach: E2E K6 for TPS + Integration Test for Correctness"
pre_flow: "Command"
pre_feature_type: "EXTEND"
status: complete
---

# Brainstorm Notes: Tích hợp đánh giá Performance Cache đa cấu hình (Mã hóa / Không mã hóa)

## Date
2026-08-21

## Context
Từ định hướng xây dựng framework test hiệu năng (TPS), user yêu cầu mở rộng trọng tâm vào tầng Cache (vừa được nâng cấp với `base-cache-starter`). Mục tiêu là benchmark và test độ chính xác/hiệu năng của 3 trạng thái:
1. Không mã hóa (NONE)
2. Mã hóa toàn bộ (FULL)
3. Mã hóa một phần (PARTIAL)

Hai use case được dùng làm ví dụ kiểm thử:
- **User Login** (`auth-service`): Tập trung vào lưu trữ Session/Token.
- **Profile Management** (`account-service`): Tập trung vào lưu cache thông tin người dùng có chứa PII (Cần mã hóa 1 phần hoặc toàn bộ).

## Questions Asked & Answers (Giả định trong luồng tư duy)
- **Q1: Làm sao để thay đổi cấu hình mã hóa khi đang chạy test?**
  - *A:* Spring Boot Cache thường khởi tạo bean lúc start-up. Ta cần dùng Spring Profiles (`application-test-none.yml`, `application-test-full.yml`) hoặc khởi tạo lại Context (dùng `@DirtiesContext` - tuy nhiên sẽ làm chậm test). Để test TPS bằng K6, ta sẽ build app Docker và truyền `ENV` biến đổi cho từng lần chạy K6.
- **Q2: Làm sao để verify rằng dữ liệu trong Redis thực sự đã bị mã hóa?**
  - *A:* Phải kết nối trực tiếp vào Redis Testcontainer (qua `StringRedisTemplate`) và đọc raw value ra, sau đó assert format (ví dụ: chuỗi mã hóa AES phải bắt đầu bằng IV, hoặc không chứa clear-text JSON).

## Approaches Considered

### Approach 1: Microbenchmarking bằng JMH (Test cục bộ Service)
- **Mô tả:** Viết benchmark JMH gọi thẳng vào `ProfileService` hoặc `SessionPromotionService` với các cấu hình Cache properties khác nhau.
- **Pros:** Đo lường chính xác tuyệt đối overhead của CPU khi dùng AES-GCM và thuật toán Serialization. Không bị nhiễu bởi Network I/O.
- **Cons:** Không phản ánh đúng TPS thực tế từ góc nhìn End-user (vì bỏ qua tầng HTTP, Tomcat, Security Filter Chain). Không test được cấu hình toàn cục.

### Approach 2: E2E Load Testing bằng K6 (CI/CD Pipeline)
- **Mô tả:** Viết script K6 gọi `POST /api/v1/auth/login` và `GET /api/v1/profiles/me`. Khởi động hệ thống 3 lần, mỗi lần với một cờ `cache.encryption.mode=NONE | FULL | PARTIAL`, sau đó chạy script K6 để vẽ biểu đồ so sánh.
- **Pros:** Có số liệu TPS và p95 latency cực kỳ sát với Production. Thấy rõ độ trễ mạng + mã hóa kết hợp.
- **Cons:** Khó debug nếu kết quả sai lệch. Không verify được dữ liệu trong Redis có đúng là bị mã hóa hay không (chỉ đo được tốc độ).

### Approach 3: Integration Test Assertions (Tính đúng đắn)
- **Mô tả:** Dùng `@SpringBootTest` kết hợp Redis Testcontainers. Gửi request HTTP nội bộ (MockMvc hoặc RestAssured), sau đó dùng `RedisTemplate` check raw data.
- **Pros:** Đảm bảo tính chính xác 100% (Security compliance).
- **Cons:** Không đo được TPS.

## Selected Direction
**Kết hợp Approach 2 và Approach 3 (Hybrid)**
1. **Tính đúng đắn (Integration Test):** Bổ sung các test case dùng `Testcontainers` vào `auth-service` và `account-service`. Gửi request API và móc vào Redis để assert raw data:
   - `mode=NONE` -> Data phải là JSON đọc được.
   - `mode=FULL` -> Data phải là chuỗi byte đã mã hóa, không thể deserialize thủ công nếu không có key.
2. **Hiệu năng (K6 Load Test Pipeline):** Viết script K6 (`auth_flow.js`, `profile_flow.js`). Thiết lập CI flow chạy lần lượt 3 môi trường cấu hình, thu thập TPS và xuất ra bảng so sánh (Benchmark Matrix) để chứng minh mã hóa PARTIAL hiệu quả hơn FULL bao nhiêu % TPS.

## Pre-classifications (preliminary)
- **Feature type:** EXTEND (Mở rộng tính năng Test cho 2 module có sẵn).
- **Flow type:** Command (Testing Infrastructure).
- **Affected modules:** `auth-service`, `account-service`, `base-testing-starter`.

## Open Questions for Design Phase
- [OPEN] Key mã hóa dùng cho Testcontainers sẽ được lưu ở đâu? (Dùng môi trường cố định hay random mỗi lần chạy test?)
- [OPEN] Đối với "mã hóa 1 phần" (PARTIAL), ta sẽ cấu hình JSON Path nào trên `ProfileResponse` để mã hóa? (VD: `$.phoneNumber`, `$.email`).
