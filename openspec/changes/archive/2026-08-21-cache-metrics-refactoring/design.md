# Technical Design: cache-metrics-refactoring

## 1. Architecture Overview
Kiến trúc sẽ dịch chuyển từ `SmartInitializingSingleton` (tự động đăng ký métrics tĩnh lúc khởi động) sang kiến trúc chuẩn `CacheMeterBinderProvider` của Spring Boot Actuator (event-driven, gọi provider động mỗi khi khởi tạo một cache).

## 2. Component Design

### 2.1 `TwoLevelCacheMeterBinderProvider`
- **Giao diện**: `CacheMeterBinderProvider<TwoLevelCache>`
- **Phương thức**: `getMeterBinder(cache: TwoLevelCache, tags: Iterable<Tag>): MeterBinder`
- **Hành vi**: Khởi tạo `TwoLevelCacheMetricsBinder` với thông số nhận được.

### 2.2 `TwoLevelCacheMetricsBinder`
- **Giao diện**: `MeterBinder`
- **Phương thức**: `bindTo(registry: MeterRegistry)`
- **Hành vi**:
  - Truy xuất L1 cache từ `TwoLevelCache`.
  - Gọi `CaffeineCacheMetrics(nativeCache, cache.name, tags).bindTo(registry)`.
  - Sử dụng `Gauge.builder("cache.hit.ratio")` để register custom metric.

### 2.3 `CacheHitRatioAlertMonitor`
- **Giao diện**: `@Component` (hoặc khai báo `@Bean`) có cơ chế `SmartLifecycle` (hoặc đơn giản là `@PostConstruct` / `@PreDestroy`).
- **Hành vi**:
  - Nhận `TwoLevelCacheManager` vào thông qua constructor.
  - Sử dụng `Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual()...)` để tạo scheduler.
  - Định kỳ duyệt qua tất cả các cache, tính hit ratio, và ghi log `WARN`.

## 3. Configuration Changes
`CacheMetricsAutoConfiguration.kt` sẽ bị thay thế phần `@Bean` cấu hình:
- Xóa `@Bean cacheMetricsRegistrar`.
- Đăng ký `@Bean twoLevelCacheMeterBinderProvider`.
- Đăng ký `@Bean cacheHitRatioAlertMonitor`.
