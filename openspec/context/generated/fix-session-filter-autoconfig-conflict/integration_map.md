# Integration Map

_Generated: 2026-09-30 | Services: base-security-starter, auth-service_

## External Integrations

NOT DETECTED — This change is entirely internal to the architecture framework (`base-security-starter`) and service configuration (`auth-service`). No external 3rd-party REST/gRPC/SOAP integrations are involved.

## Inter-Module Integrations

| Source Module | Target Module | Mechanism | Purpose |
|---------------|---------------|-----------|---------|
| `base-security-starter` | `auth-service` | Gradle dependency (`implementation("com.ntt:base-security-starter")`) | Provides auto-configuration for security properties, cipher, and dynamic authorization. |
| `base-security-starter` | Resource Servers (`account-service`, etc.) | Gradle dependency + Servlet Filter (`DefaultSessionValidationFilter`) | Provides lightweight token signature verification at order `-1700`. |
| `auth-service` | PostgreSQL | JDBC (`HikariCP`, `auth_db`) | Validates user credentials (`users` table). |
| `auth-service` | Redis | `StringRedisTemplate` / Lettuce | Login rate limiting, token blacklist caching, refresh token storage. |
| Frontend (`localhost:3000`) | `auth-service` (`localhost:8081`) | HTTP REST (`POST /auth/login`) | Authentication exchange and session cookie initialization. |

## Network & Protocol Specifications
- **Login Request**: `POST /auth/login`
  - Protocol: HTTP/1.1 or HTTP/2
  - Content-Type: `application/json`
  - Headers: `Origin: http://localhost:3000`, `X-Device-Fingerprint`, `X-App-Version`
  - Body: `{"username": "...", "password": "...", "domainCode": "..."}`
- **Login Response**:
  - Status: HTTP 200 OK
  - Set-Cookie: `refreshToken=...; HttpOnly; SameSite=Lax; Path=/auth`
  - Body: `ApiResponse<AuthResponse>`
