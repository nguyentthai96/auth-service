# Software Requirements Specification (SRS)

## 1. Overview
Thiết lập framework kiểm thử hiệu năng (TPS) và Integration Test cho I/O Database/Cache, tập trung vào khả năng mã hóa (None/Full/Partial) của `base-cache-starter`.

## 2. Use Cases & Scenarios
- **UC-1: Test End-to-End User Login (AuthService)**
  - Hệ thống giả lập tải cao trên endpoint Login để kiểm tra TPS và Latency khi Cache được kích hoạt với các cấu hình mã hóa khác nhau.
- **UC-2: Test Profile Management (ProfileService)**
  - Hệ thống kiểm tra tính đúng đắn của dữ liệu PII lưu trong Redis, đảm bảo dữ liệu không bị lộ dưới dạng clear-text khi bật mã hóa.

## 3. Functional Requirements
- **FR-001**: Hỗ trợ Assert số lượng SQL (`assertQueryCount`) qua `datasource-proxy`.
- **FR-002**: Test luồng HTTP Client với WireMock.
- **FR-003**: E2E Load Test bằng K6 (`auth_flow.js`, `profile_flow.js`).
- **FR-004**: Tích hợp K6 vào Gradle (`k6Run` task).
- **FR-005**: Đánh giá JMH Microbenchmark cho logic serialization/encryption.
- **FR-006**: Cấu hình Fail Fast (Thresholds) cho CI/CD.
- **FR-007**: Tích hợp Integration Test kiểm tra raw data trong Redis để đảm bảo Cache Encryption hoạt động đúng theo cấu hình (NONE/FULL/PARTIAL).

## 4. Non-Functional Requirements
- Môi trường test độc lập, không ảnh hưởng Database Production.
- Tốc độ thực thi CI/CD nhanh gọn.
