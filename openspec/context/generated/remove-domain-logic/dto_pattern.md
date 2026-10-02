# DTO Pattern

_Generated: 2026-10-02_

## Request DTO

- `RegisterRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `@NotBlank`, `@Size`
- `LoginRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - annotations: `@NotBlank`
- `SwitchDomainRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - **TARGET: DELETE** (domain concept)
- `RefreshTokenRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `ChangePasswordRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `ForgotPasswordRequestDto` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
- `MfaVerifyRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `TotpConfirmRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `SsoCallbackRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
- `IntrospectionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `CreateAnonymousSessionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`

## Response DTO

- `AuthResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - factory: `AuthResponse.from(AuthToken)` — **may need update if AuthToken changes**
- `DeletionRequestResult` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/ResponseDtos.kt`
  - **contains domainId field → MODIFY**
- `MfaRequiredResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `TotpSetupResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
- `IntrospectionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
- `AnonymousTokenResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AnonymousDtos.kt`
- `DeviceResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/DeviceDtos.kt`
- `RevokeSessionsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`

## Domain Model

- `AuthToken` — `src/main/kotlin/com/ntt/authservice/auth/domain/model/AuthToken.kt`
  - **contains activeDomain field → MODIFY**

## DTO Pattern

- Style: Kotlin `data class` (no Lombok)
- Validation: Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Pattern`)
- Response wrapping: `ApiResponse<T>` (base-core)
- Factory pattern: `Response.from(DomainModel)` (e.g., `AuthResponse.from(AuthToken)`)

## NOT DETECTED

- Filter DTOs
- Pagination DTOs (likely from base-core)
