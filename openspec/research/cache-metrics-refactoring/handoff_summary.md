# Handoff Summary: Cache Metrics Refactoring

## 1. Mục tiêu
Chuyển đổi kiến trúc thu thập metrics của `TwoLevelCache` trong module `base-core` để tương thích hoàn toàn với chuẩn Spring Boot Actuator, gỡ bỏ triệt để lỗi `BeanDefinitionOverrideException` mà không làm mất đi các tính năng monitor hiện có.

## 2. Kết quả Research
- **Nguyên nhân gốc rễ**: Sự xung đột giữa `SmartInitializingSingleton` tự chế của `base-core` và `CacheMetricsRegistrar` mặc định của Spring Boot.
- **Phương án lựa chọn**: Sử dụng `CacheMeterBinderProvider` (Phương án 3).

## 3. Danh sách công việc cần làm (Next Steps)
1. **Xóa file cũ**: Xóa bỏ hoàn toàn `CacheMetricsRegistrar.kt` hiện tại.
2. **Tạo `TwoLevelCacheMeterBinderProvider.kt`**: Implement `CacheMeterBinderProvider<TwoLevelCache>`.
3. **Tạo `TwoLevelCacheMetricsBinder.kt`**: Chứa logic gọi `CaffeineCacheMetrics.monitor` và đăng ký hit ratio gauge.
4. **Tạo `CacheHitRatioAlertMonitor.kt`**: Chứa Virtual Thread scheduler kiểm tra hit ratio định kỳ.
5. **Cập nhật cấu hình**: Đổi lại các khai báo `@Bean` trong `CacheMetricsAutoConfiguration.kt`.

## 4. Tài liệu tham khảo
- [Research Brief](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/cache-metrics-refactoring/research_brief.md)
- [Phân tích Trade-off & So sánh](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/cache-metrics-refactoring/comparison_analysis.md)
- [Technical Spec](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/openspec/research/cache-metrics-refactoring/technical_spec.md)
