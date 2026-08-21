# Service Structure

## Packages
- `com.ntt.basecore.autoconfigure.cache`: Chứa toàn bộ cấu hình auto-configuration cho cache metrics.

## File Naming Conventions
- Auto-configuration: `*AutoConfiguration.kt` (e.g., `CacheMetricsAutoConfiguration.kt`)
- Utilities/Services: `<Feature>.kt` (e.g., `CacheMetricsRegistrar.kt`, `TwoLevelCache.kt`)
