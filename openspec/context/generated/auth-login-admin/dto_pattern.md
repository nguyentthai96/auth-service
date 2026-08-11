# DTO Pattern

_Generated: 2026-08-11_

## Request DTO (Backend — Kotlin)

- LoginRequestDto — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: username (String), password (String), domainCode (String), captchaToken (String?), trustedDeviceHash (String?)
  - Annotations: @field:NotBlank (username, password, domainCode)
  - Notes: Also duplicated in `AuthController.kt` (legacy) — same structure

- RegisterRequestDto — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: username, password, email, fullName, domainCode
  - Annotations: @field:NotBlank, @field:Email

- RefreshTokenRequestDto — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: refreshToken (String)
  - Notes: Will need modification for cookie-based flow (extract from cookie instead)

- SwitchDomainRequestDto — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/RequestDtos.kt`
  - Fields: domainCode (String)

- MfaVerifyRequest — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Fields: mfaToken, code, trustedDeviceHash (optional)

- SsoCallbackRequest — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/SsoDtos.kt`
  - Fields: provider, code, redirectUri

- IntrospectionRequest — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - Fields: token (String)

## Response DTO (Backend — Kotlin)

- AuthResponse — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
  - Fields: accessToken, refreshToken, tokenType, expiresIn, userId, username, activeDomain, roles, permissions

- MfaRequiredResponse — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/MfaDtos.kt`
  - Fields: mfaRequired, mfaToken, method, expiresIn

- IntrospectionResponse — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - Fields: active, sub, roles, permissions, exp, iat, jti

## Request/Response DTO (Frontend — TypeScript)

- AuthResponse (type) — `admindashboard/src/@auth/authApi.ts`
  - Fields: user (User), access_token (string)
  - Notes: Mismatch with backend AuthResponse — needs alignment

- JwtSignInPayload (type) — `admindashboard/src/@auth/services/jwt/JwtAuthProvider.tsx`
  - Fields: email (string), password (string)
  - Notes: Currently uses `email` — needs change to `username`

## NOT DETECTED

- Filter DTO — NOT DETECTED (no search/filter DTOs relevant to login)
- Pagination DTO — NOT DETECTED (not relevant)
