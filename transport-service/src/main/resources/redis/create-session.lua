local now = redis.call('TIME')
local nowMs = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local expiry = tonumber(ARGV[3])
if expiry <= nowMs then return 0 end
local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', nowMs)
for _, id in ipairs(expired) do
    redis.call('HDEL', KEYS[1], id)
    redis.call('ZREM', KEYS[2], id)
end
if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 1 then return 0 end
redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
redis.call('ZADD', KEYS[2], expiry, ARGV[1])
local latest = redis.call('ZREVRANGE', KEYS[2], 0, 0, 'WITHSCORES')
redis.call('PEXPIREAT', KEYS[1], latest[2])
redis.call('PEXPIREAT', KEYS[2], latest[2])
return 1
