---
type: brainstorm_notes
change: cache-metrics-refactoring
date: 2026-08-21
selected_direction: "TwoLevelCacheMeterBinderProvider with manual ExecutorService for Monitor"
pre_flow: "Command"
pre_feature_type: "MAINTENANCE"
status: complete
---

# Brainstorm Notes: Cache Metrics Refactoring Implementation Details

## Context
Refactoring `CacheMetricsRegistrar` sang kiến trúc chuẩn `CacheMeterBinderProvider` của Spring Boot để sửa lỗi `BeanDefinitionOverrideException`. Chúng ta cần đi sâu vào chi tiết thiết kế của các class mới này.

## Approaches Considered

### Vấn đề 1: Lên lịch cho CacheHitRatioAlertMonitor

Làm sao để Monitor chạy định kỳ mỗi 5 phút?

#### Approach 1A: Sử dụng `@Scheduled`
- **Pros**: Code rất clean, chuẩn Spring Framework (`@Scheduled(fixedRateString = "PT5M")`).
- **Cons**: Yêu cầu người dùng (consumer của base-cache-starter) phải có `@EnableScheduling`. Nếu hệ thống của họ không bật tính năng này, Monitor sẽ im lặng không chạy. Ép buộc `@EnableScheduling` từ một starter-library là vi phạm tính độc lập (non-invasive).

#### Approach 1B: Sử dụng Virtual Thread Executor (Giữ nguyên như cũ)
- **Pros**: Hoàn toàn độc lập, không phụ thuộc vào `@EnableScheduling`. Tận dụng JDK 21 Virtual Threads (rất nhẹ). Chạy trong background không ảnh hưởng thread pool chính của app.
- **Cons**: Phải tự quản lý `ExecutorService` (cần shutdown hook nếu muốn dọn dẹp sạch).

**Quyết định**: Chọn **Approach 1B**. Starter library nên cố gắng không can thiệp vào global config của app (như `@EnableScheduling`).

---

### Vấn đề 2: Đăng ký Metrics trong `TwoLevelCacheMetricsBinder`

Spring Boot sẽ gọi `TwoLevelCacheMeterBinderProvider.getMeterBinder(cache, tags)` và mong đợi trả về một `MeterBinder`.

#### Approach 2A: Trả về trực tiếp `CaffeineCacheMetrics`
- **Pros**: Đơn giản.
- **Cons**: Mất đi cái custom gauge `cache.hit.ratio` mà chúng ta đang có.

#### Approach 2B: Tạo một custom `MeterBinder` (TwoLevelCacheMetricsBinder)
- **Pros**: Bên trong hàm `bindTo(registry)`, chúng ta gọi `CaffeineCacheMetrics(nativeCache, cache.name, tags).bindTo(registry)` để lấy toàn bộ metrics chuẩn, ĐỒNG THỜI đăng ký thêm `Gauge.builder("cache.hit.ratio")`.
- **Cons**: Cần viết thêm 1 class.

**Quyết định**: Chọn **Approach 2B**. Giữ nguyên được business logic hiện tại mà vẫn đáp ứng hoàn hảo interface của Spring Boot.

## Selected Direction
Chúng ta sẽ triển khai 3 class:
1. `TwoLevelCacheMeterBinderProvider`: Implement `CacheMeterBinderProvider<TwoLevelCache>`, class này sẽ được Spring quét tự động.
2. `TwoLevelCacheMetricsBinder`: Implement `MeterBinder`. Nhận tags từ Spring truyền xuống để đảm bảo metrics có chung metadata với các thành phần khác.
3. `CacheHitRatioAlertMonitor`: Một `@Component` tự quản lý vòng đời (sử dụng `SmartLifecycle` hoặc `@PostConstruct`/`@PreDestroy`) với `ScheduledExecutorService` dựa trên Virtual Threads.

## Pre-classifications (preliminary)
- Feature type: MAINTENANCE
- Flow type: Command
- Affected modules: `base-cache-starter` (`com.ntt.basecore.autoconfigure.cache`)

## Open Questions for Design Phase
- [RESOLVED] Cách schedule monitor? -> Dùng ExecutorService như cũ để giữ tính độc lập.
- [RESOLVED] Cách truyền tags từ Spring Boot? -> Truyền qua constructor của Provider xuống Binder.
