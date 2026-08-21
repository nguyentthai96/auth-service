-- Atomic data store with size limit enforcement (TOCTOU-safe)
-- KEYS[1] = session hash key (anon:session:{id})
-- KEYS[2] = data key to write (anon:data:{id}:{ns}:{key})
-- ARGV[1] = maxDataSizeBytes
-- ARGV[2] = value to store
-- ARGV[3] = ttlSeconds
-- Returns: -1 if limit exceeded, otherwise new total data size

local maxSize = tonumber(ARGV[1])
local newSize = #ARGV[2]

-- Get current running data size counter (0 if not set)
local currentSize = tonumber(redis.call('HGET', KEYS[1], 'dataSize') or '0')

-- Atomic size check — reject if over limit
if currentSize + newSize > maxSize then
    return -1
end

-- Store data with TTL
redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[3]))

-- Increment running data size counter
redis.call('HINCRBY', KEYS[1], 'dataSize', newSize)

return currentSize + newSize
