# outbound-propagation Specification

## Purpose
Lan truyền ngữ cảnh định danh và mã tương quan giao dịch sang các luồng thực thi bất đồng bộ, các cuộc gọi HTTP dịch vụ ngoài và sự kiện Kafka.
## Requirements
### Requirement: Async and Virtual Thread Context Propagation
Hệ thống SHALL cung cấp cơ chế đóng gói tác vụ (TaskDecorator) tự động sao chép toàn bộ `RequestContext` và `MDC` hiện tại sang luồng thực thi mới khi khởi chạy tác vụ bất đồng bộ hoặc Virtual Thread, đồng thời dọn dẹp sạch sẽ khi tác vụ hoàn tất.

#### Scenario: Background task spawned via Async
- **WHEN** một phương thức được đánh dấu `@Async` hoặc được ủy thác cho ThreadPoolTaskExecutor
- **THEN** luồng thực thi mới nhận được bản sao chính xác của `RequestContext` và `MDC` từ luồng cha

### Requirement: HTTP Client Correlation Header Propagation
Hệ thống SHALL cung cấp bộ can thiệp (ClientHttpRequestInterceptor) cho các HTTP Client (`RestClient` / `RestTemplate`) tự động đính kèm header `X-Correlation-ID` lấy từ ngữ cảnh hiện tại vào mọi yêu cầu gọi sang microservice khác.

#### Scenario: Inter-service REST call
- **WHEN** một microservice gọi API sang một microservice khác trong hệ thống
- **THEN** request gửi đi tự động chứa header `X-Correlation-ID` mang giá trị trùng khớp với giao dịch đang xử lý

### Requirement: Configurable Token Relay Option
Hệ thống SHALL hỗ trợ cờ cấu hình `app.http-client.forward-auth-token` (mặc định tắt: `false`). Khi cờ này được bật, hệ thống tự động chuyển tiếp header `Authorization: Bearer <token>` sang cuộc gọi downstream.

#### Scenario: Token relay enabled by configuration
- **WHEN** cấu hình `app.http-client.forward-auth-token` mang giá trị true và request hiện tại có header Authorization
- **THEN** HTTP client tự động chuyển tiếp header Authorization sang request downstream

