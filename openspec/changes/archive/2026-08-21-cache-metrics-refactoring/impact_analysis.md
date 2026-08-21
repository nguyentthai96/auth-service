# Impact Analysis & Reuse

## Core Files
- `CacheMetricsRegistrar.kt` (components/base-core/starters/base-cache-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/cache/CacheMetricsRegistrar.kt)
- `CacheMetricsAutoConfiguration.kt` (components/base-core/starters/base-cache-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/cache/CacheMetricsAutoConfiguration.kt)

## Call Tree
- `CacheMetricsAutoConfiguration` -> `@Bean CacheMetricsRegistrar` -> `CacheMetricsRegistrar` -> `CaffeineCacheMetrics.monitor`

## Blast Radius
- 🟢 **Low Impact**: Class `CacheMetricsRegistrar` chỉ được cấu hình duy nhất tại `CacheMetricsAutoConfiguration` trong module `base-cache-starter`. Xóa bỏ class này chỉ ảnh hưởng cục bộ tới config cache metrics. Không có service nào bên ngoài import trực tiếp `CacheMetricsRegistrar`.

## Reuse Map
- **FR-001**: Xóa bỏ `CacheMetricsRegistrar` -> Không có dependency ngầm.
- **FR-002, FR-003, FR-004**: Tách logic từ `CacheMetricsRegistrar` thành các classes mới tuân thủ chuẩn của Actuator. Chúng ta sẽ tái sử dụng cấu trúc Virtual Thread từ code cũ.

## Context Snapshot
- Module: `base-core`
- Impact scope: 1 file cấu hình (AutoConfiguration) và 1 file class (bị thay thế bởi 3 files mới).
