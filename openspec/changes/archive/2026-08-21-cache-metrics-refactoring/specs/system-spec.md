# System Specification: Cache Metrics Refactoring

## 1. Feature
Refactor kiến trúc đăng ký metrics của `TwoLevelCache` để sử dụng `CacheMeterBinderProvider` của Spring Boot.

## 2. Dependencies
- Spring Boot Actuator
- Micrometer Core
- Caffeine Cache

## 3. Data Model Changes
Không có thay đổi về CSDL (N/A).

## 4. API Endpoints
Không có API mới (N/A).

## 5. Security
N/A

## 6. Logic & Rules
1. **Binding Metrics**:
   - `TwoLevelCacheMeterBinderProvider` chỉ cung cấp binder nếu cache là `TwoLevelCache`.
   - Tags của metrics (nếu được truyền từ Spring) phải được áp dụng vào Gauge hit ratio.
2. **Monitoring Alert**:
   - Chạy 1 Virtual Thread 5 phút/lần.
   - Nếu `requestCount > 100` và `hitRate < 0.8`, ghi log mức độ WARN.
