-- Add access_token_jti and device_name to login_sessions for session-token sync
ALTER TABLE login_sessions ADD COLUMN access_token_jti VARCHAR(36);
ALTER TABLE login_sessions ADD COLUMN device_name VARCHAR(100);

CREATE INDEX idx_login_sessions_jti ON login_sessions(access_token_jti);
