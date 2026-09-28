## Purpose

Tạo cầu nối độc lập chuyển đổi thông tin xác thực từ Spring SecurityContext sang mô hình danh tính người dùng của miền nghiệp vụ và bổ sung định danh người dùng vào MDC.

## ADDED Requirements

### Requirement: SecurityContext to UserContext Mapping
Hệ thống SHALL cung cấp bộ lọc chạy sau chuỗi xác thực của Spring Security để chuyển đổi đối tượng `Authentication` hợp lệ thành cấu trúc `UserContext` và gắn vào `RequestContext.user`.

#### Scenario: Authenticated request processing
- **WHEN** request đã vượt qua xác thực token JWT và sở hữu thông tin principal hợp lệ
- **THEN** hệ thống trích xuất `userId`, `username`, `roles`, `permissions`, `jti` gắn vào `RequestContext.user` và nạp thêm khóa `userId` vào MDC

#### Scenario: Anonymous or unauthenticated request
- **WHEN** request gọi vào endpoint công khai hoặc không có thông tin xác thực
- **THEN** hệ thống giữ `RequestContext.user` mang giá trị null và không nạp khóa `userId` vào MDC

### Requirement: Custom Claims Extensibility
Hệ thống SHALL cung cấp điểm mở rộng cho phép các microservice hạ nguồn tùy biến ánh xạ thêm các thông tin mở rộng (như activeDomain, tenantId) từ JWT claims vào thuộc tính attributes của `UserContext`.

#### Scenario: Downstream service requires domain claims
- **WHEN** downstream service đăng ký một bean tùy biến ngữ cảnh người dùng
- **THEN** các thuộc tính tùy biến được tự động bổ sung vào `UserContext.attributes`
