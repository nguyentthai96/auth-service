# Integration Map

_Generated: 2026-08-11_

## Redis (Cache/Rate Limiting)

- Client: StringRedisTemplate — `spring-boot-starter-data-redis` (auto-configured)
- Protocol: Redis protocol via Lettuce
- Used by:
  - MfaRateLimitService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaRateLimitService.kt`
    - Keys: `mfa:verify:attempts:{userId}:{method}`, `mfa:verify:lock:{userId}:{method}`
    - Pattern: INCR + EXPIRE, fail-open strategy
  - OtpService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/OtpService.kt`
    - Keys: `otp:{userId}:{channel}`, `otp:{userId}:{channel}:attempts`
    - Pattern: SET + GET + INCR with TTL
  - MfaService — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/MfaService.kt`
    - Keys: `totp:pending:{userId}` (pending TOTP setup)
    - Pattern: SET + GET + DELETE with TTL

## CAPTCHA Provider (REST API)

- Client: CaptchaVerifier (interface) — `auth-service/src/main/kotlin/com/ntt/authservice/auth/application/CaptchaVerifier.kt`
- Protocol: HTTPS REST (RestTemplate)
- Implementations:
  - TurnstileCaptchaVerifier — same file
  - NoopCaptchaVerifier — same file
- Request: POST to provider verify URL
- Response: JSON with `success: boolean`

## HTTP SSO Gateway (REST API)

- Client: HttpSsoGateway — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpSsoGateway.kt`
- Protocol: HTTPS REST
- Notes: OAuth2 token exchange

## HTTP CAPTCHA Gateway (REST API)

- Client: HttpCaptchaGateway — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/out/http/HttpCaptchaGateway.kt`
- Protocol: HTTPS REST

## Kafka (Event Streaming)

- Client: PermissionChangedConsumer — `auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/kafka/PermissionChangedConsumer.kt`
- Protocol: Kafka
- Topics: `iam.permission.changed`
- Notes: Consumer only — listens for permission cache invalidation

## Frontend HTTP Client (Ky)

- Client: ky (HTTP client) — `admindashboard/src/utils/api.ts`
- Protocol: HTTPS
- Config: prefixUrl from env, global headers (Authorization: Bearer)
- Notes: Currently does NOT send `credentials: 'include'` — needs modification for cookie flow

## NOT DETECTED

- gRPC — NOT DETECTED
- GraphQL — NOT DETECTED
- WebSocket — NOT DETECTED
