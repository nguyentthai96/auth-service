# Technical Specification: Auth Core Features (FR-001 → FR-004)

## 1. Architecture Overview

```mermaid
graph TB
    subgraph "Client Layer"
        FE["Frontend/Mobile App"]
    end

    subgraph "auth-service"
        AC["AuthController"]
        MFA["MfaController"]
        SSO["SsoController"]

        AS["AuthService"]
        MS["MfaService"]
        TS["TotpService"]
        OS["OtpService"]
        CS["CaptchaVerifier"]
        SA["SsoAdapter"]
        JWT["JwtService (RS256)"]
        PPS["PasswordPolicyService"]

        subgraph "Data Layer"
            UE["UserEntity"]
            PPE["PasswordPolicyEntity"]
            PHE["PasswordHistoryEntity"]
            UIE["UserIdentityEntity"]
        end
    end

    subgraph "External"
        Redis["Redis"]
        Kafka["Kafka"]
        IdP["Keycloak / Google / MSFT"]
        CAPTCHA["Turnstile / hCaptcha"]
    end

    subgraph "Downstream"
        AccSvc["account-service"]
    end

    FE --> AC
    FE --> MFA
    FE --> SSO

    AC --> AS
    MFA --> MS
    SSO --> SA

    AS --> JWT
    AS --> PPS
    MS --> TS
    MS --> OS
    MS --> CS

    SA --> IdP
    SA --> Kafka
    Kafka --> AccSvc

    OS --> Redis
    MS --> Redis
    JWT --> Redis
end
```

## 2. Entity Relationship Diagram

```mermaid
erDiagram
    users ||--o{ user_identities : "has SSO identities"
    users ||--o{ password_history : "has password history"
    domains ||--o| password_policies : "has password policy"
    users ||--o{ refresh_tokens : "has tokens"
    users ||--o{ token_blacklist : "has blacklisted tokens"

    users {
        bigint id PK
        varchar username UK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar phone
        varchar status
        int failed_login_count
        timestamptz locked_until_at
        boolean mfa_enabled
        varchar mfa_method
        varchar totp_secret_encrypted
        varchar trusted_device_hash
        timestamptz password_changed_at
        int version
    }

    user_identities {
        bigint id PK
        bigint user_id FK
        varchar provider
        varchar provider_sub UK
        varchar provider_email
        varchar provider_name
        timestamptz linked_at
    }

    password_policies {
        bigint id PK
        bigint domain_id FK
        int min_length
        int max_length
        boolean require_uppercase
        boolean require_lowercase
        boolean require_digit
        boolean require_special
        int min_character_types
        int history_count
        int max_age_days
        int lockout_threshold
        int lockout_duration_minutes
    }

    password_history {
        bigint id PK
        bigint user_id FK
        varchar password_hash
        timestamptz created_at
    }
end
```

## 3. Sequence Diagrams

### 3.1 Two-Phase MFA Login Flow

```mermaid
sequenceDiagram
    actor User
    participant FE as Frontend
    participant AC as AuthController
    participant AS as AuthService
    participant MS as MfaService
    participant Redis
    participant JWT as JwtService

    User->>FE: Enter username/password
    FE->>AC: POST /api/auth/login
    AC->>AS: login(request)
    AS->>AS: validate credentials
    AS->>AS: check user.mfaEnabled

    alt MFA NOT enabled
        AS->>JWT: generateTokens()
        JWT-->>AS: accessToken + refreshToken
        AS-->>AC: AuthResponse (full tokens)
        AC-->>FE: 200 OK
    else MFA enabled
        AS->>MS: initiateMfa(userId, method)
        MS->>Redis: SET otp:{userId}:{channel} = code, TTL=300s
        MS->>MS: send OTP via channel (SMS/Email) OR skip if TOTP
        MS-->>AS: mfaToken (JWT, 5min, type=mfa)
        AS-->>AC: MfaRequiredResponse (mfaToken, method)
        AC-->>FE: 200 OK (mfa_required)

        User->>FE: Enter OTP code
        FE->>AC: POST /api/auth/mfa/verify (mfaToken + code)
        AC->>MS: verifyMfa(mfaToken, code)
        MS->>Redis: GET otp:{userId}:{channel}
        MS->>MS: validate code + attempts

        alt Code valid
            MS->>Redis: DEL otp:{userId}:{channel}
            MS->>JWT: generateTokens()
            JWT-->>MS: accessToken + refreshToken
            MS-->>AC: AuthResponse (full tokens)
            AC-->>FE: 200 OK
        else Code invalid (attempt < 3)
            MS->>Redis: INCR attempts
            MS-->>AC: MFA_CODE_INVALID
            AC-->>FE: 401 (retry)
        else Max attempts exceeded
            MS->>Redis: DEL otp:{userId}:{channel}
            MS-->>AC: MFA_MAX_ATTEMPTS
            AC-->>FE: 403 (login again)
        end
    end
```

### 3.2 SSO OAuth2 Login + JIT Provisioning

```mermaid
sequenceDiagram
    actor User
    participant FE as Frontend
    participant SC as SsoController
    participant SA as SsoAdapter
    participant IdP as Keycloak/Google
    participant DB as PostgreSQL
    participant Kafka

    User->>FE: Click "Login with Google"
    FE->>IdP: Redirect to authorization endpoint
    IdP->>User: Show consent screen
    User->>IdP: Approve
    IdP->>FE: Redirect callback with auth code

    FE->>SC: POST /api/auth/sso/callback (code, provider)
    SC->>SA: handleCallback(code, provider)
    SA->>IdP: Exchange code for tokens (POST /token)
    IdP-->>SA: id_token + access_token
    SA->>SA: Parse ID token claims (sub, email, name)

    SA->>DB: findByProviderAndSub(provider, sub)

    alt User exists
        DB-->>SA: UserIdentityEntity
        SA->>SA: Generate internal JWT
    else User not exists
        SA->>DB: Check domain config (autoProvision?)

        alt Auto-provision ON
            SA->>DB: Create UserEntity + UserIdentityEntity
            SA->>Kafka: Publish iam.user.sso_provisioned
            SA->>SA: Generate internal JWT
        else Auto-provision OFF
            SA-->>SC: SSO_USER_NOT_PROVISIONED error
        end
    end

    SA-->>SC: AuthResponse
    SC-->>FE: 200 OK (accessToken, refreshToken)
```

### 3.3 Password Change with Policy Validation

```mermaid
sequenceDiagram
    actor User
    participant AC as AuthController
    participant PPS as PasswordPolicyService
    participant Passay as PassayValidator
    participant DB as PostgreSQL

    User->>AC: POST /api/auth/change-password (old, new)
    AC->>AC: Get userId from SecurityContext
    AC->>PPS: changePassword(userId, oldPwd, newPwd)

    PPS->>DB: Load user + domain membership
    PPS->>DB: Load PasswordPolicy for domain
    PPS->>Passay: Build rules from policy config
    PPS->>Passay: validate(newPwd)

    alt Complexity check fails
        Passay-->>PPS: List<RuleViolation>
        PPS-->>AC: 400 (violations)
    else Complexity OK
        PPS->>DB: Load last N password hashes
        PPS->>PPS: BCrypt.matches(newPwd, each history hash)

        alt Password reused
            PPS-->>AC: 400 (PASSWORD_RECENTLY_USED)
        else Not reused
            PPS->>DB: Update user.passwordHash
            PPS->>DB: Insert password_history
            PPS->>DB: Prune old history (> historyCount)
            PPS->>DB: Update user.passwordChangedAt = now()
            PPS-->>AC: 200 OK
        end
    end
```

## 4. API Specification

### 4.1 MFA Endpoints

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/api/auth/mfa/verify` | Verify OTP/TOTP code | mfaToken |
| POST | `/api/auth/mfa/totp/setup` | Setup TOTP (get QR code) | JWT |
| POST | `/api/auth/mfa/totp/confirm` | Confirm TOTP setup | JWT |
| POST | `/api/auth/mfa/resend` | Resend OTP via SMS/Email | mfaToken |
| PUT | `/api/auth/mfa/settings` | Enable/disable MFA, change method | JWT |

### 4.2 SSO Endpoints

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/api/auth/sso/callback` | Handle OAuth2 callback | Public |
| GET | `/api/auth/sso/providers` | List available SSO providers for domain | Public |
| POST | `/api/auth/sso/link` | Link existing account to SSO identity | JWT |
| DELETE | `/api/auth/sso/unlink/{provider}` | Unlink SSO identity | JWT |

### 4.3 Token Endpoints

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/api/auth/introspect` | Token introspection (RFC 7662) | Internal |
| GET | `/.well-known/jwks.json` | Public key endpoint (JWKS) | Public |
| POST | `/api/auth/sessions/{userId}/revoke-all` | Force logout all sessions | ADMIN |

### 4.4 Password Policy Endpoints

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/api/admin/domains/{id}/password-policy` | Get domain password policy | ADMIN |
| PUT | `/api/admin/domains/{id}/password-policy` | Update domain password policy | ADMIN |
| POST | `/api/auth/change-password` | Change password (with policy validation) | JWT |
| POST | `/api/auth/forgot-password` | Initiate password reset | Public |
| POST | `/api/auth/reset-password` | Complete password reset with token | Public |

## 5. Data Schema Changes (Migration V2)

### New Tables

```sql
-- User SSO Identities
CREATE TABLE user_identities (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    provider        VARCHAR(50) NOT NULL,
    provider_sub    VARCHAR(255) NOT NULL,
    provider_email  VARCHAR(255),
    provider_name   VARCHAR(200),
    linked_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_sub)
);

-- Password Policies per Domain
CREATE TABLE password_policies (
    id                      BIGINT PRIMARY KEY,
    domain_id               BIGINT NOT NULL UNIQUE REFERENCES domains(id),
    min_length              INT NOT NULL DEFAULT 8,
    max_length              INT NOT NULL DEFAULT 128,
    require_uppercase       BOOLEAN NOT NULL DEFAULT TRUE,
    require_lowercase       BOOLEAN NOT NULL DEFAULT TRUE,
    require_digit           BOOLEAN NOT NULL DEFAULT TRUE,
    require_special         BOOLEAN NOT NULL DEFAULT FALSE,
    min_character_types     INT NOT NULL DEFAULT 3,
    history_count           INT NOT NULL DEFAULT 5,
    max_age_days            INT NOT NULL DEFAULT 90,
    lockout_threshold       INT NOT NULL DEFAULT 5,
    lockout_duration_minutes INT NOT NULL DEFAULT 15,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Password History
CREATE TABLE password_history (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_password_history_user ON password_history(user_id);
```

### Alter Existing Tables

```sql
-- Add MFA and SSO columns to users table
ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_method VARCHAR(20) DEFAULT 'NONE';
ALTER TABLE users ADD COLUMN totp_secret_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN trusted_device_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;
```

## 6. Agent Implementation Notes

### Dependencies cần thêm (build.gradle.kts)
```kotlin
implementation("dev.samstevens.totp:totp:1.7.1")
implementation("org.passay:passay:1.6.4")
implementation("org.springframework.boot:spring-boot-starter-data-redis")
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
```

### Cấu hình application.yml bổ sung
```yaml
app:
  security:
    mfa:
      otp-ttl-seconds: 300
      max-attempts: 3
      totp-window: 1
    captcha:
      provider: turnstile  # turnstile | hcaptcha | recaptcha
      secret-key: ${CAPTCHA_SECRET_KEY}
      site-key: ${CAPTCHA_SITE_KEY}
    sso:
      enabled: false  # Enable per domain in domains.config
  jwt:
    algorithm: RS256
    private-key-path: ${JWT_PRIVATE_KEY_PATH}
    public-key-path: ${JWT_PUBLIC_KEY_PATH}
    key-id: "auth-service-key-1"
```

### Key Design Decisions
1. **MFA Token**: Short-lived JWT (5min, type=mfa, sub=userId) — NOT stored in DB, stateless
2. **OTP Storage**: Redis key `otp:{userId}:{channel}` với TTL — atomic, tự cleanup
3. **TOTP Secret**: AES-256 encrypted in DB — decrypt tại runtime khi verify
4. **CAPTCHA**: Pluggable interface `CaptchaVerifier` — swap provider via config
5. **RS256 Key**: RSA 2048-bit key pair, JWKS auto-exposed at `/.well-known/jwks.json`
6. **Password Validator Cache**: `ConcurrentHashMap<domainId, PasswordValidator>` — invalidate on policy update
