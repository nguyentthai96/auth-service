# DTO Pattern

_Generated: 2025-07-15_

## Request DTO

- `CreateAnonymousSessionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` — fields: `deviceFingerprint: String?` (optional). No validation annotations.
- `StoreSessionDataRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` — fields: `namespace: @NotBlank String`, `key: @NotBlank String`, `value: @NotNull Any`. Annotations: `@field:NotBlank`, `@field:NotNull`.
- `CreateAnonymousSessionCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/CreateAnonymousSessionCommand.kt` — CQRS command: `ipAddress: String`, `deviceFingerprint: String?`
- `RenewAnonymousTokenCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenCommand.kt` — CQRS command: `currentToken: String`

## Response DTO

- `AnonymousTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` — fields: `token: String`, `sessionId: String`, `expiresIn: Long`, `tokenType: String = "Bearer"`
- `SessionDataResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` — fields: `namespace: String`, `key: String`, `value: Any?`
- `DataTransferredInfo` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt` — fields: `itemCount: Int`, `namespaces: List<String>`, `status: String`

## Result DTO (Application Layer)

- `AnonymousSessionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionResult.kt` — fields: `token: String`, `sessionId: String`, `expiresIn: Long`
- `PromotionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/PromotionResult.kt` — fields: `status: Status`, `itemCount: Int`, `namespaces: List<String>`. Status enum: SUCCESS, PARTIAL, FAILED, CONFLICT, SKIPPED.
- `DataTransferResult` (nested in AnonymousSessionDataService) — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionDataService.kt` — fields: `itemCount: Int`, `namespaces: List<String>`, `partial: Boolean`

## Configuration DTO

- `SecurityProperties.AnonymousProperties` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt` line 191 — data class with fields:
  - `tokenTtlSeconds: Long = 3600` (1 hour)
  - `sessionTtlSeconds: Long = 86400` (24 hours)
  - `maxDataSizeBytes: Long = 65536` (64KB)
  - `maxRenewals: Int = 24`
  - `promotedDataTtlSeconds: Long = 604800` (7 days)
  - `rateLimit: MfaProperties.LimitConfig` (maxAttempts=5, windowSeconds=3600, lockSeconds=0)
  - **TO ADD**: `slidingWindowEnabled: Boolean = true` (feature flag)
  - **TO ADD**: `scanCount: Int = 100` (SCAN batch size, tunable)

## DTO Pattern Summary

- All DTOs are Kotlin `data class` — no inheritance, no builders
- Validation uses Jakarta Bean Validation: `@field:NotBlank`, `@field:NotNull`
- Request DTOs at adapter layer (`auth.adapter.in.web.dto`)
- Command DTOs at application layer (`auth.application.command`)
- Result DTOs at application layer (`auth.application`)
- Configuration DTOs as nested data classes in `SecurityProperties`
- No Base*Request or Base*Response classes — flat DTO hierarchy

## NOT DETECTED

- DTO base classes (no `BaseRequest`, `BaseResponse`)
- `@Getter`, `@Builder`, `@SuperBuilder` annotations (Kotlin data classes used instead)
- Filter DTOs
