-- ============================================================================
-- V12: Seed i18n messages for event sourcing error codes
-- Part of: user-registration-event (Event Sourcing)
-- Purpose: Add i18n messages for AUTH_050-053 error codes
-- Pattern: Follows V6__seed_i18n_messages.sql format
-- ============================================================================

-- English (en) event sourcing error messages
INSERT INTO i18n_messages (code, locale, message, module, is_active, created_at, updated_at) VALUES
('auth.event_store_persist_failed', 'en', 'Event store write failure', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.outbox_publish_failed', 'en', 'Event publish failed', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.event_not_found', 'en', 'Event not found', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.outbox_max_retries', 'en', 'Event publish max retries exceeded', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (code, locale) DO NOTHING;

-- Vietnamese (vi) event sourcing error messages
INSERT INTO i18n_messages (code, locale, message, module, is_active, created_at, updated_at) VALUES
('auth.event_store_persist_failed', 'vi', 'Lỗi ghi vào event store', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.outbox_publish_failed', 'vi', 'Gửi sự kiện thất bại', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.event_not_found', 'vi', 'Không tìm thấy sự kiện', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000),
('auth.outbox_max_retries', 'vi', 'Vượt quá số lần thử gửi sự kiện', 'auth', TRUE, EXTRACT(EPOCH FROM NOW()) * 1000, EXTRACT(EPOCH FROM NOW()) * 1000)
ON CONFLICT (code, locale) DO NOTHING;
