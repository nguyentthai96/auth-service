## Purpose

Cung cấp cấu trúc dữ liệu ngữ cảnh yêu cầu bất biến và cơ chế lưu trữ an toàn luồng, cho phép toàn bộ các tầng ứng dụng và tác vụ nền truy xuất thông tin người dùng, máy khách và dấu vết giao dịch.

## ADDED Requirements

### Requirement: Immutable Request Context Model
Hệ thống SHALL cung cấp cấu trúc dữ liệu `RequestContext` bất biến lưu trữ đầy đủ các thông tin: correlationId, requestId, clientIp, userAgent, deviceFingerprint, appVersion, clientPlatform, locale, timestamp, tenantId và user context.

#### Scenario: Request context initialized successfully
- **WHEN** một yêu cầu HTTP được tiếp nhận
- **THEN** hệ thống khởi tạo một thể hiện `RequestContext` chứa đầy đủ metadata giao vận và đánh dấu sẵn sàng phục vụ các tầng xử lý tiếp theo

### Requirement: Loom-Safe RequestContextHolder
Hệ thống SHALL lưu trữ `RequestContext` bằng `ThreadLocal` chuẩn và bắt buộc giải phóng sạch sẽ trong khối `finally` sau khi hoàn tất xử lý yêu cầu, không sử dụng `InheritableThreadLocal` nhằm tránh rò rỉ bộ nhớ luồng vận chuyển (carrier thread) trên Java 21 Virtual Threads.

#### Scenario: Thread local cleanup after request
- **WHEN** chuỗi xử lý filter chain hoàn tất hoặc ném ra ngoại lệ
- **THEN** hệ thống tự động gọi hàm clear trên context holder để bảo đảm không tồn đọng dữ liệu sang chu kỳ tiếp theo

### Requirement: Non-Web Safe Fallback
Hệ thống SHALL trả về một đối tượng ngữ cảnh dự phòng an toàn (system fallback) khi `RequestContextHolder.require()` được gọi ngoài luồng HTTP Web (ví dụ trong Kafka Listener hoặc Scheduled Task), ngăn ngừa lỗi `ScopeNotActiveException`.

#### Scenario: Service invoked from Kafka consumer
- **WHEN** một phương thức nghiệp vụ hoặc AOP Aspect được kích hoạt từ luồng nhận sự kiện Kafka
- **THEN** hệ thống trả về đối tượng ngữ cảnh fallback với clientPlatform là INTERNAL thay vì dừng ứng dụng
