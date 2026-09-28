# Service Structure

_Generated: 2026-09-28 | Candidate Services: base-core, auth-service, system-admin-service, account-service_

## Package Conventions

### 1. `components/base-core`
- Multi-module starter hierarchy:
  - `starters/base-web-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/web/`
    - Sub-packages: `versioning`, `idempotency`, `data`, `config`
    - Target for new feature: `com.ntt.basecore.autoconfigure.web.context`
  - `starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/`
    - Sub-packages: `cipher`, `session`, `mfa`
    - Target for new feature: `com.ntt.basecore.autoconfigure.security.context`
  - `starters/base-http-client-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/httpclient/`
    - Sub-packages: `client`
    - Target for new feature: `com.ntt.basecore.autoconfigure.httpclient.interceptor`
  - `base-model/src/main/kotlin/com/ntt/basecore/`
    - Sub-packages: `model`, `context` (or `com.ntt.basecore.context`)
  - `common-log/src/main/kotlin/com/ntt/commonlog/`
    - Sub-packages: `filter`, `http`, `utils`, `logback`, `aop`

### 2. `services/auth-service`
- Hexagonal / Clean Architecture:
  - `com.ntt.authservice.shared.web`: Chứa `RequestContext`, `RequestContextFilter`, `BaseController`
  - `com.ntt.authservice.shared.audit`: Chứa `AuditLogService`
  - `com.ntt.authservice.auth.adapter.in.web`: Chứa REST Controllers
  - `com.ntt.authservice.auth.application.command`: Chứa Command Handlers

### 3. `services/system-admin-service`
- `com.ntt.sysadminservice.audit.application`: Chứa `AuditAspect.kt`, `AuditService.kt`
- `com.ntt.sysadminservice.admin.adapter.in.web`: Chứa Admin REST Controllers

### 4. `services/account-service`
- `com.ntt.accountservice.shared.security`: Chứa `ResourceJwtAuthFilter.kt`
- `com.ntt.accountservice.account.adapter.in.web`: Chứa Account REST Controllers

## Naming Conventions
- Filters: `*Filter` (extends `OncePerRequestFilter`)
- AutoConfigurations: `*AutoConfiguration` (annotated `@AutoConfiguration`)
- Context Holders: `*ContextHolder` (static `ThreadLocal` wrapper object)
- Context Models: `*Context` (immutable Kotlin `data class`)
- Interceptors: `*ClientInterceptor` or `*ProducerInterceptor`
- Decorators: `*TaskDecorator` (implements `TaskDecorator`)
