# Technical Design: fix-session-filter-autoconfig-conflict

## Context

Xem chi tiết động lực và phân tích nguyên nhân gốc rễ tại `proposal.md` và `impact_analysis.md`.
Hiện tại, `base-security-starter` được `auth-service` sử dụng để kế thừa các tính năng bảo mật dùng chung (như `DynamicAuthorizationManager`, `SecurityContextBridgeFilter`, và mã hóa cipher). Tuy nhiên, `SecurityAutoConfiguration` lại tự động đăng ký `DefaultSessionValidationFilter` như một Servlet Filter ở order `-1700`. Do servlet filter này chạy trước Spring Security (`-100`), nó từ chối các request không có header `Authorization` (như login) với mã lỗi 401 thay vì chuyển tiếp vào Spring Security chain.

## Goals / Non-Goals

**Goals:**
- Tách bạch vai trò cấu hình giữa Identity Provider (`auth-service`) và Resource Server (`account-service`, ...).
- Cung cấp toggle switch `@ConditionalOnProperty(prefix = "app.security.session-validation", name = ["enabled"], havingValue = "true", matchIfMissing = true)` cho bean `defaultSessionValidationFilter`.
- Cho phép `auth-service` tắt `DefaultSessionValidationFilter` để Spring Security và `JwtAuthFilter` có toàn quyền điều khiển xác thực.
- Đảm bảo 100% tương thích ngược cho tất cả downstream services không sửa đổi cấu hình.

**Non-Goals:**
- Thay đổi logic nội bộ của `DefaultSessionValidationFilter` hay `AbstractSessionValidationFilter`.
- Thay đổi logic xác thực JWT của `JwtAuthFilter` trong `auth-service`.
- Viết lại filter chain của Spring Security.

## Decisions

### Quyết định 1: Thêm `@ConditionalOnProperty` cho `defaultSessionValidationFilter`
- **Lý do**: Chuẩn quy ước Spring Boot Starters. Khi một starter cung cấp một Servlet filter có khả năng chặn đứng request ở tầng thấp (order âm rất sâu: `-1700`), starter BẮT BUỘC phải cung cấp thuộc tính cấu hình để các service đặc thù (như Auth Server) có thể tắt tính năng này mà không cần ghi đè bean rườm rà.
- **Phương án thay thế đã loại bỏ**:
  - *Chỉ cấu hình `public-paths` trong YAML*: Bỏ qua vì `DefaultSessionValidationFilter` vẫn sống trong bộ nhớ và can thiệp vào mọi request (AntPathMatcher regex), đồng thời có nguy cơ xung đột với các endpoint yêu cầu token nội bộ khác của `auth-service`.
  - *Tạo dummy bean `AbstractSessionValidationFilter` rỗng*: Bỏ qua vì mang tính "hack", khó bảo trì và không tường minh.

### Quyết định 2: Đặt `matchIfMissing = true`
- **Lý do**: Bảo vệ 100% các Resource Server downstream (`account-service`, `system-admin-service`, v.v.). Nếu không có cờ này, tất cả các service hiện tại sẽ bị mất filter xác thực session nếu chưa kịp thêm cấu hình `enabled: true`.

### Quyết định 3: Thêm Whitelist `public-paths` trong `auth-service` (Defense-in-Depth)
- **Lý do**: Dù `DefaultSessionValidationFilter` đã bị disable trên `auth-service`, việc khai báo rõ ràng các `public-paths` (`/auth/**`, `/actuator/**`, `/captcha/**`, ...) trong `application-security.yml` giúp các thành phần khác (hoặc logging/monitoring filter) hiểu rõ semantic các endpoint công khai của service.

## Risks / Trade-offs

- **[Risk 1: Cấu hình YAML sai tên property]** $\rightarrow$ *Mitigation*: Ánh xạ chặt chẽ thông qua `@ConfigurationProperties(prefix = "app.security")` trong `SecurityProperties.kt` với class `SessionValidationProperties`.
- **[Risk 2: Build stale dependency trong auth-service]** $\rightarrow$ *Mitigation*: Chạy task `publishToMavenLocal` cho module `base-security-starter` trước khi restart hoặc build `auth-service`.

## Migration Plan

1. Cập nhật `SecurityProperties.kt` và `SecurityAutoConfiguration.kt` trong `components/base-core/starters/base-security-starter`.
2. Chạy `./gradlew :starters:base-security-starter:publishToMavenLocal` từ thư mục `components/base-core`.
3. Cập nhật `application-security.yml` trong `services/auth-service`.
4. Khởi động lại `auth-service` và verify bằng curl `POST /auth/login`.
