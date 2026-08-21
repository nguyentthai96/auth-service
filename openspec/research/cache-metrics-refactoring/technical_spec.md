# Technical Specification: Chuyển đổi sang CacheMeterBinderProvider

Tài liệu này cung cấp bản thiết kế kỹ thuật chi tiết để refactor `CacheMetricsRegistrar` sang kiến trúc chuẩn của Spring Boot.

## 1. Architecture Changes

Thay vì để `base-core` tự quản lý vòng đời và việc đăng ký metrics (thông qua `SmartInitializingSingleton`), quyền điều khiển sẽ được trao lại cho Spring Boot. Spring Boot sẽ tự động gọi provider của chúng ta mỗi khi có bất kỳ cache nào được khởi tạo.

## 2. Các Components Mới

### 2.1 `TwoLevelCacheMeterBinderProvider`
- **Nhiệm vụ:** Lắng nghe Spring Boot Actuator, trả về Binder phù hợp cho `TwoLevelCache`.
- **Implement:** `CacheMeterBinderProvider<TwoLevelCache>`
- **Logic:**
  - Nhận vào instance của `TwoLevelCache`.
  - Trích xuất L1 Native Cache (Caffeine).
  - Khởi tạo và trả về `TwoLevelCacheMetricsBinder`.

### 2.2 `TwoLevelCacheMetricsBinder`
- **Nhiệm vụ:** Đăng ký các metrics vào Micrometer.
- **Implement:** `MeterBinder`
- **Logic:**
  - Gọi `CaffeineCacheMetrics.monitor(...)` để bind các thông số cơ bản.
  - Sử dụng `Gauge.builder("cache.hit.ratio")` để đăng ký chỉ số hit ratio.

### 2.3 `CacheHitRatioAlertMonitor`
- **Nhiệm vụ:** Chạy ngầm định kỳ (Virtual Thread) cảnh báo nếu Hit Ratio thấp.
- **Cơ chế:**
  - Inject `TwoLevelCacheManager`.
  - Dùng `@Scheduled(fixedRateString = "PT5M")` hoặc tự khởi tạo `Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual()...)` như cũ.
  - Duyệt qua các cache để tính toán và in cảnh báo.

## 3. Cập nhật `CacheMetricsAutoConfiguration`

Cấu hình mới sẽ trông như sau:

```kotlin
@AutoConfiguration(after = [CacheAutoConfiguration::class])
@ConditionalOnClass(name = ["io.micrometer.core.instrument.MeterRegistry"])
@ConditionalOnBean(TwoLevelCacheManager::class)
@ConditionalOnProperty(prefix = "app.cache", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class CacheMetricsAutoConfiguration {

    @Bean
    fun twoLevelCacheMeterBinderProvider(): CacheMeterBinderProvider<TwoLevelCache> {
        return TwoLevelCacheMeterBinderProvider()
    }

    @Bean
    fun cacheHitRatioAlertMonitor(cacheManager: TwoLevelCacheManager): CacheHitRatioAlertMonitor {
        return CacheHitRatioAlertMonitor(cacheManager)
    }
}
```

## 4. Agent Implementation Notes
- **Lưu ý 1:** Bằng cách này, chúng ta hoàn toàn loại bỏ `CacheMetricsRegistrar` cũ và **chấp nhận dùng bean Registrar mặc định của Spring Boot**.
- **Lưu ý 2:** Lỗi `BeanDefinitionOverrideException` biến mất hoàn toàn mà không cần đặt cờ `allow-bean-definition-overriding: true` hoặc đổi tên bean tạm bợ.
- **Lưu ý 3:** Metrics bây giờ sẽ tự động được bind ngay cả khi cấu hình cache được tạo động (lazy load).
