-- Sliding window rate limiter (weighted counter)
-- KEYS[1] = anon:rate:{ip}:{currentWindow}
-- KEYS[2] = anon:rate:{ip}:{prevWindow}
-- ARGV[1] = maxAttempts
-- ARGV[2] = windowSeconds
-- ARGV[3] = elapsedSeconds
-- Returns: -1 if denied, otherwise the weighted count (floor)

local maxAttempts = tonumber(ARGV[1])
local windowSeconds = tonumber(ARGV[2])
local elapsedSeconds = tonumber(ARGV[3])

-- Increment current window counter
local current = redis.call('INCR', KEYS[1])

-- Set TTL on first increment (2x window for overlap)
if current == 1 then
    redis.call('EXPIRE', KEYS[1], windowSeconds * 2)
end

-- Get previous window counter (0 if not exists)
local prev = tonumber(redis.call('GET', KEYS[2]) or '0')

-- Calculate weight for previous window (linear decay)
local weight = math.max(0, (windowSeconds - elapsedSeconds) / windowSeconds)

-- Weighted count = previous * weight + current
local count = prev * weight + current

-- Check against max attempts
if count > maxAttempts then
    return -1
end

return math.floor(count)
