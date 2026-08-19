# Service Structure: anonymous-login-optimization

> _Generated: 2025-01-20_
> Candidate Service: auth-service (`src/main/kotlin/com/ntt/authservice/`)

---

## 1. Package Structure

```
com.ntt.authservice/
├── auth/                                      # Auth bounded context
│   ├── domain/
│   │   ├── model/
│   │   │   ├── User.kt                        # Domain entity
│   │   │   ├── UserStatus.kt                  # Status sealed class
│   │   │   ├── AuthToken.kt                   # Domain value object
│   │   │   └── vo/                             # Value objects
│   │   │       ├── UserId.kt
│   │   │       ├── PasswordHash.kt
│   │   │       ├── Email.kt
│   │   │       └── DomainCode.kt
│   │   └── service/
│   │       └── TokenHasher.kt
│   ├── application/                            # Application layer
│   │   ├── JwtService.kt                      # JWT token generation/validation
│   │   ├── AuthService.kt                     # Legacy auth service
│   │   ├── LoginResult.kt                     # Sealed result type
│   │   ├── LoginRateLimitService.kt           # Multi-dimensional rate limiting
│   │   ├── MfaRateLimitService.kt             # MFA rate limiting
│   │   ├── LoginSessionService.kt             # Session recording
│   │   ├── SessionPolicyService.kt            # Session policy enforcement
│   │   ├── SessionCleanupScheduler.kt         # Scheduled cleanup
│   │   ├── OtpService.kt                      # OTP management
│   │   ├── TotpService.kt                     # TOTP authenticator
│   │   ├── MfaService.kt                      # MFA orchestration
│   │   ├── SsoAdapter.kt                      # SSO integration
│   │   ├── DomainLookupService.kt             # Domain resolution
│   │   ├── AltchaCaptchaVerifier.kt           # CAPTCHA verification
│   │   ├── PasswordPolicyService.kt           # Password policy
│   │   ├── cipher/                             # E2EE services
│   │   │   ├── EncryptedAuditService.kt
│   │   │   ├── X25519KeyExchangeServiceImpl.kt
│   │   │   ├── CipherVersionNegotiator.kt
│   │   │   └── DecryptionVaultService.kt
│   │   ├── command/                            # CQRS commands
│   │   │   ├── LoginCommand.kt
│   │   │   ├── LoginHandler.kt
│   │   │   ├── RegisterCommand.kt
│   │   │   ├── RegisterHandler.kt
│   │   │   ├── RefreshTokenCommand.kt
│   │   │   ├── RefreshTokenHandler.kt
│   │   │   ├── SwitchDomainCommand.kt
│   │   │   ├── SwitchDomainHandler.kt
│   │   │   ├── RevokeSessionsCommand.kt
│   │   │   ├── RevokeSessionsHandler.kt
│   │   │   ├── TokenGenerator.kt
│   │   │   └── AuthDomainEvents.kt
│   │   ├── query/                              # CQRS queries
│   │   │   ├── BuildAuthResponseQuery.kt
│   │   │   └── BuildAuthResponseHandler.kt
│   │   ├── event/                              # Domain events
│   │   │   ├── RateLimitExceededEvent.kt
│   │   │   └── NewDeviceLoginEvent.kt
│   │   └── port/out/                           # Outbound ports
│   │       ├── UserPort.kt
│   │       ├── DomainPort.kt
│   │       ├── TokenStore.kt
│   │       ├── CaptchaGateway.kt
│   │       ├── PermissionCache.kt
│   │       ├── SsoGateway.kt
│   │       └── EventPublisher.kt
│   └── adapter/
│       ├── in/
│       │   ├── web/                            # REST controllers
│       │   │   ├── CqrsAuthController.kt
│       │   │   ├── AuthController.kt
│       │   │   ├── SessionController.kt
│       │   │   ├── AdminSessionController.kt
│       │   │   ├── TokenController.kt
│       │   │   ├── MfaController.kt
│       │   │   ├── CaptchaController.kt
│       │   │   ├── SsoController.kt
│       │   │   ├── KeyExchangeController.kt
│       │   │   ├── AccountLifecycleController.kt
│       │   │   ├── RateLimitAdminController.kt
│       │   │   ├── dto/                        # DTOs
│       │   │   │   ├── AuthResponse.kt
│       │   │   │   ├── RequestDtos.kt
│       │   │   │   ├── TokenDtos.kt
│       │   │   │   ├── MfaDtos.kt
│       │   │   │   └── SsoDtos.kt
│       │   │   └── filter/
│       │   │       └── LoginRateLimitFilter.kt
│       │   └── kafka/                          # Kafka consumers
│       └── out/
│           ├── persistence/                    # JPA persistence
│           │   ├── entity/
│           │   ├── mapper/
│           │   └── repository/
│           ├── cache/                          # Cache adapters
│           ├── cipher/                         # Cipher adapters
│           ├── event/                          # Event publishers
│           ├── gateway/                        # External gateway impls
│           ├── http/                           # HTTP clients
│           └── sso/                            # SSO provider impls
├── rbac/                                       # RBAC bounded context
│   ├── domain/model/
│   ├── domain/service/
│   ├── application/
│   ├── adapter/in/web/
│   └── adapter/out/persistence/
│       ├── entity/
│       │   └── PermissionEntities.kt           # Contains TokenBlacklistEntity
│       ├── mapper/
│       └── repository/
│           └── Repositories.kt                 # Contains TokenBlacklistRepository
├── pbac/                                       # PBAC bounded context
│   ├── application/
│   └── adapter/
└── shared/                                     # Cross-cutting concerns
    ├── config/
    │   ├── SecurityConfig.kt
    │   ├── SecurityProperties.kt
    │   ├── RedisConfig.kt
    │   ├── JacksonConfig.kt
    │   ├── JpaAuditingConfig.kt
    │   ├── I18nConfig.kt
    │   └── HttpClientConfig.kt
    ├── security/
    │   └── JwtAuthFilter.kt
    ├── exception/
    │   ├── AuthExceptions.kt
    │   ├── AuthCoreExceptions.kt
    │   ├── AuthErrorCode.kt
    │   ├── CipherExceptions.kt
    │   └── GlobalExceptionHandler.kt
    ├── persistence/
    │   └── VersionedAuditableEntity.kt
    ├── audit/
    │   └── AuditLogService.kt
    └── i18n/
        ├── DatabaseMessageSource.kt
        ├── I18nMessageEntity.kt
        └── I18nMessageRepository.kt
```

---

## 2. Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Controller | `{Feature}Controller` | `CqrsAuthController`, `MfaController`, `SessionController` |
| Command | `{Action}Command` | `LoginCommand`, `RegisterCommand`, `RefreshTokenCommand` |
| Command Handler | `{Action}Handler` | `LoginHandler`, `RegisterHandler`, `RefreshTokenHandler` |
| Service | `{Domain}Service` | `JwtService`, `LoginRateLimitService`, `SessionPolicyService` |
| Port | `{Domain}Port` / `{Domain}Gateway` / `{Domain}Cache` | `UserPort`, `CaptchaGateway`, `PermissionCache` |
| DTO | `{Name}Dto` / `{Name}RequestDto` / `{Name}Response` | `LoginRequestDto`, `AuthResponse`, `RegisterRequestDto` |
| Entity | `{Name}Entity` | `TokenBlacklistEntity` |
| Repository | `{Name}Repository` | `TokenBlacklistRepository` |
| Exception | `{Name}Exception` | `InvalidCredentialsException`, `RateLimitExceededException` |
| Error Code | `AuthErrorCode.{SCREAMING_SNAKE}` | `AuthErrorCode.INVALID_CREDENTIALS`, `AuthErrorCode.RATE_LIMITED` |
| Config | `{Name}Config` / `{Name}Properties` | `SecurityConfig`, `SecurityProperties` |
| Filter | `{Name}Filter` | `JwtAuthFilter`, `LoginRateLimitFilter` |
| Event | `{Name}Event` | `RateLimitExceededEvent`, `NewDeviceLoginEvent` |

---

## 3. Architecture Pattern

- **Hexagonal (Ports & Adapters)**: Clear separation via `port/out/` interfaces
- **CQRS**: Commands and Queries separated (`command/`, `query/` packages)
- **Clean Architecture**: Domain → Application → Adapter layers
- **DDD Bounded Contexts**: `auth`, `rbac`, `pbac` as separate modules
- **Shared Kernel**: `shared/` package for cross-cutting concerns

---

## 4. Technology Stack

| Technology | Usage | Evidence |
|-----------|-------|---------|
| Kotlin | Primary language | All `.kt` files |
| Spring Boot | Framework | `@RestController`, `@Service`, `@Component`, `@Configuration` |
| Spring Security | Auth framework | `SecurityFilterChain`, `OncePerRequestFilter`, `SecurityContextHolder` |
| JJWT (io.jsonwebtoken) | JWT library | `Jwts.builder()`, `Jwts.parser()`, `Jwts.SIG.RS256` |
| Spring Data JPA | Persistence | `JpaRepository<>` interfaces |
| Spring Data Redis | Caching/Rate limiting | `StringRedisTemplate`, `RedisTemplate<String, Any>` |
| Jakarta Validation | Input validation | `@field:NotBlank`, `@field:Email`, `@field:Size` |
| base-core | Shared library | `BusinessException`, `ErrorCodeBase`, `BaseControllerAdvice` |
| eventsourcing-utils | CQRS framework | `CommandHandler<C, R>`, `Command<R>` |
