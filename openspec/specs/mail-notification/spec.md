## Purpose

Hệ thống email notification async theo Transactional Outbox pattern — enqueue mail vào database, scheduled job poll và gửi SMTP với retry mechanism.

## ADDED Requirements

### Requirement: Mail queue persistence
Hệ thống SHALL lưu trữ email requests trong bảng `mail_queue` với status lifecycle: PENDING → PROCESSING → SENT | FAILED.

#### Scenario: Enqueue mail
- **WHEN** component gọi enqueue với recipient, template_code, template_data
- **THEN** hệ thống MUST tạo record status PENDING trong mail_queue

#### Scenario: Mail record structure
- **WHEN** mail record được tạo
- **THEN** record MUST chứa: recipient, subject, template_code, template_data (JSONB), status, retry_count, max_retries, created_at

### Requirement: Template-based rendering
Hệ thống SHALL render email content từ template stored trong bảng `mail_templates` với placeholder substitution.

#### Scenario: Template exists and active
- **WHEN** mail_queue record có template_code matching active template
- **THEN** hệ thống MUST render subject + body bằng template substitution `{{key}}` → value

#### Scenario: Template not found
- **WHEN** mail_queue record có template_code KHÔNG matching bất kỳ active template
- **THEN** hệ thống MUST mark mail status FAILED VÀ log error

### Requirement: Scheduled mail processing
MailJobScheduler SHALL poll mail_queue theo interval cấu hình, process batch, gửi SMTP.

#### Scenario: Process pending mails
- **WHEN** scheduler triggers VÀ có mails status PENDING với next_retry_at <= now
- **THEN** hệ thống MUST lock records (SELECT FOR UPDATE SKIP LOCKED), render template, gửi SMTP, update status SENT

#### Scenario: No pending mails
- **WHEN** scheduler triggers VÀ KHÔNG có mails cần xử lý
- **THEN** scheduler MUST return immediately (no-op)

### Requirement: Retry with exponential backoff
Hệ thống SHALL retry mail gửi thất bại với exponential backoff strategy.

#### Scenario: First failure
- **WHEN** SMTP send fails VÀ retry_count < max_retries
- **THEN** hệ thống MUST increment retry_count, set next_retry_at = now + baseDelay × 2^retryCount, status = PENDING

#### Scenario: Max retries exhausted
- **WHEN** SMTP send fails VÀ retry_count >= max_retries
- **THEN** hệ thống MUST set status = FAILED VÀ record error_message

### Requirement: New device login email
Hệ thống SHALL gửi email thông báo khi user đăng nhập từ device mới.

#### Scenario: New device detected
- **WHEN** login thành công VÀ isNewDevice = true VÀ login transaction committed
- **THEN** hệ thống MUST enqueue email template `NEW_DEVICE_LOGIN` với device info cho user

#### Scenario: Known device login
- **WHEN** login thành công VÀ isNewDevice = false
- **THEN** hệ thống MUST NOT enqueue bất kỳ email nào

### Requirement: Mail queue cleanup
Hệ thống SHALL dọn dẹp mail_queue records đã gửi thành công older than configurable period.

#### Scenario: Cleanup old records
- **WHEN** cleanup job triggers VÀ có records status SENT older than cleanup_after_days
- **THEN** hệ thống MUST delete các records đó permanently
