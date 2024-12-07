-- 锁的key=>KEY_PREFIX + name
local key = KEYS[1]

-- 当前线程标识=>currentThreadId
local threadId = ARGV[1]

-- 获取锁中的线程标识=>currentThreadId
local id = redis.call('get', key)

-- 比较线程中的标识与锁中的标识是否一致
if (id == threadId) then
    -- 释放锁 del key
    return redis.call('del', key)
end

return 0