<!-- self-contained: true -->
<!-- pipeline: wf_openspec_apply -->
<!-- locked_profile: { flow: "NonFinancial", factory: "BaseCoreDataFactory", feature_type: "EXTEND", transaction_flow: "Standard" } -->
<!-- context_loaded: true -->
<!-- reuse_rules_loaded: true -->
# Implementation Tasks: Shared RequestContext in Base-Core

_Generated: 2026-09-28_

This task list breaks down the implementation of extracting, standardizing, and propagating `RequestContext` across all microservices via `base-core`.

---

## 1. Core Model & Context Management (base-model)

- [x] 1.1 **Create RequestContext & UserContext Models**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/base-model/src/main/kotlin/com/ntt/basecore/context/RequestContext.kt` | Action: [NEW][EXTRACT]
  - Base: Kotlin Data Class
  - FR: FR-001 — Common RequestContext Data Model
  - Pattern: Immutable snapshot with copy/builder or clear mutable lifecycle; encapsulates `UserContext`, client metadata, correlation ID, tenant ID, and custom attributes
  - Dependencies: `java.util.Locale`, `java.util.UUID`
  - Source: Extracted from [auth-service/RequestContext.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt#L25-L78)

- [x] 1.2 **Implement RequestContextHolder with Virtual Thread Safety & Fallback**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/base-model/src/main/kotlin/com/ntt/basecore/context/RequestContextHolder.kt` | Action: [NEW]
  - Base: Object Singleton
  - FR: FR-002 — RequestContextHolder & Lifecycle Management
  - Pattern: Standard `ThreadLocal<RequestContext>` (strict prohibition of `InheritableThreadLocal` to prevent carrier thread leak in JDK 21 Virtual Threads); provides `get()`, `set()`, `clear()`, `setUserContext()`, and `runWith(context) { ... }`; includes non-web fallback `RequestContext.fallback(source)` to avoid `ScopeNotActiveException` in async jobs or Kafka consumers
  - Dependencies: `com.ntt.basecore.context.RequestContext`

- [x] 1.3 **Unit Tests for RequestContextHolder & Lifecycle Cleanup**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/base-model/src/test/kotlin/com/ntt/basecore/context/RequestContextHolderTest.kt` | Action: [NEW]
  - Base: JUnit 5 (`org.junit.jupiter.api.Test`)
  - FR: FR-011 — Unit & Concurrency Testing
  - Pattern: Verify `get()`, `set()`, `clear()`, non-web fallback behavior, and `runWith` block scope isolation
  - Dependencies: `com.ntt.basecore.context.RequestContextHolder`, `org.assertj.core.api.Assertions`

---

## 2. Web Filter & MDC Orchestration (base-web-starter)

- [x] 2.1 **Implement BaseRequestContextFilter with Correlation ID & IP Extraction**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/web/filter/BaseRequestContextFilter.kt` | Action: [NEW][EXTRACT]
  - Base: `org.springframework.web.filter.OncePerRequestFilter`
  - FR: FR-003 — BaseRequestContextFilter & Header Extraction, FR-004 — MDC Context Logging & Cleanup
  - Pattern: Order `@Order(Ordered.HIGHEST_PRECEDENCE + 10)` (runs immediately after `VirtualThreadMdcFilter`); extracts/generates `X-Correlation-ID` (priority: `X-Correlation-ID` -> `X-Request-ID` -> UUID v7); extracts client IP supporting trusted proxies & RFC 7239 / `X-Forwarded-For`; populates client metadata and populates MDC (`correlationId`, `clientIp`, `appVersion`, `clientPlatform`); strictly executes `RequestContextHolder.clear()` and `MDC.clear()` in `finally` block
  - Dependencies: `com.ntt.basecore.context.RequestContextHolder`, `org.slf4j.MDC`, `jakarta.servlet.http.HttpServletRequest`
  - Source: Extracted from [auth-service/RequestContextFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt#L34-L175)

- [x] 2.2 **Implement ContextTaskDecorator for Async & Virtual Thread Propagation**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/context/decorator/ContextTaskDecorator.kt` | Action: [NEW]
  - Base: `org.springframework.core.task.TaskDecorator`
  - FR: FR-006 — ContextTaskDecorator for Async & Virtual Thread Propagation
  - Pattern: Captures `RequestContextHolder.get()` and `MDC.getCopyOfContextMap()` at dispatch time, wraps task execution with setup and clean `finally` teardown to ensure child threads / virtual thread tasks have access to parent context without carrier thread contamination
  - Dependencies: `com.ntt.basecore.context.RequestContextHolder`, `org.slf4j.MDC`

- [x] 2.3 **Create RequestContextProperties & RequestContextAutoConfiguration**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/web/config/RequestContextAutoConfiguration.kt` | Action: [NEW]
  - Base: Spring `@AutoConfiguration`
  - FR: FR-008 — Spring Boot Auto-Configuration & Conditional Activation
  - Pattern: `@ConditionalOnWebApplication(type = SERVLET)`, `@EnableConfigurationProperties(RequestContextProperties::class)`, conditionally declares `BaseRequestContextFilter` and registers `ContextTaskDecorator` into `ThreadPoolTaskExecutor` or virtual thread executors if present
  - Dependencies: `org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication`, `org.springframework.boot.context.properties.ConfigurationProperties`

- [x] 2.4 **Web Slice Tests for BaseRequestContextFilter & MDC Lifecycle**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-web-starter/src/test/kotlin/com/ntt/basecore/web/filter/BaseRequestContextFilterTest.kt` | Action: [NEW]
  - Base: JUnit 5 + MockMvc / MockHttpServletRequest
  - FR: FR-011 — Unit & Concurrency Testing
  - Pattern: Validate correlation ID header extraction and fallback generation, verify MDC values are present during filter chain and guaranteed cleared in `finally`
  - Dependencies: `org.springframework.mock.web.MockHttpServletRequest`, `org.springframework.mock.web.MockHttpServletResponse`

---

## 3. Security Bridge & Outbound Propagation (base-security-starter & base-http-client-starter)

- [x] 3.1 **Implement SecurityContextBridgeFilter in base-security-starter**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/security/filter/SecurityContextBridgeFilter.kt` | Action: [NEW][EXTRACT]
  - Base: `org.springframework.web.filter.OncePerRequestFilter`
  - FR: FR-005 — SecurityContextBridgeFilter
  - Pattern: Placed after Spring Security's `SecurityContextHolderFilter` / authentication filter; extracts `Authentication`, resolves `userId`, `username`, `roles`, `jti`, enriches current `RequestContextHolder.get()` with `UserContext`, and sets `MDC.put("userId", userId)`
  - Dependencies: `org.springframework.security.core.context.SecurityContextHolder`, `com.ntt.basecore.context.RequestContextHolder`, `com.ntt.basecore.context.UserContext`
  - Source: Extracted from [auth-service/RequestContextFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt#L66-L82)

- [x] 3.2 **Implement RequestContextClientInterceptor with Configurable Token Relay**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-http-client-starter/src/main/kotlin/com/ntt/basecore/client/interceptor/RequestContextClientInterceptor.kt` | Action: [NEW]
  - Base: `org.springframework.http.client.ClientHttpRequestInterceptor`
  - FR: FR-007 — Outbound RequestContextClientInterceptor (Inter-service HTTP propagation)
  - Pattern: Reads `RequestContextHolder.get()`; automatically sets outbound headers `X-Correlation-ID` and `X-Tenant-Id`; implements decision **OQ-01** (default: does NOT forward `Authorization` bearer token; if `app.http-client.forward-auth-token: true`, relays bearer token from current context or security context)
  - Dependencies: `com.ntt.basecore.context.RequestContextHolder`, `org.springframework.http.HttpRequest`

- [x] 3.3 **Register Interceptor in BaseHttpClientAutoConfiguration**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-http-client-starter/src/main/kotlin/com/ntt/basecore/client/config/BaseHttpClientAutoConfiguration.kt` | Action: [MODIFY]
  - Base: Spring `@AutoConfiguration`
  - FR: FR-007, FR-008 — Auto-Configuration
  - Pattern: Automatically inject `RequestContextClientInterceptor` into `RestClient.Builder` and `RestTemplateBuilder` beans
  - Dependencies: `org.springframework.web.client.RestClient`, `org.springframework.boot.web.client.RestTemplateCustomizer`

- [x] 3.4 **Unit Tests for SecurityContextBridgeFilter & Client Interceptor**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-http-client-starter/src/test/kotlin/com/ntt/basecore/client/interceptor/RequestContextClientInterceptorTest.kt` | Action: [NEW]
  - Base: JUnit 5
  - FR: FR-011 — Unit & Concurrency Testing
  - Pattern: Verify header injection into outbound requests; test Token Relay disabled by default and enabled via configuration flag
  - Dependencies: `org.springframework.mock.http.client.MockClientHttpRequest`

---

## 4. Downstream Service Migration & Compatibility (auth-service & system-admin-service)

- [x] 4.1 **Refactor auth-service RequestContext.kt to Inherit / Wrap BaseCore Model**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt` | Action: [MODIFY][REUSE]
  - Base: `com.ntt.basecore.context.RequestContext`
  - FR: FR-009 — Compatibility Adapter for auth-service
  - Pattern: Keep existing class signature and package `com.ntt.authservice.shared.web.RequestContext` to ensure zero breaking changes for existing controllers (`BaseController`, `AuthenticatedController`, `DeviceControllerTest`); delegate getters/setters to underlying `RequestContext` and preserve helper methods (`requireUserId()`, `requireJti()`, `hasRole()`, `isAdmin()`)
  - Dependencies: `com.ntt.basecore.context.RequestContext`, `com.ntt.basecore.context.RequestContextHolder`
  - Source: [auth-service/RequestContext.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt)

- [x] 4.2 **Refactor auth-service RequestContextFilter.kt to Delegate to BaseRequestContextFilter**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt` | Action: [MODIFY][REUSE]
  - Base: `com.ntt.basecore.web.filter.BaseRequestContextFilter`
  - FR: FR-009 — Compatibility Adapter
  - Pattern: Deprecate or convert `RequestContextFilter` into a lightweight delegate or mark `@ConditionalOnMissingBean(BaseRequestContextFilter::class)`, avoiding duplicate header parsing and MDC handling
  - Dependencies: `com.ntt.basecore.web.filter.BaseRequestContextFilter`
  - Source: [auth-service/RequestContextFilter.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt)

- [x] 4.3 **Refactor auth-service AuditLogService.kt to Reuse RequestContextHolder**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt` | Action: [MODIFY][REUSE]
  - Base: Spring `@Service`
  - FR: FR-009, FR-010 — Service Layer Integration
  - Pattern: Replace lines 149-165 (manual `ServletRequestAttributes` retrieval and duplicate `getClientIp()` string splitting) with direct call to `RequestContextHolder.get().clientIp`
  - Dependencies: `com.ntt.basecore.context.RequestContextHolder`
  - Source: [auth-service/AuditLogService.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt#L149-L165)

- [x] 4.4 **Refactor system-admin-service AuditAspect.kt to Use RequestContextHolder**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/application/AuditAspect.kt` | Action: [MODIFY][REUSE]
  - Base: Spring Aspect (`@Aspect`)
  - FR: FR-010 — Integration with Downstream Services
  - Pattern: Replace Spring's `RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes` at line 138 with `com.ntt.basecore.context.RequestContextHolder.get()`
  - Dependencies: `com.ntt.basecore.context.RequestContextHolder`
  - Source: [system-admin-service/AuditAspect.kt](file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/application/AuditAspect.kt#L138)

---

## 5. Verification, Concurrency & Integration Testing

- [x] 5.1 **Virtual Thread Concurrency & Carrier-Thread Leak Test**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/components/base-core/starters/base-web-starter/src/test/kotlin/com/ntt/basecore/context/VirtualThreadContextLeakTest.kt` | Action: [NEW]
  - Base: JUnit 5 + Virtual Thread Executor (`Executors.newVirtualThreadPerTaskExecutor()`)
  - FR: FR-011 — Concurrency & Leak Testing
  - Pattern: Execute 1,000+ concurrent Virtual Threads mutating context and verify that carrier threads are never contaminated and MDC is strictly 100% clean after completion
  - Dependencies: `com.ntt.basecore.context.RequestContextHolder`, `org.slf4j.MDC`

- [x] 5.2 **End-to-End Filter Chain & Client Propagation Integration Test**
  - File: `file:///home/nguyentthai96/Desktop/bigbang/boilerplate/services/auth-service/src/test/kotlin/com/ntt/authservice/shared/web/SharedRequestContextIntegrationTest.kt` | Action: [NEW]
  - Base: `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
  - FR: FR-011 — Integration Testing
  - Pattern: Send HTTP request with `X-Correlation-ID`; assert response header receives same `X-Correlation-ID`; assert logs contain MDC correlationId and userId; verify downstream RestClient call receives forwarded correlation ID
  - Dependencies: `org.springframework.boot.test.web.client.TestRestTemplate`, `org.springframework.boot.test.context.SpringBootTest`

- [x] 5.3 **Verify Existing Test Suites Pass Without Regression**
  - File: N/A (Build verification command: `./gradlew test`) | Action: [TEST]
  - Base: Gradle Test
  - FR: FR-009, FR-011
  - Pattern: Run full test suite on `auth-service` and `system-admin-service` to confirm zero regression on existing endpoints (`/api/auth/login`, `/api/auth/register`, `/api/v1/devices`, etc.)
