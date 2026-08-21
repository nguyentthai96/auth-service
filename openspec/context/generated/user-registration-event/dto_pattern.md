# DTO Pattern

_Generated: 2025-08-21 | Feature: user-registration-event_

## Request DTO

- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: `username: String` (@NotBlank), `email: String` (@NotBlank @Email), `password: String` (@NotBlank @Size(min=8, max=100)), `fullName: String` (@NotBlank), `phone: String?`, `domainCode: String` (default="default"), `anonymousSessionId: String?`, `anonymousToken: String?`
  - Validation: Jakarta Bean Validation annotations
- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: `username`, `password`, `domainCode?`, `captchaToken?`, `trustedDeviceHash?`, `deviceFingerprint?`, `captchaPayload?`, `anonymousSessionId?`, `anonymousToken?`
- `RefreshTokenRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: `refreshToken: String` (@NotBlank)
- `SwitchDomainRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: `domainCode: String` (@NotBlank)
- `MfaVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Fields: `mfaToken`, `code`, `trustDevice`, `deviceHash?`
- `TotpConfirmRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaResendRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `MfaSettingsRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `SsoCallbackRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
  - Fields: `code`, `provider`, `redirectUri`
- `SsoLinkRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `IntrospectionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `CreateAnonymousSessionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `StoreSessionDataRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - Fields: `accessToken`, `refreshToken?`, `tokenType` (default="Bearer"), `expiresIn`, `userId`, `username`, `activeDomain`, `roles: List<String>`, `permissions: List<String>`, `promotedFromAnonymous` (default=false), `dataTransferred: DataTransferredInfo?`, `message?`
  - Factory: `AuthResponse.from(token: AuthToken)` — converts domain model to DTO
  - JSON: `@JsonInclude(NON_NULL)` for optional `message` field
- `MfaRequiredResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Fields: `mfaRequired`, `mfaToken`, `method`, `expiresIn`
- `TotpSetupResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Fields: `secret`, `qrCodeUri`, `issuer`
- `MfaSettingsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `RecoveryCodesResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Fields: `codes: List<String>`, `generatedAt`, `totalCodes`, `message`
- `SsoProviderInfoDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `IntrospectionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - Fields: `active`, `sub?`, `username?`, `roles?`, `permissions?`, `exp?`, `iat?`, `iss?`, `jti?`
- `RevokeSessionsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - Fields: `revokedCount`, `userId`
- `AnonymousTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - Fields: `token`, `sessionId`, `expiresIn`, `tokenType`
- `SessionDataResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `DataTransferredInfo` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
  - Fields: `itemCount`, `namespaces: List<String>`, `status`

## Domain Event DTO (Event Payloads)

- `DomainEvent` (interface) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Fields: `eventType: String`
- `UserRegisteredEvent` (data class) — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Current fields: `userId: Long`, `username: String`, `domainCode: String`
  - eventType: `"user.registered"` (needs normalization to `"iam.user.registered"`)
  - ⚠️ Duplicate exists in `AuthDomainEvents.kt` with eventType `"USER_REGISTERED"`
- `PermissionChangedEvent` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/EventPublisher.kt`
  - Fields: `userId: Long?`, `domainId: Long?`
- `SsoProvisionedEvent` — Fields: `userId`, `provider`, `email?`, `domainCode`
- `AccountDeactivatedEvent` — Fields: `userId`, `reason?`
- `AccountDeletedEvent` — Fields: `userId`
- `AuditEvent` — Fields: `userId?`, `action`, `entityType?`, `entityId?`, `ipAddress?`, `userAgent?`, `details?`

## CAPTCHA Verification DTO

- `CaptchaVerifyResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/CaptchaClient.kt`
  - Fields: `success: Boolean`, `score: Double?`, `action: String?`, `errorCodes: List<String>?`
  - Used by: `CaptchaClient` (declarative HTTP client)

## DTO Conventions

- **Style**: Kotlin `data class` (immutable, structural equality, automatic `copy()`)
- **Grouping**: Feature-based files — `RequestDtos.kt`, `AuthResponse.kt`, `MfaDtos.kt`, `SsoDtos.kt`, `TokenDtos.kt`, `AnonymousDtos.kt`
- **Naming**: `*Request` / `*RequestDto` for input, `*Response` for output, `*Event` for domain events
- **Validation**: Jakarta Bean Validation — `@field:NotBlank`, `@field:Email`, `@field:Size` on constructor fields
- **Base class**: None — all standalone `data class` (no inheritance)
- **Serialization**: Jackson (auto-configured via `JacksonConfig.kt`)
- **Nullability**: Kotlin nullable types (`?`) for optional fields, non-null for required

## NOT DETECTED

- Filter DTO (no `*Filter.kt` DTO classes — web filters are HTTP filters, not DTO)
- `@Getter` / `@Builder` / `@SuperBuilder` (Java Lombok — not used, Kotlin data class instead)
- Nested DTO (no complex nested DTOs beyond `DataTransferredInfo` in `AuthResponse`)
