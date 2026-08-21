<!-- self-contained: true -->
# Implementation Tasks

 - [x] **Task 1: Tạo `TwoLevelCacheMeterBinderProvider`**
  - File: `components/base-core/starters/base-cache-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/cache/TwoLevelCacheMeterBinderProvider.kt` | Action: [NEW]
  - Base: `CacheMeterBinderProvider<TwoLevelCache>` từ `org.springframework.boot.actuate.metrics.cache`
  - FR: FR-002 — Áp dụng chuẩn `CacheMeterBinderProvider` của Spring Boot
  - Source: Tách logic từ `CacheMetricsRegistrar.kt`

 - [x] **Task 2: Tạo `TwoLevelCacheMetricsBinder`**
  - File: `components/base-core/starters/base-cache-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/cache/TwoLevelCacheMetricsBinder.kt` | Action: [NEW]
  - Base: `MeterBinder` từ `io.micrometer.core.instrument.binder`
  - FR: FR-003, FR-004 — Đăng ký các metrics cơ bản và custom Gauge `cache.hit.ratio`
  - Source: Tách logic từ `CacheMetricsRegistrar.kt`

 - [x] **Task 3: Tạo `CacheHitRatioAlertMonitor`**
  - File: `components/base-core/starters/base-cache-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/cache/CacheHitRatioAlertMonitor.kt` | Action: [NEW]
  - FR: FR-005 — Giữ nguyên tính năng cảnh báo khi hit ratio thấp (chạy bằng Virtual Thread)
  - Source: Tách logic scheduler từ `CacheMetricsRegistrar.kt`

 - [x] **Task 4: Xóa `CacheMetricsRegistrar` & Cập nhật AutoConfiguration**
  - File: `components/base-core/starters/base-cache-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/cache/CacheMetricsAutoConfiguration.kt` | Action: [MODIFY]
  - File: `components/base-core/starters/base-cache-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/cache/CacheMetricsRegistrar.kt` | Action: [DELETE]
  - FR: FR-001 — Xóa bỏ class cũ gây conflict bean definition
  - Pattern: Đăng ký 2 bean mới (`TwoLevelCacheMeterBinderProvider` và `CacheHitRatioAlertMonitor`) thay vì `cacheMetricsRegistrar`

 - [x] **Task 5: Xóa config fix cứng ở các modules (nếu có)**
  - File: `services/auth-service/src/test/resources/application-test.yml` (và tương tự ở các service khác) | Action: [MODIFY]
  - Hành động: Gỡ bỏ cờ `spring.main.allow-bean-definition-overriding: true` (nếu có) để xác minh ứng dụng có thể test bình thường mà không bị crash.
