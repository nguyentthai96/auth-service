# DTO Pattern

_Generated: 2026-09-28 | Candidate Services: base-core, auth-service, system-admin-service, account-service_

## Context Models (Immutable Domain Data Structures)

### 1. `RequestContext`
- **Location**: `com.ntt.basecore.context.RequestContext`
- **Pattern**: Kotlin `data class` bất biến (read-only metadata, mutable user context bridge).
```kotlin
data class RequestContext(
    val correlationId: String,
    val requestId: String,
    val clientIp: String,
    val userAgent: String?,
    val deviceFingerprint: String?,
    val appVersion: String = "unknown",
    val clientPlatform: String = "unknown",
    val locale: Locale = Locale.getDefault(),
    val timestamp: Long = System.currentTimeMillis(),
    val tenantId: String? = null,
    var user: UserContext? = null
)
```

### 2. `UserContext`
- **Location**: `com.ntt.basecore.context.UserContext`
- **Pattern**: Clean domain representation of authenticated identity.
```kotlin
data class UserContext(
    val userId: Long,
    val username: String,
    val roles: Set<String> = emptySet(),
    val permissions: Set<String> = emptySet(),
    val jti: String? = null,
    val activeDomain: String? = null,
    val attributes: Map<String, Any> = emptyMap()
)
```

## Standard API Response Envelope Pattern
Trong `base-core`, mọi API response đều chuẩn hóa qua `BaseResponse<T>` hoặc `ResponseEntity<*>`:
- `components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt`
- Headers trả về luôn bao gồm:
  - `X-Correlation-ID: <uuid>` (Echo lại từ Inbound Request)

## Serialization & Naming Convention
- DTOs dùng Jackson Kotlin Module:
  - `com.fasterxml.jackson.module.kotlin.registerKotlinModule()`
  - Field naming: `camelCase` (JSON) ↔ `camelCase` (Kotlin)
  - Timestamps: Epoch milliseconds kết thúc bằng suffix `*At` (e.g. `createdAt`, `updatedAt`) hoặc ISO-8601 UTC.
