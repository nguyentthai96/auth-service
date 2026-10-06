<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "Command", factory: "N/A", feature_type: "MAINTENANCE", transaction_flow: "Command" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->

# Tasks: filter-architecture-consolidation

> **Deployment order**: Phase 1 → Phase 2 → Phase 3 → Phase 4 (sequential, each independently deployable)
> **Total FR coverage**: 12/12

## Phase 1: Base-Core Enhancement (FR-001, FR-002)

- [x] **1.1 Add Content-Language header to BaseRequestContextFilter**
  - File: `components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/web/filter/BaseRequestContextFilter.kt` | Action: [MODIFY]
  - FR: FR-001 — Thêm Content-Language header vào BaseRequestContextFilter
  - Base: `OncePerRequestFilter` from `org.springframework.web.filter`
  - Pattern: Add `response.setHeader("Content-Language", locale.toLanguageTag())` BEFORE `filterChain.doFilter()` at line 76. Use `LocaleContextHolder.getLocale()` (already extracted at line 58).
  - Dependencies: `RequestContextProperties` (add `contentLanguageEnabled: Boolean = true`)

- [x] **1.2 Add contentLanguageEnabled property to RequestContextProperties**
  - File: `components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/web/config/RequestContextProperties.kt` | Action: [MODIFY]
  - FR: FR-001 — Property toggle cho Content-Language header
  - Pattern: Add `var contentLanguageEnabled: Boolean = true` field. Default enabled.

- [x] **1.3 Add belt-and-suspenders comment to BaseControllerAdvice**
  - File: `components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt` | Action: [MODIFY]
  - FR: FR-002 — Giữ setContentLanguageHeader với comment giải thích
  - Pattern: Add KDoc comment above `setContentLanguageHeader()` method (line 213): "Belt-and-suspenders: BaseRequestContextFilter sets this header first; this method re-sets on error paths as backup. Idempotent, zero cost."

- [x] **1.4 Build and publish base-core to Maven Local**
  - File: `components/base-core` | Action: [BUILD]
  - FR: FR-001, FR-002
  - Command: `./gradlew publishToMavenLocal` at `components/base-core`

## Phase 2: Account/SysAdmin Service Migration (FR-003, FR-004, FR-005)

- [x] **2.1 Delete ResourceJwtAuthFilter from account-service**
  - File: `services/account-service/src/main/kotlin/com/ntt/accountservice/shared/security/ResourceJwtAuthFilter.kt` | Action: [DELETE]
  - FR: FR-003 — Xóa duplicate filter (86 LOC)
  - Source: `DefaultSessionValidationFilter` from base-security-starter auto-registers via `SecurityAutoConfiguration` (`matchIfMissing=true`)

- [x] **2.2 Remove JwtAuthFilter reference from account-service SecurityConfig**
  - File: `services/account-service/src/main/kotlin/com/ntt/accountservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-003 — Xóa `addFilterBefore(jwtAuthFilter, ...)` từ SecurityConfig
  - Pattern: Remove constructor parameter `ResourceJwtAuthFilter`, remove `addFilterBefore` call. `DefaultSessionValidationFilter` chạy ở servlet level (order -1700, TRƯỚC security chain).

- [x] **2.3 Configure publicPaths for account-service**
  - File: `services/account-service/src/main/resources/application.yml` (or `application-security.yml`) | Action: [MODIFY]
  - FR: FR-005 — Cấu hình publicPaths cho DefaultSessionValidationFilter bypass
  - Pattern: Add `app.security.public-paths: ["/actuator/**", "/health", "/health/**"]`

- [x] **2.4 Delete ResourceJwtAuthFilter from system-admin-service**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/systemadminservice/shared/security/ResourceJwtAuthFilter.kt` | Action: [DELETE]
  - FR: FR-004 — Xóa duplicate filter (86 LOC)

- [x] **2.5 Remove JwtAuthFilter reference from system-admin-service SecurityConfig**
  - File: `services/system-admin-service/src/main/kotlin/com/ntt/systemadminservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-004 — Tương tự 2.2

- [x] **2.6 Configure publicPaths for system-admin-service**
  - File: `services/system-admin-service/src/main/resources/application.yml` (or `application-security.yml`) | Action: [MODIFY]
  - FR: FR-005 — Tương tự 2.3

- [x] **2.7 Verify account-service and system-admin-service compile and start**
  - File: `services/account-service`, `services/system-admin-service` | Action: [TEST]
  - FR: FR-003, FR-004, FR-005
  - Command: `./gradlew compileKotlin` per service

## Phase 3: Auth-Service Refactor (FR-006, FR-007, FR-008, FR-009, FR-010, FR-012)

- [x] **3.1 Create AuthSessionValidationFilter**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/AuthSessionValidationFilter.kt` | Action: [ADD]
  - FR: FR-006, FR-007, FR-008 — Modular pipeline kế thừa AbstractSessionValidationFilter
  - Base: `AbstractSessionValidationFilter` from `com.ntt.basecore.autoconfigure.security.session`
  - Dependencies:
    - `JwtTokenExtractor` — token extraction and validation
    - `TokenBlacklistCacheService` — revocation check (FR-007: override `isTokenRevoked()` → delegate)
    - `FingerprintService` — device fingerprint validation
    - `SecurityProperties` — publicPaths config
    - `MeterRegistry` — business metrics counter
  - Pattern:
    - Override `validateToken(token)` → `jwtTokenExtractor.extractAndValidate(token)` → `TokenValidationResult`
    - Override `isTokenRevoked(tokenId)` → `tokenBlacklistCacheService.isBlacklisted(tokenId)`
    - Override `extractTokenId(principal)` → `(principal as Claims).id` (or jti claim)
    - Override `onAuthenticationSuccess(request, principal)`:
      - If token type = "anonymous" → set ROLE_ANONYMOUS
      - Else → extract roles/permissions from claims → set SecurityContext
      - Validate device fingerprint if present
      - Record metrics counter `auth.token.validation`

- [x] **3.2 Register AuthSessionValidationFilter as FilterRegistrationBean**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` (or dedicated config) | Action: [ADD]
  - FR: FR-006, FR-012 — Servlet registration with OrderConstants
  - Pattern:
    ```kotlin
    @Bean
    fun authSessionValidationFilter(...): FilterRegistrationBean<AuthSessionValidationFilter> {
        val filter = AuthSessionValidationFilter(...)
        val registration = FilterRegistrationBean(ObservableFilterWrapper.wrap(filter, "authSessionValidation", observationRegistry))
        registration.order = OrderConstants.SESSION_VALIDATION // -1700
        return registration
    }
    ```
  - Note: `@ConditionalOnMissingBean(AbstractSessionValidationFilter::class)` on DefaultSessionValidF auto-disables

- [x] **3.3 Remove JwtAuthFilter from SecurityConfig**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/config/SecurityConfig.kt` | Action: [MODIFY]
  - FR: FR-010 — Remove `addFilterBefore(jwtAuthFilter, ...)`, KEEP `addFilterBefore(loginRateLimitFilter, ...)` and `addFilterBefore(serviceAuthFilter, ...)`

- [x] **3.4 Delete ContentLanguageFilter**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/auth/adapter/in/web/filter/ContentLanguageFilter.kt` | Action: [DELETE]
  - FR: FR-009 — Base-core BaseRequestContextFilter handles Content-Language now

- [x] **3.5 Delete JwtAuthFilter**
  - File: `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/security/JwtAuthFilter.kt` | Action: [DELETE]
  - FR: FR-006 — Replaced by AuthSessionValidationFilter

- [x] **3.6 Enable session-validation for auth-service (re-enable)**
  - File: `services/auth-service/src/main/resources/application-security.yml` | Action: [MODIFY]
  - FR: FR-006 — Trước đây `enabled: false` (archive fix), giờ bật lại vì AuthSessionValidationFilter tự đăng ký → DefaultSessionValidF bị override qua `@ConditionalOnMissingBean`
  - Pattern: Set `app.security.session-validation.enabled: true` (or remove the property entirely since `matchIfMissing=true`). Verify `AuthSessionValidationFilter` bean replaces default.

- [x] **3.7 Verify auth-service compile and authentication flow**
  - File: `services/auth-service` | Action: [TEST]
  - FR: FR-006, FR-007, FR-008, FR-009, FR-010
  - Command: `./gradlew compileKotlin` + verify login flow + verify anonymous flow + verify authenticated flow

## Phase 4: Error Response Standardization (FR-011)

- [x] **4.1 Refactor sendUnauthorized to RFC 7807 ProblemDetail**
  - File: `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/session/AbstractSessionValidationFilter.kt` | Action: [MODIFY]
  - FR: FR-011 — Chuẩn hóa error format
  - Pattern: Change `sendUnauthorized()` (line 151-155):
    ```kotlin
    private fun sendUnauthorized(response: HttpServletResponse, message: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = "application/problem+json"
        val problemDetail = """{"type":"about:blank","title":"Unauthorized","status":401,"detail":"${JsonUtils.escapeJson(message)}"}"""
        response.writer.write(problemDetail)
    }
    ```
  - Error: Content-Type changes from `application/json` to `application/problem+json`

- [x] **4.2 Build and publish base-core to Maven Local (final)**
  - File: `components/base-core` | Action: [BUILD]
  - FR: FR-011
  - Command: `./gradlew publishToMavenLocal` at `components/base-core`

- [x] **4.3 Re-verify all services compile with updated base-core**
  - File: All services | Action: [TEST]
  - FR: FR-011
  - Command: `./gradlew compileKotlin` per service
