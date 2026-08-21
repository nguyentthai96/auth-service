# DTO Pattern

_Generated: 2025-07-15 (REUSE — updated scan)_

## Command DTO (CQRS)

- `CreateAnonymousSessionCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/CreateAnonymousSessionCommand.kt` — fields: `ipAddress: String`, `deviceFingerprint: String?`
- `RenewAnonymousTokenCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenCommand.kt` — fields: `sessionId: String`, `currentJti: String`

## Request DTO

- `CreateAnonymousSessionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/` — fields: `deviceFingerprint: String?` (optional body)
- `StoreSessionDataRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/` — fields: `namespace: String`, `key: String`, `value: String` (`@Valid`)

## Response DTO

- `AnonymousTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/` — fields: `token: String`, `sessionId: String`, `expiresIn: Long`

## Result DTO (Application layer)

- `AnonymousSessionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionResult.kt` — `data class(token: String, sessionId: String, expiresIn: Long)`
- `PromotionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/PromotionResult.kt` — `data class(status: Status, itemCount: Int, namespaces: List<String>)`, enum `Status { SUCCESS, PARTIAL, CONFLICT, FAILED }`
- `DataTransferResult` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` (inner class) — `data class(itemCount: Int, namespaces: List<String>, partial: Boolean)`

## Config DTO

- `SecurityProperties.AnonymousProperties` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` (lines 191-207) — `data class` with fields: `tokenTtlSeconds`, `sessionTtlSeconds`, `maxDataSizeBytes`, `maxRenewals`, `promotedDataTtlSeconds`, `rateLimit: LimitConfig`, `slidingWindowEnabled: Boolean`, `scanCount: Int`

## Annotations

- `@Valid` — Jakarta Validation on request DTOs
- `@RequestBody` — Spring MVC
- No Lombok (`@Getter`, `@Builder`) — Kotlin data classes used instead
- No `@SuperBuilder` — plain Kotlin data class inheritance

## NOT DETECTED

- Filter DTO — NOT DETECTED (no list/filter operations in anonymous features)
- Base Request/Response classes — NOT DETECTED (DTOs are standalone data classes, not extending a base)
