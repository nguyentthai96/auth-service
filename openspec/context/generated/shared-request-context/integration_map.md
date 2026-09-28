# Integration Map

_Generated: 2026-09-28 | Candidate Services: base-core, auth-service, system-admin-service, account-service_

## Inbound Integrations (HTTP Headers & Proxies)

| Integration Point | Protocol | Key Headers / Attributes | Source File / Handler |
|-------------------|----------|-------------------------|-----------------------|
| Client / API Gateway | HTTP 1.1 / HTTP 2 | `X-Correlation-ID`, `X-Request-ID`, `X-Forwarded-For`, `User-Agent`, `X-Device-Fingerprint`, `X-App-Version`, `X-Client-Platform`, `X-Tenant-Id` | `BaseRequestContextFilter.kt` (mới) |
| Proxy Chain Resolution | Servlet API | `x-forwarded-for`, `Proxy-Client-IP`, `WL-Proxy-Client-IP`, `remoteAddr` | `components/base-core/common-log/src/main/kotlin/com/ntt/commonlog/utils/IpInfoUtil.kt` |
| Token Authentication | HTTP Bearer Header | `Authorization: Bearer <JWT>` (RS256 / HMAC) | `ResourceJwtAuthFilter.kt` (`account-service`), `JwtAuthFilter.kt` (`auth-service`) |

## Internal Integrations (Context Bridges & AOP)

| Integration Point | Mechanism | Interaction | Source File / Class |
|-------------------|-----------|-------------|---------------------|
| Spring Security Context | In-Memory Object | Đọc `SecurityContextHolder.getContext().authentication` chuyển đổi sang `UserContext` | `SecurityContextBridgeFilter.kt` (mới) |
| Structured Logging Engine | SLF4J MDC | `MDC.put()` và `MDC.clear()` cho 5 keys (`correlationId`, `clientIp`, `appVersion`, `clientPlatform`, `userId`) | `VirtualThreadMdcFilter.kt`, `BaseRequestContextFilter.kt` |
| Admin Audit Aspect | Spring AOP | `@Around` controller execution, ghi nhận `userId`, `clientIp`, `action` | `services/system-admin-service/src/main/kotlin/com/ntt/sysadminservice/audit/application/AuditAspect.kt` |
| Audit Log Service | Spring Bean Injection | Ghi nhận login/logout audit event kèm `clientIp`, `deviceFingerprint` | `services/auth-service/src/main/kotlin/com/ntt/authservice/shared/audit/AuditLogService.kt` |

## Outbound Integrations (Distributed Propagation)

| Integration Point | Protocol | Mechanism | Source File / Class |
|-------------------|----------|-----------|---------------------|
| Downstream Microservices | HTTP REST | `ClientHttpRequestInterceptor` tự động chèn `X-Correlation-ID: <id>` | `RequestContextClientInterceptor.kt` (mới trong `base-http-client-starter`) |
| Apache Kafka Messaging | Kafka Protocol | `ProducerInterceptor` đính kèm correlationId byte[] vào `record.headers()` | `components/base-core/starters/base-messaging-starter/src/main/kotlin/com/ntt/basecore/autoconfigure/messaging/kafka/KafkaTracingProducerInterceptor.kt` |
| Async / Virtual Thread Pools | Java Concurrency | `TaskDecorator` copy `RequestContext` và `MDC` snapshot sang luồng mới | `ContextTaskDecorator.kt` (mới trong `base-web-starter`) |
