# Proposal: cache-metrics-refactoring

## 1. Problem Statement
Ứng dụng bị văng lỗi `BeanDefinitionOverrideException` khi chạy test hoặc start ứng dụng do bị trùng lặp tên bean `cacheMetricsRegistrar` giữa `base-core` và `Spring Boot Actuator`. Thay vì đổi tên bean như một giải pháp tạm thời hoặc dùng `@AutoConfigureBefore` nguy hiểm, chúng ta cần refactor logic đăng ký metrics về đúng chuẩn của Spring Boot Actuator (`CacheMeterBinderProvider`) để đồng bộ hoàn toàn với core framework.

## 2. Proposed Solution
Xóa bỏ class `CacheMetricsRegistrar` hiện tại và thay thế bằng 3 component mới, thực hiện chức năng chuyên biệt tuân thủ Single Responsibility:
1. `TwoLevelCacheMeterBinderProvider`: Bắt sự kiện tạo TwoLevelCache và cung cấp binder tương ứng.
2. `TwoLevelCacheMetricsBinder`: Thực hiện bind `CaffeineCacheMetrics` và gauge hit ratio.
3. `CacheHitRatioAlertMonitor`: Quản lý luồng kiểm tra hit ratio và log cảnh báo định kỳ qua Virtual Thread.

## 3. Scope & Limitations
- Chỉ thay đổi nội bộ của module `base-cache-starter` (`base-core`).
- Không làm thay đổi hành vi của các module sử dụng thư viện này (ngoại trừ việc lỗi override biến mất).
- Giao diện và API metrics (`cache.gets`, `cache.hit.ratio`, v.v.) không bị ảnh hưởng.

## 4. Alternative Approaches Considered
- Đổi tên bean thành `twoLevelCacheMetricsRegistrar`: Giải quyết lỗi nhưng không bắt được sự kiện lazy-load của cache.
- Sử dụng `@AutoConfigureBefore`: Rủi ro cực cao, có thể văng lỗi `BeanNotOfRequiredTypeException` do ép kiểu sai chuẩn Spring.
