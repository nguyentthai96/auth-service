-- ============================================================================
-- V20: Add {bcrypt} prefix to existing password hashes
-- ============================================================================
-- Purpose: DelegatingPasswordEncoder uses prefix-based routing to determine
--          which algorithm to use for password verification. Existing BCrypt
--          hashes need {bcrypt} prefix for correct routing.
--
-- Scope:   users.password_hash + password_history.password_hash
-- Safety:  WHERE clause ensures idempotency (skips already-prefixed hashes)
-- Rollback: UPDATE users SET password_hash = SUBSTRING(password_hash, 9)
--           WHERE password_hash LIKE '{bcrypt}%';
-- ============================================================================

-- Add {bcrypt} prefix to users table
UPDATE users
SET password_hash = CONCAT('{bcrypt}', password_hash)
WHERE password_hash NOT LIKE '{%}%'
  AND password_hash LIKE '$2a$%';

-- Add {bcrypt} prefix to password_history table
UPDATE password_history
SET password_hash = CONCAT('{bcrypt}', password_hash)
WHERE password_hash NOT LIKE '{%}%'
  AND password_hash LIKE '$2a$%';
