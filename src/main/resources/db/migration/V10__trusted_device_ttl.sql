-- V10: Add trusted device timestamp for TTL enforcement (FR-005)
-- Tracks when a device was marked as trusted, enabling configurable expiry (default 30 days).
-- Existing users with trusted_device_hash but no set_at → treated as expired → MFA required → fresh timestamp set on next verify.

ALTER TABLE users ADD COLUMN trusted_device_set_at TIMESTAMPTZ;
