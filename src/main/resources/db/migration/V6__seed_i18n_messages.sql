-- ============================================================================
-- V6: Seed i18n_messages with auth-service message keys
-- Part of: api-response-i18n-standard
-- Purpose: Seed DB with auth error messages for runtime override capability
-- Note: File bundles serve as fallback — DB entries enable runtime management
-- ============================================================================

-- English (en) auth messages
INSERT INTO i18n_messages (code, locale, message, module, is_active, created_at, updated_at) VALUES
('auth.invalid_credentials', 'en', 'Invalid username or password', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.account_locked', 'en', 'Account is locked due to failed login attempts', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.token_expired', 'en', 'JWT token has expired', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.permission_denied', 'en', 'Insufficient permissions', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.captcha_required', 'en', 'CAPTCHA verification required', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.captcha_failed', 'en', 'CAPTCHA verification failed', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.mfa_code_invalid', 'en', 'Invalid MFA verification code', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.mfa_rate_limited', 'en', 'MFA rate limit exceeded', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.rate_limited', 'en', 'Too many login attempts', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.session_limit', 'en', 'Maximum active sessions ({0}) reached', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (code, locale) DO NOTHING;

-- Vietnamese (vi) auth messages
INSERT INTO i18n_messages (code, locale, message, module, is_active, created_at, updated_at) VALUES
('auth.invalid_credentials', 'vi', 'Sai tên đăng nhập hoặc mật khẩu', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.account_locked', 'vi', 'Tài khoản bị khóa do đăng nhập sai quá nhiều lần', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.token_expired', 'vi', 'Token đã hết hạn', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.permission_denied', 'vi', 'Không đủ quyền truy cập', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.captcha_required', 'vi', 'Yêu cầu xác minh CAPTCHA', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.captcha_failed', 'vi', 'Xác minh CAPTCHA thất bại', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.mfa_code_invalid', 'vi', 'Mã xác minh MFA không hợp lệ', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.mfa_rate_limited', 'vi', 'Đã vượt quá giới hạn tốc độ xác minh MFA', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.rate_limited', 'vi', 'Quá nhiều lần đăng nhập', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.session_limit', 'vi', 'Đã đạt tối đa {0} phiên hoạt động', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (code, locale) DO NOTHING;
