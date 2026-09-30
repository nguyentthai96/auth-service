# Impact Analysis: fix-session-filter-autoconfig-conflict

## 1. Core Files Affected

| # | File | Lines | Purpose | Action |
|---|------|-------|---------|--------|
| 1 | `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityAutoConfiguration.kt` | L56-L72 | Đăng ký bean `defaultSessionValidationFilter` | [MODIFY] |
| 2 | `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/SecurityProperties.kt` | L21-L47 | Ánh xạ cấu hình `app.security` | [MODIFY] |
| 3 | `services/auth-service/src/main/resources/application-security.yml` | L25-L45 | Cấu hình bảo mật auth-service | [MODIFY] |

## 2. Call Tree & Request Flow

```
POST /auth/login (Client / Web / Curl)
 ├── Tomcat Filter Chain
 │    ├── BaseRequestContextFilter (-2000)
 │    ├── HttpLoggingFilter (-1900)
 │    └── [DefaultSessionValidationFilter (-1700)]
 │         ├── [BEFORE]: Unconditional check → Missing Bearer → 401 Unauthorized (Blocked in 1ms)
 │         └── [AFTER]: Disabled in auth-service via app.security.session-validation.enabled=false (Passed)
 ├── Spring Security FilterChainProxy (-100)
 │    ├── LoginRateLimitFilter
 │    ├── JwtAuthFilter (Bỏ qua do không có Authorization header)
 │    ├── ServiceAuthFilter (Bỏ qua do không phải /api/internal/)
 │    └── AuthorizationFilter (PermitAll match: /auth/login)
 └── DispatcherServlet
      └── CqrsAuthController.login()
           └── CommandBus.dispatch(LoginCommand)
                └── LoginCommandHandler -> AuthenticationPipeline -> 200 OK Token Response
```

## 3. Blast Radius Assessment

| Target | Downstream Callers | Risk Level | Mitigation |
|--------|-------------------|------------|------------|
| `SecurityAutoConfiguration.defaultSessionValidationFilter` | Microservices using `base-security-starter` (`account-service`, `system-admin-service`, `notification-service`) | 🟢 Low | `matchIfMissing = true` đảm bảo các Resource Server khác không bị ảnh hưởng nếu không đổi cấu hình. |
| `SecurityProperties.sessionValidation` | `SecurityAutoConfiguration` | 🟢 Low | Data class `SessionValidationProperties(var enabled: Boolean = true)` tương thích 100%. |
| `auth-service/application-security.yml` | Toàn bộ request vào `auth-service` | 🟢 Low | Chỉ tắt filter -1700 thừa thãi; Spring Security `SecurityConfig` và `JwtAuthFilter` tiếp tục bảo vệ toàn bộ API của `auth-service`. |

## 4. Reuse Map

| Component | Usage | Decision |
|-----------|-------|----------|
| `DefaultSessionValidationFilter` | Tái sử dụng nguyên vẹn logic validation cho Resource Servers | [REUSE] |
| `JwtAuthFilter` | Tái sử dụng nguyên vẹn trong `auth-service` | [REUSE] |
| `SecurityConfig` | Tái sử dụng toàn bộ `SecurityFilterChain` của `auth-service` | [REUSE] |
| `SecurityProperties` | Mở rộng thêm thuộc tính cấu hình toggle | [MODIFY] |

## 5. Context Snapshot

- **Kotlin Version**: 2.1+
- **Spring Boot Version**: 3.4+
- **Order Constants**:
  - `SESSION_VALIDATION = -1700`
  - `DEFAULT_FILTER_ORDER = -100` (Spring Security)
- **Zero Breaking Changes**: Không làm thay đổi public API, không làm hỏng các microservice khác.
