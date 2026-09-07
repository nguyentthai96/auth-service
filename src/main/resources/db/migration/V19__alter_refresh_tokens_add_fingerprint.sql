-- Add device_fingerprint and access_token_jti to refresh_tokens for session-token sync
ALTER TABLE refresh_tokens ADD COLUMN device_fingerprint VARCHAR(64);
ALTER TABLE refresh_tokens ADD COLUMN access_token_jti VARCHAR(36);
