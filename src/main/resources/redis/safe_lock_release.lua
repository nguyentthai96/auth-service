-- Safe distributed lock release with ownership check
-- KEYS[1] = lock key
-- ARGV[1] = ownerUUID
-- Returns: 1 if released (owner matched), 0 if not owner (skipped)

if redis.call('GET', KEYS[1]) == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 1
else
    return 0
end
