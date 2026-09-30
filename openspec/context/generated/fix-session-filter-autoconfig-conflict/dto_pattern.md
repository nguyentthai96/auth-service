# DTO Pattern

_Generated: 2026-09-30 | Services: base-security-starter, auth-service_

## Request DTOs

### LoginRequestDto
- **Path**: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/LoginRequestDto.kt`
- **Fields**:
  - `username: String` (`@field:NotBlank`, `@field:Size(min = 3, max = 50)`)
  - `password: String` (`@field:NotBlank`)
  - `domainCode: String?`
  - `captchaToken: String?`
  - `trustedDeviceHash: String?`
  - `anonymousSessionId: String?`
  - `anonymousToken: String?`

## Response DTOs

### AuthResponse
- **Path**: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/AuthResponse.kt`
- **Fields**:
  - `accessToken: String`
  - `refreshToken: String?` (nullified when returned to client if sent via HttpOnly cookie)
  - `tokenType: String = "Bearer"`
  - `expiresIn: Long`
  - `user: UserInfoDto`
  - `promotedFromAnonymous: Boolean`
  - `dataTransferred: DataTransferredInfo?`

### ApiResponse Envelope
- **Class**: `com.ntt.basecore.domain.web.payload.ApiResponse<T>`
- **Fields**: `success: Boolean`, `code: String`, `message: String`, `data: T?`, `timestamp: Long`

## Configuration Properties DTOs

### SessionValidationProperties (Proposed for base-security-starter)
- **Path**: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityProperties.kt`
- **Fields**:
  - `enabled: Boolean = true`

### SecurityProperties (base-security-starter)
- **Path**: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityProperties.kt`
- **Prefix**: `app.security`
- **Fields**:
  - `enabled: Boolean = true`
  - `publicPaths: List<String> = listOf("/actuator/health", "/health")`
  - `jwtIssuerUri: String = ""`
  - `tokenRevocation: TokenRevocationProperties = TokenRevocationProperties()`
  - `sessionValidation: SessionValidationProperties = SessionValidationProperties()`
