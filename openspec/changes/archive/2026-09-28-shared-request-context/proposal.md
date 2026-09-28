# Proposal: shared-request-context

## Why

Hiện tại, các microservices trong hệ sinh thái (`auth-service`, `account-service`, `system-admin-service`, `notification-service`) đang xử lý request context và tracing metadata một cách phân mảnh và trùng lặp:
1. `auth-service` tự xây dựng `RequestContextFilter` nhưng bị gắn chặt với domain nội bộ của Auth Provider (`anonymousSessionId`, `anonymousTokenJti`, `InvalidCredentialsException`).
2. `system-admin-service` trong `AuditAspect` phải tự viết code ad-hoc để bóc tách `X-Forwarded-For` và `userPrincipal`.
3. `account-service` và `notification-service` thiếu cơ chế Correlation ID và MDC structured logging, khiến chuỗi vết (distributed trace) bị đứt đoạn hoàn toàn khi giao tiếp liên service.
4. `@RequestScope` bean hiện tại bị lỗi `ScopeNotActiveException` khi được gọi trong các tác vụ nền phi Web (Kafka Consumers `@KafkaListener`, Scheduled Tasks `@Scheduled`).
5. Trên Java 21 Virtual Threads (Project Loom), việc sử dụng context không đúng cách (như `InheritableThreadLocal` hoặc thiếu dọn dẹp) có thể gây rò rỉ dữ liệu qua carrier threads.

Việc chuẩn hóa và đưa thành phần Request Context & Observability vào `base-core` giải quyết triệt để vấn đề DRY, chuẩn hóa 100% structured logging và truy vết phân tán cho toàn bộ hệ thống.

## What Changes

- **NEW**: Cung cấp domain model `RequestContext`, `UserContext` và `RequestContextHolder` (ThreadLocal chuẩn, Loom-safe) trong `base-core` (`com.ntt.basecore.context`).
- **NEW**: Cung cấp `BaseRequestContextFilter` trong `starters/base-web-starter` tự động bóc tách `X-Correlation-ID`, `X-Request-ID`, `X-Forwarded-For` (qua `IpInfoUtil`), device info và nạp 4 khóa vào SLF4J MDC.
- **NEW**: Cung cấp `ContextTaskDecorator` trong `starters/base-web-starter` để tự động truyền (snapshot) `RequestContext` và MDC sang `@Async` và Virtual Thread Executors.
- **NEW**: Cung cấp `SecurityContextBridgeFilter` trong `starters/base-security-starter` chạy sau Spring Security để ánh xạ `Authentication` sang `UserContext` và nạp thêm `userId` vào MDC.
- **NEW**: Cung cấp `RequestContextClientInterceptor` trong `starters/base-http-client-starter` tự động đính kèm `X-Correlation-ID` (và cờ cấu hình `forward-auth-token`) khi gọi REST sang microservice khác.
- **NEW**: Cung cấp cơ chế `RequestContext.fallback("system")` cho các tác vụ nền phi Web (Kafka / Scheduled) tránh crash ứng dụng.
- **MODIFY**: Refactor `auth-service` loại bỏ `RequestContextFilter` cục bộ, tái sử dụng starter từ `base-core`.
- **MODIFY**: Refactor `system-admin-service` loại bỏ các hàm bóc tách IP/User thủ công trong `AuditAspect`.
- **MODIFY**: Kích hoạt starter cho `account-service` và `notification-service` để tự động thừa hưởng Correlation ID và MDC logging.

## Capabilities

### New Capabilities

- `request-context`: Core domain metadata model (`RequestContext`), clean user identity model (`UserContext`), và `RequestContextHolder` an toàn luồng với Virtual Threads và fallback cho non-web.
- `observability-filter`: Servlet filter tự động bóc tách transport headers, IP client đa proxy qua `IpInfoUtil`, quản lý vòng đời MDC và echo correlation ID trong HTTP response header.
- `security-context-bridge`: Cầu nối tách biệt giữa Spring Security (`SecurityContextHolder`) và domain context (`RequestContext.user`), hỗ trợ mở rộng claims tùy chỉnh.
- `outbound-propagation`: Lan truyền ngữ cảnh phân tán qua HTTP Client (`RestClient`/`RestTemplate`) và sự kiện Kafka.

### Modified Capabilities

- Không có spec nghiệp vụ cũ nào trong `openspec/specs/` bị thay đổi hành vi yêu cầu (đây là các capabilities hạ tầng mới).

## Impact

- **Components Affected**:
  - `components/base-core`: `base-model`, `starters/base-web-starter`, `starters/base-security-starter`, `starters/base-http-client-starter`, `common-log`.
- **Services Affected**:
  - `services/auth-service`: Refactor `shared/web/` và `shared/audit/AuditLogService.kt`.
  - `services/system-admin-service`: Refactor `audit/application/AuditAspect.kt`.
  - `services/account-service`: Kích hoạt starter, loại bỏ cấu hình header thủ công.
  - `services/notification-service`: Kích hoạt starter để nhận diện Correlation ID.
- **Breaking Changes**: Không có breaking change về mặt REST API công khai đối với Client bên ngoài (backward-compatible 100%).
- **Dependencies**: Không kéo thêm thư viện bên ngoài nặng (zero extra external dependencies), chỉ sử dụng Spring Boot 3 và SLF4J native.
