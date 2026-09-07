-- Mail templates table — stores email templates with placeholder support
CREATE TABLE mail_templates (
    id                  BIGINT PRIMARY KEY,
    code                VARCHAR(100) NOT NULL UNIQUE,
    name                VARCHAR(255) NOT NULL,
    subject_template    VARCHAR(500) NOT NULL,
    body_template       TEXT NOT NULL,
    language            VARCHAR(10) NOT NULL DEFAULT 'vi',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mail_templates_code_active ON mail_templates(code, active);

-- Seed: New Device Login template (Vietnamese)
INSERT INTO mail_templates (id, code, name, subject_template, body_template, language, active, created_at, updated_at)
VALUES (
    1,
    'NEW_DEVICE_LOGIN',
    'Thông báo đăng nhập từ thiết bị mới',
    'Cảnh báo bảo mật: Đăng nhập từ thiết bị mới',
    E'Xin chào {{userName}},\n\nChúng tôi phát hiện tài khoản của bạn vừa được đăng nhập từ một thiết bị mới:\n\n- Thiết bị: {{deviceName}}\n- Loại: {{deviceType}}\n- Trình duyệt: {{browserName}}\n- Hệ điều hành: {{osName}}\n- Địa chỉ IP: {{ipAddress}}\n- Thời gian: {{loginTime}}\n\nNếu đây không phải bạn, vui lòng đổi mật khẩu ngay lập tức và kiểm tra danh sách thiết bị đang hoạt động.\n\nTrân trọng,\nĐội ngũ Bảo mật',
    'vi',
    TRUE,
    NOW(),
    NOW()
);
