local key = KEYS[1]
local intervalPerTokens = tonumber(ARGV[1])
local curTime = tonumber(ARGV[2])
local initTokens = tonumber(ARGV[3])
local bucketMaxTokens = tonumber(ARGV[4])
local resetBucketInterval = tonumber(ARGV[5])
local bucket = redis.call('hgetall', key)
local currentTokens
if table.maxn(bucket) == 0 then
currentTokens = initTokens
redis.call('hset', key, 'lastRefillTime', curTime)
redis.call('expire', key, resetBucketInterval * 1.5)
elseif table.maxn(bucket) == 4 then
local lastRefillTime = tonumber(bucket[2])
local tokensRemaining = tonumber(bucket[4])
if curTime > lastRefillTime then
local intervalSinceLast = curTime - lastRefillTime
if intervalSinceLast > resetBucketInterval then
currentTokens = initTokens
redis.call('hset', key, 'lastRefillTime', curTime)
else
local grantedTokens = math.floor(intervalSinceLast / intervalPerTokens)
if grantedTokens > 0 then
local padMillis = math.fmod(intervalSinceLast, intervalPerTokens)
redis.call('hset', key, 'lastRefillTime', curTime - padMillis)
end
currentTokens = math.min(grantedTokens + tokensRemaining, bucketMaxTokens)
end
else
currentTokens = tokensRemaining
end
end
assert(currentTokens >= 0)
if currentTokens == 0  then
redis.call('hset', key, 'tokensRemaining', currentTokens)
return 0
else
redis.call('hset', key, 'tokensRemaining', currentTokens - 1)
return currentTokens
end
