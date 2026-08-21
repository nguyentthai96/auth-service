# DTO Pattern — auth-core-features

> Generated: 2026-08-26
> Scope: `auth-service` (primary candidate service)
> Source: Code scan — grep for Request/Response/Dto patterns

---

## 1. DTO Files

| File | Path | Content |
|------|------|---------|
| `RequestDtos.kt` | `auth/adapter/in/web/dto/RequestDtos.kt` | Login request, register request, refresh token request, change password request |
| `AuthResponse.kt` | `auth/adapter/in/web/dto/AuthResponse.kt` | Auth response (access token, refresh token, user info) |
| `MfaDtos.kt` | `auth/adapter/in/web/dto/MfaDtos.kt` | MFA verify request, MFA settings request, MFA setup response, TOTP setup response, recovery codes response |
| `SsoDtos.kt` | `auth/adapter/in/web/dto/SsoDtos.kt` | SSO callback request, SSO link request, SSO provider info response |
| `TokenDtos.kt` | `auth/adapter/in/web/dto/TokenDtos.kt` | Token introspection request, JWKS response |
| `AnonymousDtos.kt` | `auth/adapter/in/web/dto/AnonymousDtos.kt` | Anonymous session create request, renew request, data CRUD DTOs |

## 2. Request Patterns

| DTO | File | Annotations | Notes |
|-----|------|-------------|-------|
| `LoginRequest` | `RequestDtos.kt` | `data class` | username, password, captchaToken?, trustedDeviceHash?, domainCode? |
| `RegisterRequest` | `RequestDtos.kt` | `data class` | username, password, email, displayName, domainCode |
| `RefreshTokenRequest` | `RequestDtos.kt` | `data class` | refreshToken |
| `ChangePasswordRequest` | `RequestDtos.kt` | `data class` | currentPassword, newPassword |
| `MfaVerifyRequest` | `MfaDtos.kt` | `data class` | mfaToken, code, trustDevice? |
| `MfaSettingsRequest` | `MfaDtos.kt` | `data class` | enabled, method |
| `SsoCallbackRequest` | `SsoDtos.kt` | `data class` | provider, code, state, redirectUri |
| `SsoLinkRequest` | `SsoDtos.kt` | `data class` | provider, code, redirectUri |
| `TokenIntrospectionRequest` | `TokenDtos.kt` | `data class` | token |

## 3. Response Patterns

| DTO | File | Notes |
|-----|------|-------|
| `AuthResponse` | `AuthResponse.kt` | accessToken, refreshToken, tokenType, expiresIn, user (nested UserInfo) |
| `MfaRequiredResponse` | `MfaDtos.kt` | mfaToken, method, expiresIn (returned when MFA needed) |
| `TotpSetupResponse` | `MfaDtos.kt` | secretUri (QR code URI), recoveryCodes |
| `RecoveryCodesResponse` | `MfaDtos.kt` | codes (list of backup codes) |
| `SsoProviderInfoResponse` | `SsoDtos.kt` | id, name, enabled |
| `TokenIntrospectionResponse` | `TokenDtos.kt` | active, sub, username, roles, permissions, exp, iat, iss, jti |
| `JwksResponse` | `TokenDtos.kt` | keys (JWK set) |

## 4. Internal Data Classes

| Class | File | Purpose |
|-------|------|---------|
| `LoginResult` (sealed) | `auth/application/LoginResult.kt` | `Success(authResponse)` / `MfaRequired(mfaToken, method, expiresIn)` |
| `RegisterResult` (sealed) | `auth/application/RegisterResult.kt` | Registration result variants |
| `PromotionResult` | `auth/application/PromotionResult.kt` | Anonymous → authenticated session promotion result |
| `AnonymousSessionResult` | `auth/application/AnonymousSessionResult.kt` | Anonymous session creation result |
| `IdpUserInfo` | `auth/application/SsoAdapter.kt` | SSO IdP user info (sub, email, name) |
| `SsoProviderInfo` | `auth/application/SsoAdapter.kt` | SSO provider config (id, name, enabled) |
| `ServiceClaims` | `auth/application/ServiceTokenService.kt` | Service-to-service JWT claims |
| `LockInfo` | `auth/application/MfaRateLimitService.kt` | Rate limit lock metadata |
| `UserAgentInfo` | `auth/application/LoginSessionService.kt` | Parsed user agent info |
| `ResolvedPolicy` | `auth/application/SessionPolicyService.kt` | Resolved session policy |
| `DataTransferResult` | `auth/application/AnonymousSessionDataService.kt` | Data transfer result |
| `CipherVersion` | `auth/application/cipher/CipherVersionNegotiator.kt` | Cipher version metadata |

## 5. Domain Events (DTOs for Event Bus)

| Event | File | Purpose |
|-------|------|---------|
| `PermissionChangedEvent` | `auth/application/port/out/EventPublisher.kt` | Permission cache invalidation |
| `SsoProvisionedEvent` | `auth/application/port/out/EventPublisher.kt` | SSO JIT provisioning notification |
| `AccountDeactivatedEvent` | `auth/application/port/out/EventPublisher.kt` | Account deactivation |
| `AccountDeletedEvent` | `auth/application/port/out/EventPublisher.kt` | Account deletion (GDPR) |
| `AuditEvent` | `auth/application/port/out/EventPublisher.kt` | Audit trail |
| `TokenIssuedEvent` | `auth/domain/event/TokenIssuedEvent.kt` | [NEW] Token lifecycle — issuance |
| `TokenRevokedEvent` | `auth/domain/event/TokenRevokedEvent.kt` | [NEW] Token lifecycle — revocation |
| `UserRegisteredEvent` | `auth/domain/event/UserRegisteredEvent.kt` | [NEW] User registration |
| `EventEnvelope` | `auth/domain/event/EventEnvelope.kt` | [NEW] CloudEvents-inspired wrapper |
| `UserLoggedInEvent` | `auth/application/command/AuthDomainEvents.kt` | Login success event |
| `SessionRevokedEvent` | `auth/application/command/AuthDomainEvents.kt` | Session revocation event |
| `NewDeviceLoginEvent` | `auth/application/event/NewDeviceLoginEvent.kt` | New device detected |
| `RateLimitExceededEvent` | `auth/application/event/RateLimitExceededEvent.kt` | Rate limit breach |

## 6. Validation Pattern

- Kotlin `data class` — immutable by design
- No explicit validation annotations detected (e.g., `@NotNull`, `@Size`) — validation likely handled in application layer
- Business rule validation in service layer (e.g., `PasswordPolicyService` uses Passay library)
- Request validation via Controller method-level checks
