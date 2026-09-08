# Design: argon2id-password-encoder

> **Change**: argon2id-password-encoder
> **Type**: EXTEND
> **Flow**: Command
> **Direction**: DelegatingPasswordEncoder + PasswordEncoderAutoConfiguration (Brainstorm DD-001→DD-006)
> **Date**: 2026-09-08

---

## 1. Architecture Overview

```
    ┌──────────────────────────────────────────┐
    │            application.yml               │
    │  app.security.password:                  │
    │    algorithm: argon2id                   │
    │    bcrypt-strength: 12                   │
    │    max-concurrent-hashes: 20             │
    │    argon2:                               │
    │      salt-length: 16                     │
    │      hash-length: 32                     │
    │      parallelism: 1                      │
    │      memory-cost: 65536                  │
    │      iterations: 3                       │
    └────────────────┬─────────────────────────┘
                     │ @ConfigurationProperties
    ┌────────────────▼─────────────────────────┐
    │    SecurityProperties.PasswordProperties │
    │    ├── algorithm: String                 │
    │    ├── bcryptStrength: Int               │
    │    ├── maxConcurrentHashes: Int           │
    │    └── argon2: Argon2Properties          │
    └────────────────┬─────────────────────────┘
                     │
    ┌────────────────▼─────────────────────────┐
    │   PasswordEncoderAutoConfiguration       │
    │   @Configuration                         │
    │   ┌──────────────────────────────────┐   │
    │   │  @Bean passwordEncoder()         │   │
    │   │  ┌───────────────────────────┐   │   │
    │   │  │ ConcurrencyLimited        │   │   │
    │   │  │ PasswordEncoder           │   │   │
    │   │  │  ┌────────────────────┐   │   │   │
    │   │  │  │ Delegating         │   │   │   │
    │   │  │  │ PasswordEncoder    │   │   │   │
    │   │  │  │ ├─ {argon2id} ★    │   │   │   │
    │   │  │  │ ├─ {bcrypt}        │   │   │   │
    │   │  │  │ └─ default: bcrypt │   │   │   │
    │   │  │  └────────────────────┘   │   │   │
    │   │  └───────────────────────────┘   │   │
    │   └──────────────────────────────────┘   │
    └────────────────┬─────────────────────────┘
                     │ PasswordEncoder (interface)
         ┌───────────┴───────────┐
         │                       │
    ┌────▼────────┐    ┌────────▼───────────┐
    │ Token       │    │ PasswordPolicy     │
    │ Generator   │    │ Service            │
    │ (no change) │    │ (no change)        │
    └─────────────┘    └────────────────────┘
         │
    ┌────▼──────────────────────────────────────┐
    │  LoginHandler                              │
    │  ├── tokenGenerator.matchesPassword()      │
    │  ├── ★ passwordUpgradeService              │
    │  │     .upgradeIfNeeded(user, rawPwd)       │
    │  └── tokenGenerator.generateAuthResponse() │
    └────────────────────────────────────────────┘
```

## 2. Component Design

### 2.1 PasswordEncoderAutoConfiguration (NEW)

**Package**: `com.ntt.authservice.shared.config`
**Purpose**: Bean auto-config cho PasswordEncoder — replaces SecurityConfig.passwordEncoder()

```kotlin
@Configuration
class PasswordEncoderAutoConfiguration(
    private val securityProperties: SecurityProperties
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        val passwordProps = securityProperties.password

        val encoders = mapOf<String, PasswordEncoder>(
            "bcrypt" to BCryptPasswordEncoder(passwordProps.bcryptStrength),
            "argon2id" to Argon2PasswordEncoder(
                passwordProps.argon2.saltLength,
                passwordProps.argon2.hashLength,
                passwordProps.argon2.parallelism,
                passwordProps.argon2.memoryCost,
                passwordProps.argon2.iterations
            )
        )

        val delegate = DelegatingPasswordEncoder(passwordProps.algorithm, encoders).apply {
            setDefaultPasswordEncoderForMatches(
                BCryptPasswordEncoder(passwordProps.bcryptStrength)
            )
        }

        return ConcurrencyLimitedPasswordEncoder(
            delegate = delegate,
            maxConcurrent = passwordProps.maxConcurrentHashes
        )
    }
}
```

**Design Decisions (from Brainstorm):**
- DD-002: Semaphore wraps both encode() + matches() — Argon2id matches also allocates 64MB
- DD-006: setDefaultPasswordEncoderForMatches = BCrypt — safety net for bare hashes

### 2.2 ConcurrencyLimitedPasswordEncoder (NEW — Inner class or standalone)

**Package**: `com.ntt.authservice.shared.config`
**Purpose**: Decorator limiting concurrent password operations to prevent OOM

```kotlin
class ConcurrencyLimitedPasswordEncoder(
    private val delegate: PasswordEncoder,
    maxConcurrent: Int = 20
) : PasswordEncoder {

    private val semaphore = Semaphore(maxConcurrent)

    override fun encode(rawPassword: CharSequence): String {
        semaphore.acquire()
        return try { delegate.encode(rawPassword) }
        finally { semaphore.release() }
    }

    override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean {
        semaphore.acquire()
        return try { delegate.matches(rawPassword, encodedPassword) }
        finally { semaphore.release() }
    }

    override fun upgradeEncoding(encodedPassword: String): Boolean {
        // O(1) string check — no semaphore needed
        return delegate.upgradeEncoding(encodedPassword)
    }
}
```

### 2.3 PasswordUpgradeService (NEW)

**Package**: `com.ntt.authservice.auth.application`
**Purpose**: Rehash-on-login — detect legacy hash, re-encode with default encoder

```kotlin
@Service
class PasswordUpgradeService(
    private val passwordEncoder: PasswordEncoder,
    private val userPort: UserPort,
    private val auditLogService: AuditLogService,
    private val meterRegistry: MeterRegistry
) {
    private val rehashCounter = Counter.builder("password.migration.rehash.total")
        .description("Total password rehash operations from legacy algorithm")
        .register(meterRegistry)

    fun upgradeIfNeeded(user: User, rawPassword: String) {
        if (passwordEncoder.upgradeEncoding(user.passwordHash.value)) {
            val newHash = passwordEncoder.encode(rawPassword)
            user.upgradePasswordHash(PasswordHash(newHash))

            auditLogService.logEvent(
                user.id.value,
                AuditAction.PASSWORD_REHASHED,
                "User", user.id.value.toString(),
                "algorithm=argon2id"
            )
            rehashCounter.increment()

            log.info("Password rehashed for userId={} from legacy to default algorithm", user.id.value)
        }
    }
}
```

**Design Decisions (from Brainstorm):**
- DD-001: Called in LoginHandler after resetFailedLogins(), before userPort.save()
- DD-005: Uses User domain model (not UserEntity) — Clean Architecture compliant
- User domain model saves via existing userPort.save() call in LoginHandler

### 2.4 SecurityProperties.PasswordProperties (MODIFY)

```kotlin
data class PasswordProperties(
    /** Active algorithm — "argon2id" (default) or "bcrypt" (legacy). */
    val algorithm: String = "argon2id",
    val bcryptStrength: Int = 12,
    /** Maximum concurrent password hash operations — prevents OOM with Argon2id. */
    val maxConcurrentHashes: Int = 20,
    /** Argon2id tuning parameters (OWASP 2024). */
    val argon2: Argon2Properties = Argon2Properties(),
    val maxFailedAttempts: Int = 3,
    val lockDurationMinutes: Int = 15
) {
    data class Argon2Properties(
        val saltLength: Int = 16,
        val hashLength: Int = 32,
        val parallelism: Int = 1,
        val memoryCost: Int = 65536,
        val iterations: Int = 3
    )
}
```

### 2.5 SecurityConfig (MODIFY)

- **Remove**: `passwordEncoder()` bean definition (lines 110-113)
- **Remove**: BCrypt imports (`BCryptPasswordEncoder`, `PasswordEncoder`)
- Bean moves to `PasswordEncoderAutoConfiguration`

### 2.6 LoginHandler (MODIFY)

**Change**: Inject `PasswordUpgradeService`, call after password verification success.

```kotlin
class LoginHandler(
    // ... existing deps ...
    private val passwordUpgradeService: PasswordUpgradeService  // NEW
) : CommandHandler<LoginCommand, LoginResult> {

    override fun handle(command: LoginCommand): LoginResult {
        // ... existing code through password verification ...

        // Reset failed login count on success
        user.resetFailedLogins()

        // ★ Upgrade password hash if using legacy algorithm (best-effort)
        passwordUpgradeService.upgradeIfNeeded(user, command.password)

        userPort.save(user)  // saves both resetFailedLogins + upgraded hash

        // ... rest of existing code ...
    }
}
```

### 2.7 User Domain Model (MODIFY)

```kotlin
// Add method to User domain model
fun upgradePasswordHash(newHash: PasswordHash) {
    this.passwordHash = newHash
}
```

## 3. Database Design

### Migration: V__add_password_hash_prefix.sql

```sql
-- Add {bcrypt} prefix to existing hashes for DelegatingPasswordEncoder compatibility
-- Tables: users, password_history
-- Safety: WHERE clause ensures idempotency (skip already-prefixed hashes)

UPDATE users
SET password_hash = CONCAT('{bcrypt}', password_hash)
WHERE password_hash NOT LIKE '{%}%'
  AND password_hash LIKE '$2a$%';

UPDATE password_history
SET password_hash = CONCAT('{bcrypt}', password_hash)
WHERE password_hash NOT LIKE '{%}%'
  AND password_hash LIKE '$2a$%';
```

**Column size verification**: VARCHAR(255) sufficient for both formats:
- BCrypt prefixed: `{bcrypt}$2a$12$...` = ~67 chars
- Argon2id: `{argon2id}$argon2id$v=19$m=65536,t=3,p=1$...$...` = ~110 chars

## 4. Sequence Diagrams

### 4.1 Login with Rehash (BCrypt → Argon2id)

```
User → LoginHandler: POST /api/auth/login
LoginHandler → UserPort: findByUsernameAndActive(username)
UserPort → LoginHandler: User (hash = "{bcrypt}$2a$12$...")

LoginHandler → TokenGenerator: matchesPassword(raw, hash)
TokenGenerator → PasswordEncoder: matches(raw, "{bcrypt}$2a$12$...")
PasswordEncoder → BCryptPasswordEncoder: matches(raw, "$2a$12$...")
BCryptPasswordEncoder → PasswordEncoder: true
PasswordEncoder → TokenGenerator: true

LoginHandler → User: resetFailedLogins()
LoginHandler → PasswordUpgradeService: upgradeIfNeeded(user, rawPassword)
PasswordUpgradeService → PasswordEncoder: upgradeEncoding("{bcrypt}$2a$12$...")
PasswordEncoder → PasswordUpgradeService: true (not default argon2id)
PasswordUpgradeService → PasswordEncoder: encode(rawPassword)
PasswordEncoder → Argon2PasswordEncoder: encode(rawPassword) [64MB alloc]
Argon2PasswordEncoder → PasswordEncoder: "{argon2id}$argon2id$v=19$..."
PasswordUpgradeService → User: upgradePasswordHash(newHash)
PasswordUpgradeService → AuditLogService: logEvent(PASSWORD_REHASHED)
PasswordUpgradeService → MeterRegistry: rehashCounter.increment()

LoginHandler → UserPort: save(user) [resetFailedLogins + newHash]
LoginHandler → TokenGenerator: generateAuthResponse(user)
TokenGenerator → LoginHandler: JWT tokens
LoginHandler → User: 200 OK + tokens
```

### 4.2 Normal Login (Argon2id — post migration)

```
User → LoginHandler: POST /api/auth/login
LoginHandler → UserPort: findByUsernameAndActive(username)
UserPort → LoginHandler: User (hash = "{argon2id}$argon2id$...")

LoginHandler → TokenGenerator: matchesPassword(raw, hash)
TokenGenerator → PasswordEncoder: matches(raw, "{argon2id}...")
PasswordEncoder → Argon2PasswordEncoder: matches(raw, "$argon2id$...")
Argon2PasswordEncoder → PasswordEncoder: true

LoginHandler → PasswordUpgradeService: upgradeIfNeeded(user, rawPassword)
PasswordUpgradeService → PasswordEncoder: upgradeEncoding("{argon2id}...")
PasswordEncoder → PasswordUpgradeService: false (already default)
// No rehash needed — skip

LoginHandler → TokenGenerator: generateAuthResponse(user)
```

## 5. Configuration

### application-security.yml (additions)

```yaml
app:
  security:
    password:
      algorithm: argon2id           # "argon2id" (default) or "bcrypt"
      bcrypt-strength: 12           # Legacy BCrypt fallback
      max-concurrent-hashes: 20     # Semaphore limit — prevent OOM
      argon2:
        salt-length: 16             # Bytes
        hash-length: 32             # Bytes
        parallelism: 1              # Threads (OWASP: 1)
        memory-cost: 65536          # KiB (64 MB, OWASP recommended)
        iterations: 3               # Time cost (OWASP: 3)
```

### Profile overrides

```yaml
# application-dev.yml — lower memory for development
app.security.password:
  algorithm: bcrypt                 # Use BCrypt in dev for speed
  max-concurrent-hashes: 5

# application-staging.yml — medium params
app.security.password:
  algorithm: argon2id
  argon2.memory-cost: 32768         # 32 MB for staging
  max-concurrent-hashes: 10
```

## 6. Error Handling

Không cần exception classes mới. Tất cả error scenarios đã covered bởi existing exceptions:
- `InvalidCredentialsException` — wrong password (unchanged)
- `AccountLockedException` — max attempts (unchanged)
- Password encode/matches failures → tự throw từ Spring Security encoder

## 7. Rollback Strategy

1. Set `app.security.password.algorithm: bcrypt` → restart
2. New passwords → BCrypt; Argon2id hashes vẫn verify (DelegatingPasswordEncoder)
3. No DB rollback needed — prefixed hashes work with both algorithms
