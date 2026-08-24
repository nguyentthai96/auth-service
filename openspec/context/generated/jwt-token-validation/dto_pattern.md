# DTO Pattern

_Generated: 2025-01-20_

## Request DTO

- `IntrospectionRequest` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - annotations: `@field:NotBlank(message = "Token is required")`
  - fields: `token: String`
  - extends: N/A (plain data class)
  - usage: `POST /api/auth/introspect` request body

## Response DTO

- `IntrospectionResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - annotations: `@JsonProperty("token_type")`, `@JsonProperty("client_id")`
  - fields: `active`, `sub`, `username`, `roles`, `permissions`, `exp`, `iat`, `iss`, `jti`, `tokenType`, `scope`, `clientId`
  - extends: N/A (plain data class)
  - usage: RFC 7662 introspection response

- `RevokeSessionsResponse` — `src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/dto/TokenDtos.kt`
  - fields: `revokedCount: Int`, `userId: Long`
  - usage: `POST /api/auth/sessions/{userId}/revoke-all` response

## Domain Events (as DTOs)

- `TokenValidationFailedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenValidationFailedEvent.kt`
  - implements: `DomainEvent`
  - fields: `reason: ValidationFailureReason`, `tokenJti: String?`, `ipAddress: String?`, `userAgent: String?`, `validatorName: String?`, `failedAt: Instant`
  - eventType: `"iam.token.validation-failed"`

- `TokenIssuedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenIssuedEvent.kt`
  - implements: `DomainEvent`
  - usage: Token issuance audit trail

- `TokenRevokedEvent` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/TokenRevokedEvent.kt`
  - implements: `DomainEvent`
  - usage: Token revocation audit trail

## Validation Result (Internal)

- `ClaimValidationResult` — `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidationResult.kt`
  - fields: `validatorName: String`, `status: ClaimValidationStatus`, `reason: String?`
  - enum: `ClaimValidationStatus { PASS, FAIL }`
  - usage: Internal DTO for ClaimValidator chain results

## Enum

- `ValidationFailureReason` — `src/main/kotlin/com/ntt/authservice/auth/domain/event/ValidationFailureReason.kt`
  - values: `BLACKLISTED`, `SIGNATURE_INVALID`, `AUDIENCE_MISMATCH`, `TYPE_REJECTED`
  - usage: Categorized reason for `TokenValidationFailedEvent`

- `ClaimValidationStatus` — `src/main/kotlin/com/ntt/authservice/auth/application/ClaimValidationResult.kt`
  - values: `PASS`, `FAIL`
  - usage: Claim validator result status

## Configuration Properties (as DTOs)

- `SecurityProperties.JwtProperties` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt`
  - fields: `secretKey`, `algorithm`, `privateKeyPath`, `publicKeyPath`, `keyId`, `accessTokenExpirationMs`, `refreshTokenExpirationMs`, `absoluteCeilingMs`, `issuer`, `clockSkewSeconds`, `audience`, `previousPublicKeyPath`, `previousKeyId`
  - annotation: via parent `@ConfigurationProperties(prefix = "app.security")`

- `SecurityProperties.BlacklistCacheProperties` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt`
  - fields: `caffeineTtlSeconds`, `caffeineMaxSize`, `redisTimeoutMs`, `redisKeyPrefix`, `circuitBreakerThreshold`, `circuitBreakerResetSeconds`

- `SecurityProperties.ValidationEventProperties` — `src/main/kotlin/com/ntt/authservice/shared/config/SecurityProperties.kt`
  - fields: `recordExpiredEvents: Boolean`

## Port Interface (Internal)

- `TokenStore` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/TokenStore.kt`
  - methods: `blacklistToken(jti, userId, reason, expiresAt)`, `saveRefreshToken()`, `findValidRefreshToken()`, `revokeToken()`, `revokeAllForUser()`

- `RefreshTokenInfo` — `src/main/kotlin/com/ntt/authservice/auth/application/port/out/TokenStore.kt`
  - fields: `userId: Long`, `tokenHash: String`, `expiresAt: Instant`, `revoked: Boolean`

## NOT DETECTED

- Filter DTO — N/A (no request filter DTOs specific to JWT validation)
- Pagination DTO — N/A (not applicable to JWT validation endpoints)
