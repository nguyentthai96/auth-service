# Pre-OpenSpec: cache-metrics-refactoring

> **Type**: MAINTENANCE
> **Flow**: Command
> **Source**: User Idea
> **Classification Evidence**: cache → base-core → CacheMetricsAutoConfiguration.kt
> **Archive**: N/A
> **Quality Score**: 100/100

## 📋 Feature Summary

Thay thế class `CacheMetricsRegistrar` tự viết (sử dụng `SmartInitializingSingleton`) bằng chuẩn của Spring Boot Actuator (`CacheMeterBinderProvider`). Việc này giúp gỡ bỏ triệt để lỗi xung đột tên bean `BeanDefinitionOverrideException` và cho phép Spring tự động bind metrics cho cả những cache được tạo lazy-load.

| Metric | Giá trị |
|--------|---------|
| Số FR | 5 (Idea: 5, Enriched: 0) |
| Issues | 0 (🔴: 0, 🟡: 0) |
| Open Questions | 0 |
| **Quality Score** | **100/100** |

---

## 1. Actors

- **System**: Quản lý vòng đời cache và bind metrics vào Micrometer registry.

## 2. Functional Requirements

### FR-001: Xóa bỏ CacheMetricsRegistrar cũ [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải xóa bỏ class `CacheMetricsRegistrar` hiện tại (đang implement `SmartInitializingSingleton`).
- **Validation**: Không còn class `CacheMetricsRegistrar` trong source code.

### FR-002: Tạo TwoLevelCacheMeterBinderProvider [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải có class `TwoLevelCacheMeterBinderProvider` implement interface `CacheMeterBinderProvider<TwoLevelCache>`.
- **Validation**: Class được khai báo đúng generic type và được Spring Boot tự động nhận diện.

### FR-003: Tạo TwoLevelCacheMetricsBinder [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải có class `TwoLevelCacheMetricsBinder` implement `MeterBinder` để gắn kết metrics cơ bản của Caffeine và chỉ số `cache.hit.ratio`.
- **Validation**: Các thông số của `CaffeineCacheMetrics` và hit ratio được register thành công.

### FR-004: Tách CacheHitRatioAlertMonitor [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải tách chức năng giám sát (chạy Virtual Thread báo log khi hit ratio < 80%) thành một bean độc lập `CacheHitRatioAlertMonitor`.
- **Validation**: Log cảnh báo vẫn hoạt động bình thường, định kỳ mỗi 5 phút hoặc 100 requests.

### FR-005: Cập nhật AutoConfiguration [IDEA]
- **Actor**: System
- **Action**: Hệ thống phải cập nhật `CacheMetricsAutoConfiguration` để xoá khai báo cũ và đăng ký các bean mới (`TwoLevelCacheMeterBinderProvider`, `CacheHitRatioAlertMonitor`).
- **Validation**: Lỗi `BeanDefinitionOverrideException` không còn xuất hiện khi chạy test hoặc start server mà không cần cờ override.

## 3. Non-functional Requirements

- **Performance**: Việc đo lường và bind metrics không làm ảnh hưởng tới hiệu năng query cache. Alert Monitor phải chạy bằng Virtual Thread để không block OS thread.

---

## 4. Deduplicated & Consolidated

Không phát hiện trùng lặp.

## 5. Enriched Domain Requirements

Không bổ sung thêm.

### External Integrations (from Step 2d)

| Hệ thống | Mục đích | Ghi chú |
|-----------|----------|---------|
| Micrometer / Spring Boot Actuator | Đẩy metric giám sát | Tích hợp sâu vào core |

## 6. Assumptions

- `TwoLevelCache` vẫn duy trì cấu trúc bao bọc `CaffeineCache` (L1) và `RedisCache` (L2).

---

## 7. Quality Score

| Tiêu chí | Điểm | Deduction |
|----------|-------|-----------|
| Rõ ràng (Clarity) | 25/25 | Không |
| Đầy đủ (Completeness) | 25/25 | Không |
| Nhất quán (Consistency) | 25/25 | Không |
| Kiểm thử được (Testability) | 25/25 | Không |
| **Tổng** | **100/100** | |

### Chi tiết trừ điểm

Không có điểm trừ.

---

## 8. Issues & Risks

> Không phát hiện vấn đề.

## 9. Open Questions

> Không có câu hỏi mở.

## 10. DETECTED SCOPE

<!-- STRUCTURED_MARKER: DO NOT MODIFY section name or sub-headers -->

### 10.1 Domain
base-core

### 10.2 Flow Type
Command

### 10.3 Candidate Services
- components/base-core/starters/base-cache-starter: Keyword evidence `cacheMetricsRegistrar`, `CacheMetricsAutoConfiguration`.

### Detection Evidence
- Keyword: cache → Module: base-core → File: `components/base-core/starters/base-cache-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/cache/CacheMetricsAutoConfiguration.kt`

### 10.4 External Integrations
Micrometer (Spring Boot Actuator)

### 10.5 Required Modules
base-cache-starter

---
## 11. Transaction Flow Detail

| Step | Actor | Action | System |
|------|-------|--------|--------|
| 1 | System | Khởi tạo TwoLevelCache | base-core |
| 2 | System | Spring Boot gọi TwoLevelCacheMeterBinderProvider | Actuator |
| 3 | System | Trả về TwoLevelCacheMetricsBinder | base-core |
| 4 | System | Bind metrics & gauge vào Registry | Micrometer |


## 12. Traceability Matrix

| FR-ID | URD Section | Spec Section | Affected Class | Status |
|-------|-------------|-------------|---------------|--------|
| FR-001 | Idea | | CacheMetricsRegistrar.kt | Pending |
| FR-002 | Idea | | TwoLevelCacheMeterBinderProvider.kt | Pending |
| FR-003 | Idea | | TwoLevelCacheMetricsBinder.kt | Pending |
| FR-004 | Idea | | CacheHitRatioAlertMonitor.kt | Pending |
| FR-005 | Idea | | CacheMetricsAutoConfiguration.kt | Pending |

## 13. Agent Notes (Tổng hợp bổ sung)

### Observations
Đây là một technical refactoring ở mức framework (starter library). Cần tuân thủ chặt chẽ API của Spring Boot Actuator (`CacheMeterBinderProvider`, `MeterBinder`) thay vì viết logic hook khởi tạo thủ công như trước đây.

### Suggested Approach
Chúng ta sẽ xóa `CacheMetricsRegistrar` và tạo 3 file mới:
- `TwoLevelCacheMeterBinderProvider`
- `TwoLevelCacheMetricsBinder`
- `CacheHitRatioAlertMonitor`

Tất cả nằm trong package `com.ntt.basecore.autoconfigure.cache`.
