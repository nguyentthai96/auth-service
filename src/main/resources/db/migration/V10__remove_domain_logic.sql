-- V10: Remove domain logic — simplify to flat/global RBAC
-- Decision: Cut-over strategy (system not in production)

-- Phase 1: Drop domain-scoped tables
DROP TABLE IF EXISTS user_domains CASCADE;
DROP TABLE IF EXISTS domains CASCADE;

-- Phase 2: Rename domain-scoped tables to global
ALTER TABLE domain_resources RENAME TO resources;
ALTER TABLE resources DROP COLUMN IF EXISTS domain_id;
-- Re-create unique constraint without domain_id
ALTER TABLE resources DROP CONSTRAINT IF EXISTS domain_resources_domain_id_code_key;
ALTER TABLE resources ADD CONSTRAINT uk_resources_code UNIQUE (code);

ALTER TABLE domain_roles RENAME TO roles;
ALTER TABLE roles DROP COLUMN IF EXISTS domain_id;
-- Re-create unique constraint without domain_id
ALTER TABLE roles DROP CONSTRAINT IF EXISTS domain_roles_domain_id_code_key;
ALTER TABLE roles ADD CONSTRAINT uk_roles_code UNIQUE (code);

-- Phase 3: Remove domain_id from other tables
ALTER TABLE groups DROP COLUMN IF EXISTS domain_id;
-- Re-create unique constraint without domain_id
ALTER TABLE groups DROP CONSTRAINT IF EXISTS groups_domain_id_name_key;
ALTER TABLE groups ADD CONSTRAINT uk_groups_name UNIQUE (name);

ALTER TABLE password_policies DROP COLUMN IF EXISTS domain_id;
-- Remove old unique constraint on domain_id, ensure single global policy
ALTER TABLE password_policies DROP CONSTRAINT IF EXISTS password_policies_domain_id_key;

-- Phase 4: Remove domain_id from PBAC policies table
ALTER TABLE policies DROP COLUMN IF EXISTS domain_id;
