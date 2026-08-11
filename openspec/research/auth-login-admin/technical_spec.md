# Technical Specification — Auth Login Admin Dashboard

## 1. Architecture Overview

### High-Level Architecture Diagram

```mermaid
graph TB
    subgraph "Frontend - Admin Dashboard"
        A["Login Page<br/>username/password form"] --> B["Auth Module<br/>@auth/services/jwt"]
        B --> C["Token Manager<br/>in-memory access + cookie refresh"]
        C --> D["API Client (Ky)<br/>auto-attach Bearer token"]
    end

    subgraph "Security Layer"
        E["HTTPS/TLS 1.3"] --> F["HSTS + CSP Headers"]
        F --> G["Rate Limiter<br/>Bucket4j + Redis"]
    end

    subgraph "Backend - Auth Service"
        G --> H["AuthController<br/>/api/auth/login"]
        H --> I["LoginHandler<br/>CQRS Command"]
        I --> J["UserPort<br/>findByUsername"]
        I --> K["PasswordEncoder<br/>BCrypt verify"]
        I --> L["TokenGenerator<br/>JWT RS256"]
        L --> M["JwtService<br/>access + refresh token"]
        I --> N["CAPTCHA Gateway"]
        I --> O["MFA Service"]
    end

    subgraph "Data Stores"
        J --> P[("PostgreSQL<br/>users, refresh_tokens")]
        G --> Q[("Redis<br/>rate limits, MFA counters")]
        M --> Q
    end

    D -->|"HTTPS POST"| E

    style A fill:#4CAF50,color:#fff
    style G fill:#FF9800,color:#fff
    style Q fill:#F44336,color:#fff
    style P fill:#2196F3,color:#fff
```

---

## 2. Sequence Diagrams

### 2.1 Login Flow (Happy Path)

```mermaid
sequenceDiagram
    participant U as Admin User
    participant FE as Frontend (React)
    participant RL as Rate Limiter (Bucket4j)
    participant BE as Auth Controller
    participant LH as LoginHandler
    participant DB as PostgreSQL
    participant JWT as JwtService
    participant RD as Redis

    U->>FE: Enter username + password
    FE->>FE: Validate input (Zod schema)
    FE->>RL: POST /api/auth/login {username, password}
    
    Note over RL: Check rate limit (IP-based)
    RL->>RD: INCR rate:login:ip:{clientIp}
    RD-->>RL: count
    
    alt Rate limit exceeded
        RL-->>FE: 429 Too Many Requests + Retry-After
        FE->>U: Show "Too many attempts" + countdown
    else Within limit
        RL->>BE: Forward request
        BE->>LH: LoginCommand(username, password)
        LH->>DB: findByUsernameAndActive(username)
        
        alt User not found
            LH-->>BE: InvalidCredentialsException
            BE-->>FE: 401 {error: "INVALID_CREDENTIALS"}
            FE->>U: Show "Invalid username or password"
        else User found
            LH->>LH: Check account lock status
            
            alt Account locked
                LH-->>BE: AccountLockedException(until, reason)
                BE-->>FE: 423 {error: "ACCOUNT_LOCKED", retryAfter}
                FE->>U: Show lock message + countdown
            else Account active
                LH->>LH: Check CAPTCHA requirement
                
                alt CAPTCHA required (failedCount >= threshold-1)
                    LH-->>BE: CaptchaRequiredException
                    BE-->>FE: 428 {error: "CAPTCHA_REQUIRED"}
                    FE->>U: Show CAPTCHA widget
                else No CAPTCHA needed
                    LH->>LH: Verify password (BCrypt)
                    
                    alt Password invalid
                        LH->>DB: Increment failedLoginCount
                        LH-->>BE: InvalidCredentialsException
                        BE-->>FE: 401 {error: "INVALID_CREDENTIALS"}
                    else Password valid
                        LH->>DB: Reset failedLoginCount
                        LH->>LH: Check MFA requirement
                        
                        alt MFA required
                            LH->>JWT: generateMfaToken(userId)
                            LH-->>BE: LoginResult.MfaRequired
                            BE-->>FE: 200 {mfaRequired: true, mfaToken, method}
                            FE->>U: Redirect to MFA screen
                        else No MFA
                            LH->>JWT: generateAccessToken + generateRefreshToken
                            LH->>DB: Store refresh token hash
                            LH-->>BE: LoginResult.Success(AuthResponse)
                            BE-->>FE: 200 AuthResponse + Set-Cookie: refreshToken
                            FE->>FE: Store access token in memory
                            FE->>U: Redirect to Dashboard
                        end
                    end
                end
            end
        end
    end
```

### 2.2 Silent Token Refresh Flow

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant BE as Auth Controller
    participant JWT as JwtService
    participant DB as PostgreSQL

    Note over FE: Access token expires in < 2 min
    FE->>BE: POST /api/auth/refresh (HttpOnly cookie auto-sent)
    BE->>BE: Extract refresh token from cookie
    BE->>DB: Find refresh token by hash
    
    alt Token valid
        BE->>DB: Revoke old refresh token (rotation)
        BE->>JWT: Generate new access + refresh tokens
        BE->>DB: Store new refresh token hash
        BE-->>FE: 200 {accessToken} + Set-Cookie: new refreshToken
        FE->>FE: Update in-memory access token
    else Token expired/revoked
        BE-->>FE: 401 Unauthorized
        FE->>FE: Clear auth state
        FE->>FE: Redirect to login page
    end
```

---

## 3. Data Schema

### 3.1 ERD (Liên quan đến Auth)

```mermaid
erDiagram
    USERS {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar phone
        varchar status "ACTIVE|LOCKED|DISABLED"
        int failed_login_count
        timestamp locked_until_at
        boolean mfa_enabled
        varchar mfa_method "NONE|OTP|TOTP"
        varchar trusted_device_hash
        timestamp created_at
        timestamp updated_at
    }

    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token_hash
        boolean revoked
        timestamp expires_at
        timestamp created_at
    }

    TOKEN_BLACKLIST {
        bigint id PK
        varchar token_jti UK
        timestamp expires_at
        timestamp created_at
    }

    USERS ||--o{ REFRESH_TOKENS : "has many"
```

### 3.2 Redis Key Schema

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `rate:login:ip:{clientIp}` | String (counter) | 60s | Login rate limit per IP |
| `rate:login:user:{username}` | String (counter) | 300s | Login rate limit per username |
| `mfa:verify:attempts:{userId}:OTP` | String (counter) | 900s | MFA OTP attempt counter |
| `mfa:verify:lock:{userId}:OTP` | String ("locked") | 1800s | MFA OTP lock |

---

## 4. API Specification

### 4.1 POST /api/auth/login

**Request:**
```json
{
  "username": "admin01",
  "password": "SecureP@ss123",
  "domainCode": "default",
  "captchaToken": null,
  "trustedDeviceHash": null
}
```

**Response (Success — 200):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "— set via HttpOnly cookie —",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "userId": 1,
  "username": "admin01",
  "activeDomain": "default",
  "roles": ["ADMIN"],
  "permissions": ["user:read", "user:write", "role:manage"]
}
```

**Response Headers (Success):**
```
Set-Cookie: refresh_token=<opaque_token>; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800
```

**Error Responses:**

| Status | Error Code | Description |
|--------|-----------|-------------|
| 401 | `INVALID_CREDENTIALS` | Username/password không đúng |
| 423 | `ACCOUNT_LOCKED` | Account bị lock, kèm `retryAfter` |
| 428 | `CAPTCHA_REQUIRED` | CAPTCHA cần được hoàn thành |
| 429 | `TOO_MANY_REQUESTS` | Rate limit exceeded, kèm `Retry-After` header |
| 403 | `PASSWORD_EXPIRED` | Password hết hạn, cần đổi |

### 4.2 POST /api/auth/refresh

**Request:** No body — refresh token sent via HttpOnly cookie

**Response (Success — 200):**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

---

## 5. Screen Flow

```mermaid
graph LR
    A["Login Page<br/>/sign-in"] -->|"Success"| B["Dashboard Home<br/>/"]
    A -->|"MFA Required"| C["MFA Verify<br/>/sign-in/mfa"]
    C -->|"OTP Verified"| B
    A -->|"Account Locked"| D["Lock Screen<br/>(inline trong Login Page)"]
    D -->|"Timer hết"| A
    A -->|"CAPTCHA Required"| E["CAPTCHA Widget<br/>(inline trong Login Page)"]
    E -->|"CAPTCHA solved"| A
    B -->|"Token expired<br/>+ Refresh failed"| A
    B -->|"Sign Out"| A

    style A fill:#1976D2,color:#fff
    style B fill:#4CAF50,color:#fff
    style C fill:#FF9800,color:#fff
    style D fill:#F44336,color:#fff
    style E fill:#9C27B0,color:#fff
```

---

## 6. Agent Implementation Notes

### 6.1 Frontend Changes

| File | Action | Description |
|------|--------|-------------|
| `src/@auth/authApi.ts` | **MODIFY** | Thay `mock/auth/*` → `/api/auth/*`, thêm cookie handling |
| `src/@auth/services/jwt/JwtAuthProvider.tsx` | **MODIFY** | In-memory token, cookie-based refresh, error handling |
| `src/app/(public)/(auth)/components/forms/SignInPageForm.tsx` | **MODIFY** | Email → Username field |
| `src/app/(public)/(auth)/components/tabs/sign-in/JwtSignInTab.tsx` | **MODIFY** | Add error states (locked, captcha, rate limit) |
| `src/@auth/services/jwt/components/JwtSignInForm.tsx` | **MODIFY** | Add real API integration + error display |
| `src/utils/api.ts` | **MODIFY** | Add credentials: 'include' cho cookie, CSRF header |
| `src/@auth/services/jwt/utils/jwtUtils.ts` | **MODIFY** | Token expiry check utility |

### 6.2 Backend Changes

| File | Action | Description |
|------|--------|-------------|
| `shared/config/SecurityConfig.kt` | **MODIFY** | Add CORS config cho cookie, CSRF cookie-to-header |
| `auth/adapter/in/web/AuthController.kt` | **MODIFY** | Set HttpOnly cookie cho refresh token |
| `shared/config/RateLimitConfig.kt` | **NEW** | Bucket4j + Redis configuration |
| `auth/adapter/in/web/filter/LoginRateLimitFilter.kt` | **NEW** | Rate limit filter cho `/api/auth/login` |
| `shared/config/SecurityProperties.kt` | **MODIFY** | Add rate limit properties |
| `shared/config/WebConfig.kt` | **MODIFY** | Add security headers (HSTS, CSP) |

### 6.3 Implementation Phases

| Phase | Scope | Priority |
|-------|-------|----------|
| **Phase 1** | FE: Username field + Real API integration | 🔴 P0 |
| **Phase 2** | BE: Rate limiting (Bucket4j) + FE error handling | 🔴 P0 |
| **Phase 3** | BE+FE: HttpOnly cookie cho refresh token | 🔴 P0 |
| **Phase 4** | BE: Security headers (HSTS, CSP) + CORS | 🟡 P1 |
| **Phase 5** | FE: CAPTCHA widget integration | 🟡 P1 |
| **Phase 6** | BE: Access token TTL reduction (30 → 15 min) | 🟡 P1 |
| **Phase 7** | Edge: WAF/CDN recommendation (documentation only) | 🟢 P2 |

---

## 7. Security Checklist

- [ ] HTTPS enforced (TLS 1.3)
- [ ] HSTS header set
- [ ] CSP header configured
- [ ] Refresh token in HttpOnly cookie
- [ ] Access token in-memory only
- [ ] Rate limiting on login endpoint (Bucket4j)
- [ ] Account lockout after 3 failed attempts
- [ ] CAPTCHA integration
- [ ] Password hashing with BCrypt (strength ≥ 12)
- [ ] JWT RS256 signing
- [ ] Token rotation on refresh
- [ ] Token blacklist for revocation
- [ ] No credentials in URL (POST only)
- [ ] Input validation (Zod FE + Bean Validation BE)
- [ ] Audit logging for login events
