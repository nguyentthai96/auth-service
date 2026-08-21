# Research Brief: TwoLevelCache Metrics Refactoring

## 1. Feature Overview
Khắc phục triệt để lỗi `BeanDefinitionOverrideException` cho bean `cacheMetricsRegistrar` giữa `base-core` và Spring Boot Actuator, thông qua việc tuân thủ kiến trúc Spring Boot Actuator: sử dụng `CacheMeterBinderProvider` thay vì tự implement `SmartInitializingSingleton`.

## 2. Research Goals
- Hiểu rõ sự khác biệt giữa `CacheMetricsRegistrar` của Spring Boot và của `base-core`.
- Xác định cách Spring Boot Actuator tự động bind metrics cho Cache.
- Phân tích khả năng di chuyển custom metrics (hit ratio) và Scheduled Monitor (Virtual Thread) sang kiến trúc chuẩn.

## 3. Keywords & Queries
- `Spring Boot CacheMeterBinderProvider`
- `Micrometer CacheMetrics`
- `Spring Boot Actuator CacheMetricsRegistrarConfiguration`
- `Spring Boot 3 Micrometer Cache metrics binding`

## 4. Current System Analysis
### 4.1 Related Features
- Hệ thống đang dùng `TwoLevelCache` (L1 Caffeine, L2 Redis).
- `base-core` có custom `CacheMetricsRegistrar` để bind `CaffeineCacheMetrics` và add thêm gauge `cache.hit.ratio`.
- `base-core` cũng có một Scheduler (Virtual Thread) chạy 5 phút/lần để log cảnh báo nếu hit ratio < 80%.

### 4.2 Existing Patterns
- Currently, it uses `SmartInitializingSingleton` to register all caches at startup.
- Khuyết điểm: Xung đột tên bean với Spring Boot, và không tự động bắt được các cache được lazy-load (tạo ra sau khi startup) nếu không gọi thủ công.

### 4.3 Tech Stack Constraints
- Spring Boot 3.x, Kotlin, Micrometer, JDK 21+ (dùng Virtual Threads).
