local now = redis.call('TIME')
local nowMs = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
local expiry = redis.call('ZSCORE', KEYS[2], ARGV[1])
if not expiry or tonumber(expiry) <= nowMs or tonumber(expiry) ~= tonumber(ARGV[2]) then
    return nil
end
return redis.call('HGET', KEYS[1], ARGV[1])
