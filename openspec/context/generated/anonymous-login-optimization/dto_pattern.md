# DTO Pattern

_Generated: 2025-01-20_

## Request DTO

### Anonymous Feature DTOs

- `CreateAnonymousSessionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - Fields: `deviceFingerprint: String? = null`
  - Annotations: none (all fields optional)
  - Pattern: Kotlin data class with default values

- `StoreSessionDataRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - Fields: `namespace: String`, `key: String`, `value: Any`
  - Annotations: `@field:NotBlank` (namespace, key), `@field:NotNull` (value)
  - Pattern: Kotlin data class with Jakarta validation

- `RenewAnonymousTokenCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/RenewAnonymousTokenCommand.kt`
  - Fields: `currentToken: String`
  - Pattern: CQRS Command (implements `Command<AnonymousSessionResult>`)

- `CreateAnonymousSessionCommand` — `src/main/kotlin/com/ntt/authservice/auth/application/command/CreateAnonymousSessionCommand.kt`
  - Fields: `ipAddress: String`, `deviceFingerprint: String? = null`
  - Pattern: CQRS Command (implements `Command<AnonymousSessionResult>`)

### Auth DTOs (modified for anonymous)

- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Anonymous fields: `anonymousSessionId: String? = null`, `anonymousToken: String? = null`
  - Other fields: `username`, `password`, `domainCode?`, `captchaToken?`, `trustedDeviceHash?`, `deviceFingerprint?`, `captchaPayload?`
  - Annotations: `@field:NotBlank` (username, password)

- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Anonymous fields: `anonymousSessionId: String? = null`, `anonymousToken: String? = null`
  - Other fields: `username`, `email`, `password`, `fullName`, `phone?`, `domainCode`
  - Annotations: `@field:NotBlank`, `@field:Email`, `@field:Size(min=8, max=100)`

## Response DTO

### Anonymous Feature DTOs

- `AnonymousTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - Fields: `token: String`, `sessionId: String`, `expiresIn: Long`, `tokenType: String = "Bearer"`
  - Pattern: Kotlin data class

- `SessionDataResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - Fields: `namespace: String`, `key: String`, `value: Any?`
  - Pattern: Kotlin data class

- `DataTransferredInfo` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - Fields: `itemCount: Int`, `namespaces: List<String>`, `status: String`
  - Pattern: Kotlin data class, included in AuthResponse when promotion occurs

### Auth DTOs (extended for anonymous)

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - Anonymous fields: `promotedFromAnonymous: Boolean = false`, `dataTransferred: DataTransferredInfo? = null`
  - Companion: `from(AuthToken)` factory method
  - JsonInclude: `@JsonInclude(Include.NON_NULL)` on `message` field

## Domain Result Types

- `AnonymousSessionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/AnonymousSessionResult.kt`
  - Fields: `token: String`, `sessionId: String`, `expiresIn: Long`

- `PromotionResult` — `src/main/kotlin/com/ntt/authservice/auth/application/PromotionResult.kt`
  - Fields: `status: Status`, `itemCount: Int = 0`, `namespaces: List<String> = emptyList()`
  - Status enum: `SUCCESS`, `PARTIAL`, `FAILED`, `CONFLICT`, `SKIPPED`

- `LoginResult` (sealed class) — `src/main/kotlin/com/ntt/authservice/auth/application/LoginResult.kt`
  - `Success(response: AuthResponse, promotionResult: PromotionResult? = null)`
  - `MfaRequired(mfaToken: String, method: String, expiresIn: Long)`

## Validation Annotations Used

- `@field:NotBlank` — Required non-empty string fields
- `@field:NotNull` — Required non-null fields
- `@field:Email` — Email format validation
- `@field:Size(min, max)` — Length constraints
- `@Valid` — Controller-level validation trigger

## NOT DETECTED

- No `extends Base*Request` pattern (Kotlin data classes, no base request DTO)
- No `@Getter`, `@Builder`, `@SuperBuilder` (Lombok not used — pure Kotlin data classes)
- No `Filter` DTOs for anonymous feature
