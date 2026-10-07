-- =====================================================
-- V28: Deprecate local i18n_messages table
-- Part of: microservice-database-architecture change
--
-- Context: i18n messages are now managed centrally by
-- system-admin-service. Auth-service reads them via
-- sysadmin-client (II18nReader interface).
--
-- Strategy: Rename table to preserve data as backup.
-- After verification period, drop with next migration.
-- =====================================================

-- Step 1: Rename the local table (preserves data for rollback)
ALTER TABLE IF EXISTS i18n_messages RENAME TO _deprecated_i18n_messages;

-- Step 2: Add comment explaining deprecation
COMMENT ON TABLE _deprecated_i18n_messages IS
    'DEPRECATED: i18n messages moved to system-admin-service DB. '
    'Data preserved for rollback. Safe to DROP after verification. '
    'Migration: microservice-database-architecture (2026-10).';
