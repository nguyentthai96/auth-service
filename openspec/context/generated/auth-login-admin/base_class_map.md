# Base Class Map

_Generated: 2026-08-11 | Services: auth-service, admindashboard_

## Controller

- CqrsAuthController — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/CqrsAuthController.kt`
  - extends: N/A (standalone @RestController)
  - implements: N/A
  - notes: CQRS controller — delegates to LoginHandler, RegisterHandler, RefreshTokenHandler
- AuthController — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/AuthController.kt`
  - extends: N/A
  - implements: N/A
  - notes: Legacy controller with embedded DTOs (LoginRequestDto, ChangePasswordRequestDto, etc.)
- MfaController — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/MfaController.kt`
  - extends: N/A
  - implements: N/A
- TokenController — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/TokenController.kt`
  - extends: N/A
  - implements: N/A
- RateLimitAdminController — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/RateLimitAdminController.kt`
  - extends: N/A
  - implements: N/A

## Handler (CQRS)

- LoginHandler — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/LoginHandler.kt`
  - extends: N/A (@Component)
  - notes: Handles LoginCommand — password validation, account lock check, CAPTCHA check, MFA checkpoint
- RegisterHandler — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RegisterHandler.kt`
  - extends: N/A (@Component)
- RefreshTokenHandler — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RefreshTokenHandler.kt`
  - extends: N/A (@Component)
- RevokeSessionsHandler — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/RevokeSessionsHandler.kt`
  - extends: N/A (@Component)
- SwitchDomainHandler — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/SwitchDomainHandler.kt`
  - extends: N/A (@Component)
- TokenGenerator — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/command/TokenGenerator.kt`
  - extends: N/A (@Component)
  - notes: Shared token generation — used by LoginHandler, RegisterHandler, RefreshTokenHandler

## Service

- AuthService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/AuthService.kt`
  - extends: N/A (@Service)
- JwtService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/JwtService.kt`
  - extends: N/A (@Service)
  - notes: RS256 signing + HMAC fallback. generateAccessToken, generateRefreshToken, parseToken, getJwks
- MfaService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
  - extends: N/A (@Service)
- MfaRateLimitService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt`
  - extends: N/A (@Service)
  - notes: Redis INCR + EXPIRE pattern, fail-open strategy. KEY REUSE CANDIDATE for login rate limiting
- OtpService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt`
  - extends: N/A (@Service)
- TotpService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/TotpService.kt`
  - extends: N/A (@Service)
- PasswordPolicyService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/PasswordPolicyService.kt`
  - extends: N/A (@Service)
- SsoAdapter — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/SsoAdapter.kt`
  - extends: N/A (@Service)

## Exception Base

- AuthException — `auth-service/src/main/kotlin/com/ntt/authservice/shared/exception/AuthExceptions.kt`
  - extends: BusinessException (com.ntt.basecore.exception)
  - notes: Base exception for all auth errors. Uses AuthErrorCode enum

## Entity Base

- VersionedAuditableEntity — `auth-service/src/main/kotlin/com/ntt/authservice/shared/persistence/VersionedAuditableEntity.kt`
  - extends: SnowflakePersistentAuditableEntity (com.ntt.basecore.model.id)

## Frontend (TypeScript/React)

- JwtAuthProvider — `admindashboard/src/@auth/services/jwt/JwtAuthProvider.tsx`
  - extends: N/A (React component)
  - notes: Core auth provider — signIn, signUp, signOut, refreshAccessToken. Currently uses localStorage

## NOT DETECTED

- Factory classes — NOT DETECTED
- Repository interfaces — found via JpaRepository pattern (UserRepository, DomainRepository, etc.) but no explicit base repository class
