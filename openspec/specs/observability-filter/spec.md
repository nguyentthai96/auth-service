# observability-filter Specification

## Purpose
Tự động trích xuất các thông tin định danh giao vận từ HTTP Headers, bóc tách IP qua chuỗi proxy, quản trị Mapped Diagnostic Context (MDC) và đính kèm Correlation ID vào phản hồi HTTP.
## Requirements
### Requirement: Correlation ID Extraction and Priority
Hệ thống SHALL trích xuất mã tương quan theo thứ tự ưu tiên: header `X-Correlation-ID` → header `X-Request-ID` → tự động sinh một chuỗi UUID ngẫu nhiên phiên bản mới nếu cả hai header trên đều vắng mặt.

#### Scenario: Request contains X-Correlation-ID header
- **WHEN** client gửi request có header `X-Correlation-ID` với giá trị cụ thể
- **THEN** hệ thống sử dụng chính xác giá trị này làm `correlationId` xuyên suốt quá trình xử lý

#### Scenario: Request without correlation headers
- **WHEN** client gửi request không kèm theo bất kỳ header tương quan nào
- **THEN** hệ thống tự động sinh một UUID ngẫu nhiên làm `correlationId` và tạo thêm một UUID riêng cho `requestId`

### Requirement: Multi-Proxy Client IP Extraction
Hệ thống SHALL bóc tách địa chỉ IP thực tế của máy khách bằng cách đọc giá trị IP đầu tiên trong danh sách phân tách bởi dấu phẩy của header `X-Forwarded-For`. Nếu header này không tồn tại, hệ thống sử dụng `remoteAddr` của kết nối mạng và chuẩn hóa địa chỉ loopback IPv6 sang định dạng IPv4 `127.0.0.1`.

#### Scenario: Client connects through multiple reverse proxies
- **WHEN** request có header `X-Forwarded-For: 203.0.113.195, 70.41.3.18, 150.172.238.178`
- **THEN** hệ thống trích xuất chính xác `203.0.113.195` làm `clientIp`

### Requirement: Structured MDC Logging Management
Hệ thống SHALL tự động đưa các khóa `correlationId`, `clientIp`, `appVersion`, `clientPlatform` vào SLF4J MDC ngay khi tiếp nhận yêu cầu và bắt buộc dọn sạch toàn bộ MDC trong khối `finally` khi kết thúc yêu cầu.

#### Scenario: Logging within controller execution
- **WHEN** ứng dụng ghi log trong quá trình xử lý request
- **THEN** mọi dòng log xuất ra định dạng JSON đều tự động chứa các thuộc tính `correlationId` và `clientIp` tương ứng

### Requirement: Correlation ID Response Echo
Hệ thống SHALL tự động thêm header `X-Correlation-ID` vào phản hồi HTTP gửi về cho client với giá trị trùng khớp với mã tương quan của request.

#### Scenario: HTTP response returned to client
- **WHEN** hệ thống hoàn tất xử lý request thành công hoặc trả về lỗi
- **THEN** phản hồi HTTP luôn chứa header `X-Correlation-ID`

