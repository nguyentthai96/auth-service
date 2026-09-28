-- V10: Add UUIDv7 public identifier to users table
-- Change: user-identity-dual-key | FR-001

-- Step 1: Add nullable uuid column
ALTER TABLE users ADD COLUMN uuid UUID;

-- Step 2: Backfill existing rows with random UUIDs
UPDATE users SET uuid = gen_random_uuid() WHERE uuid IS NULL;

-- Step 3: Set NOT NULL constraint
ALTER TABLE users ALTER COLUMN uuid SET NOT NULL;

-- Step 4: Create unique index on uuid
CREATE UNIQUE INDEX idx_users_uuid ON users(uuid);
