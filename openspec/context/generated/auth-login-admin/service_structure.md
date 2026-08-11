# Service Structure

_Generated: 2026-08-11_

## auth-service (Kotlin/Spring Boot)

### Detected Packages

- `auth/adapter/in/web`: Controllers (CqrsAuthController, AuthController, MfaController, SsoController, TokenController, RateLimitAdminController)
- `auth/adapter/in/web/dto`: DTOs (RequestDtos, AuthResponse, MfaDtos, SsoDtos, TokenDtos)
- `auth/adapter/in/kafka`: Kafka consumers (PermissionChangedConsumer)
- `auth/adapter/out/persistence`: Persistence adapters (UserPersistenceAdapter, DomainPersistenceAdapter, TokenStorePersistenceAdapter)
- `auth/adapter/out/gateway`: Gateway adapters (CaptchaGatewayAdapter)
- `auth/adapter/out/http`: HTTP clients (HttpCaptchaGateway, HttpSsoGateway)
- `auth/adapter/out/sso`: SSO adapters (OAuth2TokenExchanger)
- `auth/adapter/out/event`: Event publishing (SpringEventPublisher)
- `auth/adapter/out/cache`: Caching (CaffeinePermissionCache, MultiTierPermissionCache, InMemoryPermissionCache)
- `auth/application`: Services (AuthService, JwtService, MfaService, OtpService, TotpService, PasswordPolicyService, SsoAdapter, MfaRateLimitService, CaptchaVerifier, DomainLookupService)
- `auth/application/command`: CQRS handlers (LoginHandler, RegisterHandler, RefreshTokenHandler, RevokeSessionsHandler, SwitchDomainHandler, TokenGenerator)
- `auth/application/query`: Query handlers (BuildAuthResponseHandler)
- `auth/application/port/out`: Output ports (PermissionCache, CaptchaGateway)
- `shared/config`: Configuration (SecurityConfig, SecurityProperties, WebConfig)
- `shared/security`: Security filters (JwtAuthFilter)
- `shared/exception`: Exception handling (AuthExceptions, AuthErrorCode, GlobalExceptionHandler, AuthCoreExceptions)
- `shared/persistence`: Base entities (VersionedAuditableEntity)
- `rbac/adapter/in/web`: RBAC controllers (RbacControllers, RolePermissionController)
- `rbac/adapter/out/persistence/entity`: Entities (UserEntity, UserIdentityEntity, PasswordPolicyEntity, PasswordHistoryEntity, etc.)
- `rbac/adapter/out/persistence/repository`: Repositories (UserRepository, DomainRepository, etc.)
- `pbac/adapter/in/web`: PBAC controller (PolicyController)

### Not Found

- `auth/application/factory` — NOT FOUND (CQRS pattern dùng handlers thay vì factory)

### Naming Convention

- Controllers: `*Controller.kt` (e.g., CqrsAuthController, MfaController)
- Handlers: `*Handler.kt` (e.g., LoginHandler, RefreshTokenHandler)
- Services: `*Service.kt` (e.g., AuthService, JwtService)
- Entities: `*Entity.kt` (e.g., UserEntity, RefreshTokenEntity)
- DTOs: `*Request.kt`, `*Response.kt`, `*Dto.kt` (e.g., LoginRequestDto, AuthResponse)
- Exceptions: `*Exception.kt` (e.g., AuthException, AccountLockedException)
- Ports: `*Port.kt`, `*Cache.kt`, `*Gateway.kt` (e.g., PermissionCache, CaptchaGateway)

---

## admindashboard (TypeScript/React 19)

### Detected Packages

- `src/@auth`: Auth module root (authApi.ts, Authentication.tsx, authRoles.ts)
- `src/@auth/services/jwt`: JWT auth provider (JwtAuthProvider.tsx, JwtAuthContext.tsx, useJwtAuth.tsx)
- `src/@auth/services/jwt/components`: JWT form components (JwtSignInForm.tsx)
- `src/@auth/services/jwt/utils`: JWT utilities (jwtUtils.ts)
- `src/@auth/services/aws`: AWS auth provider
- `src/@auth/services/firebase`: Firebase auth provider
- `src/@auth/user`: User model (User type, UserModel)
- `src/app/(public)/(auth)/components/forms`: Auth forms (SignInPageForm.tsx)
- `src/app/(public)/(auth)/components/tabs/sign-in`: Sign-in tabs (JwtSignInTab.tsx)
- `src/utils`: Shared utilities (api.ts — ky HTTP client)

### Not Found

- `src/@auth/types` — NOT FOUND (types defined inline in authApi.ts and JwtAuthProvider.tsx)
- `src/@auth/hooks` — NOT FOUND (useJwtAuth.tsx co-located with JwtAuthContext)

### Naming Convention

- Components: PascalCase.tsx (e.g., JwtAuthProvider.tsx, SignInPageForm.tsx)
- Utilities: camelCase.ts (e.g., authApi.ts, jwtUtils.ts)
- Context: *Context.tsx (e.g., JwtAuthContext.tsx)
- Hooks: use*.tsx (e.g., useJwtAuth.tsx, useUser.tsx)
