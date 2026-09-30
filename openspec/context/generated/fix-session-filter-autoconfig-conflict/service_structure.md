# Service Structure

_Generated: 2026-09-30 | Services: base-security-starter, auth-service_

## base-security-starter Package Structure

```
components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/
├── autoconfigure/security/
│   ├── SecurityAutoConfiguration.kt          # [TARGET] Auto-configuration registering servlet filters
│   ├── SecurityProperties.kt                 # [TARGET] Configuration properties for app.security
│   ├── jwt/
│   │   ├── JwtTokenExtractor.kt              # JWT validation strategy interface
│   │   └── DefaultJwtTokenExtractor.kt       # Default JWT parsing implementation
│   ├── session/
│   │   ├── AbstractSessionValidationFilter.kt # Base servlet filter checking Bearer tokens at -1700
│   │   ├── DefaultSessionValidationFilter.kt  # Concrete filter delegating to JwtTokenExtractor
│   │   └── TokenValidationResult.kt           # Result wrapper (valid/invalid/principal)
│   └── repository/
│       └── EndpointSecurityRuleRepository.kt # JPA repository for dynamic authorization rules
└── security/
    └── filter/
        └── SecurityContextBridgeFilter.kt    # Bridges SecurityContext to RequestContextHolder (-50)
```

## auth-service Package Structure

```
services/auth-service/src/main/
├── kotlin/com/ntt/authservice/
│   ├── shared/
│   │   ├── config/
│   │   │   ├── SecurityConfig.kt             # Spring SecurityFilterChain definition
│   │   │   └── SecurityProperties.kt         # Auth-service specific security properties
│   │   ├── security/
│   │   │   └── JwtAuthFilter.kt              # Comprehensive Spring Security JWT PEP filter
│   │   └── web/
│   │       ├── BaseController.kt             # Base controller with response helpers
│   │       ├── RequestContext.kt             # Request context adapter
│   │       └── RequestContextFilter.kt       # Request context servlet filter
│   └── auth/
│       ├── adapter/in/web/
│       │   ├── CqrsAuthController.kt         # Auth endpoints (/auth/login, /auth/register)
│       │   └── filter/
│       │       ├── LoginRateLimitFilter.kt   # Rate limit filter for login
│       │       └── ServiceAuthFilter.kt      # Internal service JWT filter
│       └── application/
│           ├── command/
│           │   ├── LoginCommand.kt
│           │   └── LoginHandler.kt
│           └── pipeline/
│               └── AuthenticationPipeline.kt
└── resources/
    ├── application.yml                       # Imports base-core & security configs
    ├── application-base-core.yml             # Base-core shared configurations
    └── application-security.yml              # [TARGET] Auth-service security overrides
```

## Conventions
- **Language**: Kotlin 2.1+ / Java 21+ / Spring Boot 3.4+
- **Configuration**: YAML configuration via `@ConfigurationProperties(prefix = "app.security...")`
- **Filter Precedence**:
  - `OrderConstants.BASE_REQUEST_CONTEXT` = -2000
  - `OrderConstants.HTTP_LOGGING` = -1900
  - `OrderConstants.SESSION_VALIDATION` = -1700 (Base-core session validation filter)
  - `SecurityProperties.DEFAULT_FILTER_ORDER` = -100 (Spring Security FilterChainProxy)
