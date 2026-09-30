# session-filter-autoconfig Specification

## Purpose
Cung cấp cơ chế cấu hình linh hoạt (feature toggle) cho DefaultSessionValidationFilter trong base-security-starter nhằm giải quyết xung đột thứ tự lọc khi tích hợp vào Identity Provider (auth-service), đồng thời đảm bảo an toàn tương thích ngược cho Resource Servers.
## Requirements
### Requirement: Toggleable Session Validation Filter
Hệ thống framework base-security-starter SHALL hỗ trợ bật hoặc tắt servlet filter DefaultSessionValidationFilter thông qua thuộc tính cấu hình `app.security.session-validation.enabled`.

#### Scenario: Mặc định kích hoạt trên Resource Servers
- **WHEN** một microservice (như account-service) phụ thuộc vào base-security-starter mà không khai báo thuộc tính `app.security.session-validation.enabled`
- **THEN** bean DefaultSessionValidationFilter vẫn tự động được khởi tạo vào Spring ApplicationContext và đăng ký ở mức Servlet filter với order -1700

#### Scenario: Vô hiệu hóa tại Identity Provider
- **WHEN** service auth-service cấu hình `app.security.session-validation.enabled: false` trong file cấu hình application-security.yml
- **THEN** bean DefaultSessionValidationFilter KHÔNG được khởi tạo vào Spring ApplicationContext và hoàn toàn không đăng ký vào Servlet filter chain

### Requirement: Public Endpoints Bypass and Direct Processing
Hệ thống auth-service SHALL cho phép các request gửi tới các endpoint công khai (bao gồm /auth/login, /auth/register, /actuator/health) không mang theo Bearer token đi xuyên qua tầng Servlet filter để đến Spring Security và Controller xử lý.

#### Scenario: Gửi request đăng nhập không có token
- **WHEN** client gửi HTTP request POST tới `/auth/login` với body chứa thông tin đăng nhập mà không có header `Authorization`
- **THEN** request không bị từ chối với mã lỗi 401 ở tầng Servlet filter, mà được Spring Security cho phép qua (permitAll) và chuyển giao cho CqrsAuthController.login xử lý

