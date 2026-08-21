-- V15: Domain branding fields (FR-016)
-- Adds branding configuration fields to domains table.
-- These may already exist if the entity was created with Hibernate DDL=update.
-- Using IF NOT EXISTS / exception handling to be migration-safe.

DO $$
BEGIN
    -- Add logo_url column if not exists
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='domains' AND column_name='logo_url') THEN
        ALTER TABLE domains ADD COLUMN logo_url VARCHAR(1000);
    END IF;

    -- Add primary_color column if not exists
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='domains' AND column_name='primary_color') THEN
        ALTER TABLE domains ADD COLUMN primary_color VARCHAR(10);
    END IF;

    -- Add login_page_config column if not exists
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='domains' AND column_name='login_page_config') THEN
        ALTER TABLE domains ADD COLUMN login_page_config JSONB;
    END IF;

    -- Add favicon_url column if not exists
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='domains' AND column_name='favicon_url') THEN
        ALTER TABLE domains ADD COLUMN favicon_url VARCHAR(1000);
    END IF;
END $$;
