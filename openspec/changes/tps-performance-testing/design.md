# Technical Design

## 1. Architecture
Áp dụng mô hình **Tri-Layer Testing Architecture**:
- **Layer 1 (Logic / Micro)**: Dùng JMH (Java Microbenchmark Harness) để đo lường overhead của CPU cho thuật toán mã hóa (AES-GCM) và giải mã, loại bỏ nhiễu network.
- **Layer 2 (I/O & Integration)**: 
  - `AssertQueryCount`: Dùng `datasource-proxy` móc vào connection JDBC để đếm chính xác số lượng SQL, chống N+1 queries.
  - `Cache Test`: Sử dụng `StringRedisTemplate` query trực tiếp vào Redis Testcontainer để kiểm chứng dữ liệu đã bị mã hóa.
- **Layer 3 (E2E / Load)**: Sử dụng K6 script chạy qua Docker, giả lập 500 CCU gọi thẳng vào API của Spring Boot.

## 2. Cache Encryption Validation
Trong `CacheEncryptionIntegrationTest`:
- Lấy raw string từ Redis.
- Bật `mode=NONE`: Chứa chuỗi `"{\""`.
- Bật `mode=FULL` hoặc `PARTIAL`: Chuỗi không có format JSON chuẩn mà là binary/base64 payload chứa AES IV.

## 3. API Endpoints cho Load Test
- `POST /api/v1/auth/login` (Tạo session, lưu cache)
- `GET /api/v1/profiles/me` (Lấy dữ liệu PII từ cache)

## 4. Components & Utilities
- `AssertQueryCount`: Utility method `assertSelectCount(int)`, `assertInsertCount(int)`.
- `WireMockHelper`: Cấu hình Stubbing cho REST Client giả lập delay.
- K6 JS Scripts: Sử dụng ES6 modules, export function default và `options` object chứa thresholds (`http_req_duration: ['p(95)<200']`).
