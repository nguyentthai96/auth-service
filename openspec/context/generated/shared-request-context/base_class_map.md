# Base Class Map

_Generated: 2026-09-28 | Candidate Services: base-core, auth-service, system-admin-service, account-service_

## Filter & Middleware Base Classes

- `OncePerRequestFilter` (`jakarta.servlet.Filter`)
  - Subclasses in `base-core`:
    - `VirtualThreadMdcFilter` — `components/base-core/common-log/src/main/kotlin/com/ntt/commonlog/filter/VirtualThreadMdcFilter.kt` (`@Order(Ordered.HIGHEST_PRECEDENCE)`)
    - `HttpLoggingFilter` — `components/base-core/common-log/src/main/kotlin/com/ntt/commonlog/http/HttpLoggingFilter.kt`
    - `AbstractSessionValidationFilter` — `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/session/AbstractSessionValidationFilter.kt`
    - `CipherFilter` — `components/base-core/starters/base-security-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/security/cipher/http/CipherFilter.kt`
    - `IdempotencyFilter` — `components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/web/idempotency/IdempotencyFilter.kt`
    - `DeprecationFilter` — `components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/web/versioning/DeprecationFilter.kt`
  - Subclasses in downstream services:
    - `RequestContextFilter` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContextFilter.kt` (`@Order(Ordered.LOWEST_PRECEDENCE - 20)`)
    - `ResourceJwtAuthFilter` — `services/account-service/src/main/kotlin/com/ntt/accountservice/shared/security/ResourceJwtAuthFilter.kt`

## Context & Holder Classes

- `RequestAttributeContext` — `components/base-core/src/main/kotlin/com/ntt/basecore/context/RequestAttributeContext.kt` (barebones ThreadLocal for RequestAttributes)
- `RequestContext` (@RequestScope) — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/RequestContext.kt`
- `ExternalServiceContext` — `components/base-core/starters/base-http-client-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/httpclient/client/ExternalServiceContext.kt`
- `SagaContext` — `components/base-core/starters/base-saga-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/saga/core/SagaContext.kt`

## Controller Hierarchy

- `BaseController` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/BaseController.kt` (provides `ok()`, `created()`, `okMessage()`)
- `AuthenticatedController` extends `BaseController` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/AuthenticatedController.kt` (provides `currentUserId()`, `requireRole()`)
- `AdminController` extends `AuthenticatedController` — `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/web/AdminController.kt` (provides `requireAdmin()`)

## Exception Handlers

- `BaseControllerAdvice` — `components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/BaseControllerAdvice.kt`
- `DefaultControllerAdvice` extends `BaseControllerAdvice` — `components/base-core/starters/base-web-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/web/DefaultControllerAdvice.kt`
- `ProblemDetailControllerAdvice` — `components/base-core/src/main/kotlin/com/ntt/basecore/domain/web/ProblemDetailControllerAdvice.kt`

## Interceptors & Decorators

- `TaskDecorator` (`org.springframework.core.task.TaskDecorator`) — NOT DETECTED (Cần thêm `ContextTaskDecorator` cho Virtual Threads & `@Async`)
- `ClientHttpRequestInterceptor` (`org.springframework.http.client.ClientHttpRequestInterceptor`) — NOT DETECTED (Cần thêm `RequestContextClientInterceptor` trong `base-http-client-starter`)
- `KafkaTracingProducerInterceptor` — `components/base-core/starters/base-messaging-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/messaging/kafka/KafkaTracingProducerInterceptor.kt`
